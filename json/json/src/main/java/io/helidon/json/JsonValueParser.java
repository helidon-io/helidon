/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class JsonValueParser implements JsonParser {

    private static final int INITIAL_FRAME_CAPACITY = 4;
    private static final BigDecimal LONG_MIN_MINUS_ONE = new BigDecimal("-9223372036854775809");
    private static final BigDecimal LONG_MAX_PLUS_ONE = new BigDecimal("9223372036854775808");

    private JsonValue current;
    private Frame[] frames;
    private int depth;
    private JsonValue markedCurrent;
    private Frame[] markedFrames;
    private int markedDepth;
    private boolean marked;

    JsonValueParser(JsonValue jsonValue) {
        this.current = jsonValue;
    }

    @Override
    public boolean hasNext() {
        JsonValueType type = current.type();
        return type == JsonValueType.OBJECT || type == JsonValueType.ARRAY || depth > 0;
    }

    @Override
    public byte nextToken() {
        JsonValueType currentType = current.type();
        if (currentType == JsonValueType.OBJECT) {
            JsonObject object = current.asObject();
            if (object.size() == 0) {
                current = JsonControlValue.OBJECT_END;
                return current.jsonStartChar();
            }
            pushFrame(object);
        } else if (currentType == JsonValueType.ARRAY) {
            JsonArray array = current.asArray();
            if (array.size() == 0) {
                current = JsonControlValue.ARRAY_END;
                return current.jsonStartChar();
            }
            pushFrame(array);
        }

        if (depth == 0) {
            throw new JsonException("No more JSON Values available");
        }
        Frame frame = frames[depth - 1];
        current = frame.next();
        if (frame.complete()) {
            depth--;
            frame.clear();
        }
        return current.jsonStartChar();
    }

    private void pushFrame(JsonValue container) {
        if (frames == null) {
            frames = new Frame[INITIAL_FRAME_CAPACITY];
        } else if (depth == frames.length) {
            frames = Arrays.copyOf(frames, frames.length * 2);
        }
        Frame frame = frames[depth];
        if (frame == null) {
            frame = new Frame();
            frames[depth] = frame;
        }
        frame.initialize(container);
        depth++;
    }

    @Override
    public byte currentByte() {
        return current.jsonStartChar();
    }

    @Override
    public JsonValue readJsonValue() {
        return current;
    }

    @Override
    public JsonObject readJsonObject() {
        return current.asObject();
    }

    @Override
    public JsonArray readJsonArray() {
        return current.asArray();
    }

    @Override
    public JsonString readJsonString() {
        return current.asString();
    }

    @Override
    public JsonNumber readJsonNumber() {
        return current.asNumber();
    }

    @Override
    public String readString() {
        return current.asString().value();
    }

    @Override
    public int readStringAsHash() {
        return JsonParserArray.fnv1aHashUtf8(current.asString().value());
    }

    @Override
    public char readChar() {
        return current.asString().value().charAt(0);
    }

    @Override
    public boolean readBoolean() {
        return current.asBoolean().value();
    }

    @Override
    public byte readByte() {
        try {
            return (byte) readIntegral(Byte.MIN_VALUE, Byte.MAX_VALUE, "byte");
        } catch (ArithmeticException e) {
            throw createException("The number is too big for a byte value", e);
        }
    }

    @Override
    public short readShort() {
        try {
            return (short) readIntegral(Short.MIN_VALUE, Short.MAX_VALUE, "short");
        } catch (ArithmeticException e) {
            throw createException("The number is too big for a short value", e);
        }
    }

    @Override
    public int readInt() {
        try {
            return (int) readIntegral(Integer.MIN_VALUE, Integer.MAX_VALUE, "int");
        } catch (ArithmeticException e) {
            throw createException("The number is too big for an int value", e);
        }
    }

    @Override
    public long readLong() {
        try {
            return readIntegral(Long.MIN_VALUE, Long.MAX_VALUE, "long");
        } catch (ArithmeticException e) {
            throw createException("The number is too big for a long value", e);
        }
    }

    @Override
    public float readFloat() {
        return (float) readDouble();
    }

    @Override
    public double readDouble() {
        if (current.type() == JsonValueType.STRING) {
            String value = current.asString().value();
            return switch (value) {
                case "NaN" -> Double.NaN;
                case "Infinity", "infinity" -> Double.POSITIVE_INFINITY;
                case "-Infinity", "-infinity" -> Double.NEGATIVE_INFINITY;
                default -> throw createException("Invalid double number");
            };
        }
        return current.asNumber().doubleValue();
    }

    @Override
    public BigInteger readBigInteger() {
        return current.asNumber().bigDecimalValue().toBigInteger();
    }

    @Override
    public BigDecimal readBigDecimal() {
        return current.asNumber().bigDecimalValue();
    }

    @Override
    public byte[] readBinary() {
        String value = current.asString().value();
        return Base64.getDecoder().decode(value);
    }

    @Override
    public boolean checkNull() {
        return current.type() == JsonValueType.NULL;
    }

    @Override
    public void skip() {
        JsonValueType type = current.type();
        if (type == JsonValueType.OBJECT) {
            current = JsonControlValue.OBJECT_END;
        } else if (type == JsonValueType.ARRAY) {
            current = JsonControlValue.ARRAY_END;
        } else if (depth == 0) {
            current = JsonNoopValue.INSTANCE;
        } else {
            nextToken();
        }
    }

    @Override
    public JsonException createException(String message) {
        return new JsonException(message);
    }

    @Override
    public JsonException createException(String message, Exception e) {
        return new JsonException(message, e);
    }

    @Override
    public void mark() {
        if (marked) {
            throw new IllegalStateException("Parser has already been marked for replaying. "
                                                    + "Cannot do it twice without consuming the mark with either "
                                                    + "clearMark or resetToMark methods.");
        }
        if (depth > 0) {
            ensureMarkedFrameCapacity();
            for (int i = 0; i < depth; i++) {
                Frame frame = markedFrames[i];
                if (frame == null) {
                    frame = new Frame();
                    markedFrames[i] = frame;
                }
                frame.copyStateFrom(frames[i]);
            }
        }
        markedCurrent = current;
        markedDepth = depth;
        marked = true;
    }

    @Override
    public void clearMark() {
        clearMarkedState();
    }

    @Override
    public void resetToMark() {
        if (!marked) {
            throw new IllegalStateException("Parser tried to reset to the marked place, but no mark was found");
        }

        for (int i = 0; i < depth; i++) {
            frames[i].clear();
        }
        depth = 0;

        if (markedDepth > 0) {
            if (frames == null) {
                frames = new Frame[Math.max(INITIAL_FRAME_CAPACITY, markedDepth)];
            } else if (frames.length < markedDepth) {
                frames = Arrays.copyOf(frames, Math.max(frames.length * 2, markedDepth));
            }
            for (int i = 0; i < markedDepth; i++) {
                Frame frame = frames[i];
                if (frame == null) {
                    frame = new Frame();
                    frames[i] = frame;
                }
                frame.restore(markedFrames[i]);
            }
            depth = markedDepth;
        }
        current = markedCurrent;
        clearMarkedState();
    }

    private void ensureMarkedFrameCapacity() {
        if (markedFrames == null) {
            markedFrames = new Frame[Math.max(INITIAL_FRAME_CAPACITY, depth)];
        } else if (markedFrames.length < depth) {
            markedFrames = Arrays.copyOf(markedFrames, Math.max(markedFrames.length * 2, depth));
        }
    }

    private void clearMarkedState() {
        for (int i = 0; i < depth; i++) {
            frames[i].clearMark();
        }
        for (int i = 0; i < markedDepth; i++) {
            markedFrames[i].clear();
        }
        markedCurrent = null;
        markedDepth = 0;
        marked = false;
    }

    private long readIntegral(long minValue, long maxValue, String type) {
        BigDecimal value = readBigDecimal();
        if (value.compareTo(LONG_MIN_MINUS_ONE) <= 0 || value.compareTo(LONG_MAX_PLUS_ONE) >= 0) {
            throw new ArithmeticException("BigInteger out of " + type + " range");
        }
        long result = value.longValue();
        if (result < minValue || result > maxValue) {
            throw new ArithmeticException("BigInteger out of " + type + " range");
        }
        return result;
    }

    private static final class Frame {
        private static final int COMPLETE = -1;

        private static final int ARRAY_VALUE_OR_END = 0;
        private static final int ARRAY_DELIMITER_OR_END = 1;

        private static final int OBJECT_KEY_OR_END = 0;
        private static final int OBJECT_COLON = 1;
        private static final int OBJECT_VALUE = 2;
        private static final int OBJECT_DELIMITER_OR_END = 3;

        private JsonValue container;
        private List<JsonValue> arrayValues;
        private Iterator<Map.Entry<String, JsonValue>> objectIterator;
        private Map.Entry<String, JsonValue> objectEntry;
        private Map.Entry<String, JsonValue> replayEntry;
        private List<Map.Entry<String, JsonValue>> replayEntries;
        private int replayPosition;
        private Frame markedFrame;
        private int position;
        private int phase = COMPLETE;

        private void initialize(JsonValue container) {
            this.container = container;
            this.position = 0;
            if (container.type() == JsonValueType.ARRAY) {
                this.arrayValues = container.asArray().values();
                this.phase = ARRAY_VALUE_OR_END;
            } else {
                this.objectIterator = container.asObject().entryIterator();
                this.phase = OBJECT_KEY_OR_END;
            }
        }

        private JsonValue next() {
            return switch (container.type()) {
            case ARRAY -> nextArray();
            case OBJECT -> nextObject();
            default -> throw new JsonException("Invalid JsonValue container type: " + container.type());
            };
        }

        private JsonValue nextArray() {
            if (phase == ARRAY_VALUE_OR_END) {
                if (position == arrayValues.size()) {
                    phase = COMPLETE;
                    return JsonControlValue.ARRAY_END;
                }
                phase = ARRAY_DELIMITER_OR_END;
                return arrayValues.get(position++);
            }
            if (position < arrayValues.size()) {
                phase = ARRAY_VALUE_OR_END;
                return JsonControlValue.COMMA;
            }
            phase = COMPLETE;
            return JsonControlValue.ARRAY_END;
        }

        private JsonValue nextObject() {
            return switch (phase) {
            case OBJECT_KEY_OR_END -> {
                if (!hasNextObjectEntry()) {
                    phase = COMPLETE;
                    yield JsonControlValue.OBJECT_END;
                }
                objectEntry = nextObjectEntry();
                position++;
                phase = OBJECT_COLON;
                yield JsonString.create(objectEntry.getKey());
            }
            case OBJECT_COLON -> {
                phase = OBJECT_VALUE;
                yield JsonControlValue.COLON;
            }
            case OBJECT_VALUE -> {
                phase = OBJECT_DELIMITER_OR_END;
                yield objectEntry.getValue();
            }
            case OBJECT_DELIMITER_OR_END -> {
                objectEntry = null;
                if (hasNextObjectEntry()) {
                    phase = OBJECT_KEY_OR_END;
                    yield JsonControlValue.COMMA;
                }
                phase = COMPLETE;
                yield JsonControlValue.OBJECT_END;
            }
            default -> throw new JsonException("Invalid JsonValue object traversal state");
            };
        }

        private boolean complete() {
            return phase == COMPLETE;
        }

        private void copyStateFrom(Frame source) {
            clear();
            container = source.container;
            arrayValues = source.arrayValues;
            objectIterator = source.objectIterator;
            objectEntry = source.objectEntry;
            replayEntry = source.replayEntry;
            replayEntries = source.replayEntries;
            replayPosition = source.replayPosition;
            position = source.position;
            phase = source.phase;
            if (container.type() == JsonValueType.OBJECT) {
                source.markedFrame = this;
            }
        }

        private void restore(Frame snapshot) {
            clear();
            container = snapshot.container;
            arrayValues = snapshot.arrayValues;
            objectIterator = snapshot.objectIterator;
            objectEntry = snapshot.objectEntry;
            replayEntry = snapshot.replayEntry;
            replayEntries = snapshot.replayEntries;
            replayPosition = snapshot.replayPosition;
            position = snapshot.position;
            phase = snapshot.phase;
        }

        private boolean hasNextObjectEntry() {
            return replayEntries != null && replayPosition < replayEntries.size()
                    || replayEntry != null && replayPosition == 0
                    || objectIterator.hasNext();
        }

        private Map.Entry<String, JsonValue> nextObjectEntry() {
            if (replayEntries != null && replayPosition < replayEntries.size()) {
                return replayEntries.get(replayPosition++);
            }
            if (replayEntry != null && replayPosition == 0) {
                replayPosition = 1;
                return replayEntry;
            }

            Map.Entry<String, JsonValue> entry = objectIterator.next();
            if (markedFrame != null) {
                boolean sharedReplay = replayEntries != null && replayEntries == markedFrame.replayEntries;
                markedFrame.recordObjectEntry(entry);
                if (sharedReplay) {
                    replayPosition++;
                }
            }
            return entry;
        }

        private void recordObjectEntry(Map.Entry<String, JsonValue> entry) {
            if (replayEntries != null) {
                replayEntries.add(entry);
            } else if (replayEntry == null) {
                replayEntry = entry;
            } else {
                replayEntries = new ArrayList<>();
                replayEntries.add(replayEntry);
                replayEntries.add(entry);
                replayEntry = null;
            }
        }

        private void clearMark() {
            markedFrame = null;
        }

        private void clear() {
            container = null;
            arrayValues = null;
            objectIterator = null;
            objectEntry = null;
            replayEntry = null;
            replayEntries = null;
            replayPosition = 0;
            markedFrame = null;
            position = 0;
            phase = COMPLETE;
        }
    }

}
