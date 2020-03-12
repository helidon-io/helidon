/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server;

import java.util.Collections;
import java.util.Map;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

/**
 * Various Json utilities.
 */
public class JsonUtils {

    /**
     * JSONB instance.
     */
    private static final Jsonb JSONB = JsonbBuilder.create();

    /**
     * Private constructor for utilities class.
     */
    private JsonUtils() {
    }

    /**
     * Convert a String that "should" contain JSON to a {@link Map}.
     *
     * @param json the Json to convert
     * @return a {@link Map} containing the JSON.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> convertJSONtoMap(String json) {
        if (json == null || json.trim().length() == 0) {
            return Collections.emptyMap();
        }
        return JSONB.fromJson(json, Map.class);
    }

    /**
     * Convert an {@link Map} to a Json String representation.
     *
     * @param map {@link Map} to convert toJson
     * @return a Json String representation
     */
    public static String convertMapToJson(Map map) {
        return JSONB.toJson(map);
    }

    /**
     * Concert a Json object into the representative Java object.
     * @param json  the json
     * @param clazz {@link Class} to convert to
     * @return a new {@link Class} instance
     */
    public static Object convertFromJson(String json, Class<?> clazz) {
        return JSONB.fromJson(json, clazz);
    }
}

