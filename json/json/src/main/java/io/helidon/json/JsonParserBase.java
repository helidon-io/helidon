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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Base implementation of {@link JsonParser}.
 */
public abstract class JsonParserBase implements JsonParser {

    private int nestingDepth;

    /**
     * Protected default constructor for subclasses.
     */
    protected JsonParserBase() {
    }

    @Override
    public JsonValue readJsonValue() {
        return switch (currentByte()) {
            case '{' -> readJsonObject();
            case '[' -> readJsonArray();
            case '"' -> readJsonString();
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> readJsonNumber();
            case 't', 'f' -> JsonBoolean.create(readBoolean());
            case 'n' -> {
                checkNull();
                yield JsonNull.instance();
            }
            default -> throw createException("Unexpected JSON value type", currentByte());
        };
    }

    @Override
    public JsonObject readJsonObject() {
        if (currentByte() != '{') {
            throw createException("Object start expected", currentByte());
        }
        enterStructure();
        try {
            byte b = nextToken();
            if (b == '}') {
                return JsonObject.EMPTY_OBJECT;
            }
            LinkedHashMap<String, JsonValue> values = new LinkedHashMap<>();
            while (hasNext()) {
                String key;
                if (b == '"') {
                    key = readString();
                } else {
                    throw createException("Key name start expected", b);
                }
                b = nextToken();
                if (b != ':') {
                    throw createException("Colon expected", b);
                }
                b = nextToken();
                JsonValue value = switch (b) {
                case '"' -> readJsonString();
                case '{' -> readJsonObject();
                case '[' -> readJsonArray();
                case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> readJsonNumber();
                case 'n' -> {
                    checkNull();
                    yield JsonNull.instance();
                }
                case 't', 'f' -> JsonBoolean.create(readBoolean());
                default -> throw createException("Unexpected json value type", b);
                };
                values.put(key, value);
                b = nextToken();
                if (b == '}') {
                    return JsonObject.createFromOwnedMap(values);
                } else if (b != ',') {
                    throw createException("Comma or object end expected", b);
                }
                b = nextToken();
            }
            throw createException("Unexpected end of the object. Possibly incomplete JSON");
        } finally {
            exitStructure();
        }
    }

    @Override
    public JsonArray readJsonArray() {
        if (currentByte() != '[') {
            throw createException("Array start expected", currentByte());
        }
        enterStructure();
        try {
            byte b = nextToken();
            if (b == ']') {
                return JsonArray.EMPTY_ARRAY;
            }
            List<JsonValue> values = new ArrayList<>();
            while (hasNext()) {
                switch (b) {
                case '"':
                    values.add(readJsonString());
                    break;
                case '{':
                    values.add(readJsonObject());
                    break;
                case '[':
                    values.add(readJsonArray());
                    break;
                case '-':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    values.add(readJsonNumber());
                    break;
                case 'n':
                    checkNull();
                    values.add(JsonNull.instance());
                    break;
                case 't':
                case 'f':
                    values.add(JsonBoolean.create(readBoolean()));
                    break;
                default:
                    throw createException("Invalid JSON value type", b);
                }
                b = nextToken();
                if (b == ']') {
                    return JsonArray.createFromOwnedList(values);
                } else if (b != ',') {
                    throw createException("Comma or array end expected", b);
                }
                b = nextToken();
            }
            throw createException("Unexpected end of the array. Possibly incomplete JSON");
        } finally {
            exitStructure();
        }
    }

    /**
     * Records entry into an object or array structure.
     */
    final void enterStructure() {
        if (nestingDepth >= MAX_NESTING_DEPTH) {
            throw createException("Maximum JSON nesting depth exceeded");
        }
        nestingDepth++;
    }

    /**
     * Records exit from an object or array structure.
     */
    final void exitStructure() {
        nestingDepth--;
    }

}
