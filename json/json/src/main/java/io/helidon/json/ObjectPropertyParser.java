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

final class ObjectPropertyParser extends JsonParserBase {

    private final JsonParser parser;

    private int remaining;
    private int depth = 1;
    private int markedRemaining;
    private int markedDepth;

    ObjectPropertyParser(JsonParser parser, int propertyIndex) {
        this.parser = parser;
        this.remaining = propertyIndex;
    }

    @Override
    public boolean hasNext() {
        return parser.hasNext();
    }

    @Override
    public byte currentByte() {
        return parser.currentByte();
    }

    @Override
    public byte nextToken() {
        byte token = parser.nextToken();
        if (remaining < 0) {
            return token;
        }
        if (depth == 1) {
            if (token == ',' && --remaining == 0) {
                parser.nextToken();
                return omitProperty();
            }
            if (remaining == 0 && token == '"') {
                token = omitProperty();
                return token == ',' ? parser.nextToken() : token;
            }
        }
        switch (token) {
        case '{', '[' -> depth++;
        case '}', ']' -> depth--;
        default -> { }
        }
        return token;
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
        if (remaining >= 0 && depth == 1) {
            return super.readJsonObject();
        }
        JsonObject value = parser.readJsonObject();
        finishStructure((byte) '{');
        return value;
    }

    @Override
    public JsonArray readJsonArray() {
        JsonArray value = parser.readJsonArray();
        finishStructure((byte) '[');
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
        byte token = parser.currentByte();
        parser.skip();
        if (remaining >= 0 && (token == '{' || token == '[')) {
            depth--;
            if (depth == 0) {
                remaining = -1;
            }
        }
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
        parser.mark();
        markedRemaining = remaining;
        markedDepth = depth;
    }

    @Override
    public void clearMark() {
        parser.clearMark();
    }

    @Override
    public void resetToMark() {
        parser.resetToMark();
        remaining = markedRemaining;
        depth = markedDepth;
    }

    private byte omitProperty() {
        if (parser.currentByte() != '"') {
            throw parser.createException("Expected a property name", parser.currentByte());
        }
        parser.skip();
        if (parser.nextToken() != ':') {
            throw parser.createException("Expected ':' after property name", parser.currentByte());
        }
        parser.nextToken();
        parser.skip();
        byte token = parser.nextToken();
        if (token != ',' && token != '}') {
            throw parser.createException("Expected ',' or '}' after property value", token);
        }
        remaining = -1;
        return token;
    }

    private void finishStructure(byte start) {
        // Value-backed parsers can return the existing value without advancing their cursor.
        if (parser.currentByte() == start) {
            parser.skip();
        }
        if (remaining >= 0) {
            depth--;
        }
    }
}
