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
import java.util.Objects;

import io.helidon.common.Api;

/**
 * An implementation of the {@link io.helidon.json.JsonParser} which enforces object start as the current value.
 * Delegates all other calls to the provided JsonParser.
 */
@Api.Preview
public final class ObjectStartParser extends JsonParserBase {

    private static final JsonParser OBJECT_START_PARSER = new ForcedObjectStartParser();

    private final JsonParser realParser;
    private JsonParser parser = OBJECT_START_PARSER;
    private boolean switched = false;
    private boolean marked = false;

    private ObjectStartParser(JsonParser realParser) {
        this.realParser = realParser;
    }

    /**
     * Create a new JSON parser that pretends to be at the beginning of an object.
     * <p>
     * This method wraps an existing parser to ensure that parsing begins
     * at the start of a JSON object ('{'). It is expected the provided parser is at the key start or object end.
     * </p>
     *
     * @param parser the base parser to wrap
     * @return a new ObjectStartParser instance that starts at object beginning
     */
    public static ObjectStartParser create(JsonParser parser) {
        return new ObjectStartParser(parser);
    }

    /**
     * Creates a parser that omits one property from an object.
     * The underlying parser must already be positioned at the object start.
     * On successful creation, the parser position and any active mark remain unchanged.
     * Values are read from the underlying parser without materializing or copying the object.
     *
     * @param parser parser positioned at the object start
     * @param propertyIndex zero-based index of the property to omit
     * @return parser at the object start, hiding the selected property
     * @throws IllegalArgumentException if the property index is negative
     * @throws JsonException if the parser is not positioned at an object start
     */
    public static JsonParser create(JsonParser parser, int propertyIndex) {
        Objects.requireNonNull(parser);
        if (propertyIndex < 0) {
            throw new IllegalArgumentException("Property index must not be negative");
        }
        if (parser.currentByte() != '{') {
            throw parser.createException("Expected an object start", parser.currentByte());
        }
        return new ObjectPropertyParser(parser, propertyIndex);
    }

    @Override
    public boolean hasNext() {
        return parser.hasNext();
    }

    @Override
    public byte nextToken() {
        if (!switched) {
            switched = true;
            parser = realParser;
            return parser.currentByte();
        }
        return parser.nextToken();
    }

    @Override
    public byte currentByte() {
        return parser.currentByte();
    }

    @Override
    public JsonValue readJsonValue() {
        return switch (currentByte()) {
        case '{' -> readJsonObject();
        case '[' -> readJsonArray();
        default -> parser.readJsonValue();
        };
    }

    @Override
    public JsonObject readJsonObject() {
        if (!switched) {
            return super.readJsonObject();
        }
        JsonObject value = parser.readJsonObject();
        if (parser.currentByte() == '{') {
            parser.skip();
        }
        return value;
    }

    @Override
    public JsonArray readJsonArray() {
        JsonArray value = parser.readJsonArray();
        if (parser.currentByte() == '[') {
            parser.skip();
        }
        return value;
    }

    @Override
    public JsonString readJsonString() {
        return parser.readJsonString();
    }

    @Override
    public JsonNumber readJsonNumber() {
        return parser.readJsonNumber();
    }

    @Override
    public String readString() {
        return parser.readString();
    }

    @Override
    public int readString(JsonStringLookup lookup) {
        return parser.readString(lookup);
    }

    @Override
    public int readStringAsHash() {
        return parser.readStringAsHash();
    }

    @Override
    public char readChar() {
        return parser.readChar();
    }

    @Override
    public boolean readBoolean() {
        return parser.readBoolean();
    }

    @Override
    public byte readByte() {
        return parser.readByte();
    }

    @Override
    public short readShort() {
        return parser.readShort();
    }

    @Override
    public int readInt() {
        return parser.readInt();
    }

    @Override
    public long readLong() {
        return parser.readLong();
    }

    @Override
    public float readFloat() {
        return parser.readFloat();
    }

    @Override
    public double readDouble() {
        return parser.readDouble();
    }

    @Override
    public BigInteger readBigInteger() {
        return parser.readBigInteger();
    }

    @Override
    public BigDecimal readBigDecimal() {
        return parser.readBigDecimal();
    }

    @Override
    public byte[] readBinary() {
        return parser.readBinary();
    }

    @Override
    public boolean checkNull() {
        return parser.checkNull();
    }

    @Override
    public void skip() {
        parser.skip();
    }

    @Override
    public JsonException createException(String message) {
        return parser.createException(message);
    }

    @Override
    public JsonException createException(String message, Exception e) {
        return parser.createException(message, e);
    }

    @Override
    public JsonException createException(String message, byte c) {
        return parser.createException(message, c);
    }

    @Override
    public void mark() {
        if (switched) {
            parser.mark();
        } else {
            marked = true;
            realParser.mark();
        }
    }

    @Override
    public void clearMark() {
        realParser.clearMark();
        marked = false;
    }

    @Override
    public void resetToMark() {
        if (marked) {
            parser = OBJECT_START_PARSER;
            realParser.resetToMark();
            switched = false;
            marked = false;
        } else {
            parser.resetToMark();
        }
    }

    private static final class ForcedObjectStartParser implements JsonParser {

        private ForcedObjectStartParser() {
        }

        @Override
        public boolean hasNext() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public byte nextToken() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public byte currentByte() {
            return '{';
        }

        @Override
        public JsonValue readJsonValue() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public JsonObject readJsonObject() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public JsonArray readJsonArray() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public JsonString readJsonString() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public JsonNumber readJsonNumber() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public String readString() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public int readStringAsHash() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public char readChar() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public boolean readBoolean() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public byte readByte() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public short readShort() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public int readInt() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public long readLong() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public float readFloat() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public double readDouble() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public BigInteger readBigInteger() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public BigDecimal readBigDecimal() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public byte[] readBinary() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public boolean checkNull() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public void skip() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public JsonException createException(String message) {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public JsonException createException(String message, Exception e) {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public void mark() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public void clearMark() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }

        @Override
        public void resetToMark() {
            throw new UnsupportedOperationException("This parser allows only currentByte");
        }
    }
}
