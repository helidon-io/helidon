/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

public final class VaultUtil {
    private VaultUtil() {
    }

    /**
     * Create a list of strings from JSON array.
     *
     * @param array array to process
     * @return each element from the array as a string
     */
    public static List<String> arrayToList(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> result = new LinkedList<>();
        array.forEach(it -> result.add(stringValue(it)));
        return List.copyOf(result);
    }

    /**
     * Process response from {@code LIST} operations.
     * Finds the {@code data} object, and processes the {@code keys} array.
     *
     * @param response JSON response from API {@code LIST} call
     * @return keys as a list of strings
     */
    public static List<String> processListDataResponse(JsonObject response) {
        JsonObject data = response.getJsonObject("data");
        JsonArray keys = data.getJsonArray("keys");
        List<String> result = new ArrayList<>(keys.size());
        keys.forEach(it -> result.add(((JsonString) it).getString()));
        return result;
    }

    /**
     * Return a map of a json object that is nested in the provided object.
     * If the name is {@code nil} or not present, returns an empty map.
     *
     * @param object  JSON object to process
     * @param name name of a nested JSON object to return as a map
     * @return map representation of the nested object
     */
    public static Map<String, String> toMap(JsonObject object, String name) {
        Map<String, String> result = new HashMap<>();
        if (!object.isNull(name) && object.containsKey(name)) {
            JsonObject subObject = object.getJsonObject(name);
            subObject.forEach((key, value) -> result.put(key, stringValue(value)));
        }
        return Map.copyOf(result);
    }

    /**
     * Get a string value from a json value.
     *
     * @param value Json value
     * @return string representation of the value.
     * @throws VaultRestException in case the value is array or object
     */
    private static String stringValue(JsonValue value) {
        switch (value.getValueType()) {
        case ARRAY:
            throw new VaultApiException("Cannot create a simple String from an array: " + value);
        case OBJECT:
            throw new VaultApiException("Cannot create a simple String from an object: " + value);
        case STRING:
            return ((JsonString) value).getString();
        case TRUE:
            return "true";
        case FALSE:
            return "false";
        case NULL:
            return "null";
        case NUMBER:
        default:
            return value.toString();
        }
    }
}
