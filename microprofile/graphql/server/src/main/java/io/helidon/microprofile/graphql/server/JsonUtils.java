/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import static io.helidon.microprofile.graphql.server.ElementGenerator.CLOSE_CURLY;
import static io.helidon.microprofile.graphql.server.ElementGenerator.CLOSE_SQUARE;
import static io.helidon.microprofile.graphql.server.ElementGenerator.COLON;
import static io.helidon.microprofile.graphql.server.ElementGenerator.COMMA_SPACE;
import static io.helidon.microprofile.graphql.server.ElementGenerator.OPEN_CURLY;
import static io.helidon.microprofile.graphql.server.ElementGenerator.OPEN_SQUARE;
import static io.helidon.microprofile.graphql.server.ElementGenerator.QUOTE;
import static io.helidon.microprofile.graphql.server.ElementGenerator.SPACER;

/**
 * Various Json utilities.
 */
public class JsonUtils {

    /**
     * JSONB instance.
     */
    private static final Jsonb JSONB = JsonbBuilder.newBuilder()
            .withConfig(new JsonbConfig().withNullValues(true)).build();

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
     * Convert a Json object into the representative Java object.
     *
     * @param json  the json
     * @param clazz {@link Class} to convert to
     * @return a new {@link Class} instance
     */
    public static Object convertFromJson(String json, Class<?> clazz) {
        return JSONB.fromJson(json, clazz);
    }

    /**
     * Convert JSON value to a GraphQLSDL format.
     * @param value value to convert
     * @return JSON value converted to a GraphQLSDL format
     */
    public static String convertJsonToGraphQLSDL(Object value) {
        return convertJsonToGraphQLSDL(value, false);
    }

    /**
     * Convert JSON value to a GraphQLSDL format.
     * @param value value to convert
     * @param isKey indicates if this value is a key
     * @return JSON value converted to a GraphQLSDL format
     */
    @SuppressWarnings({"unchecked", "rawTypes"})
    public static String convertJsonToGraphQLSDL(Object value, boolean isKey) {
        StringBuffer sb = new StringBuffer();
        if (value instanceof Map) {
            sb.append(convertJsonMapToGraphQLSDL((Map) value));
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof String) {
            if (isKey) {
                sb.append(value.toString());
            } else {
                sb.append(QUOTE).append(value.toString().replaceAll("\"", "\\\\\"")).append(QUOTE);
            }
        } else if (value instanceof Collection) {
            sb.append(OPEN_SQUARE);
            sb.append(((Collection) value).stream()
                              .map(JsonUtils::convertJsonToGraphQLSDL)
                              .collect(Collectors.joining(COMMA_SPACE))).append(CLOSE_SQUARE);
        } else {
            sb.append(value.toString());
        }
        return sb.toString();
    }

    /**
     * Convert a Json {@link Map} to a GraphQL SDL.
     *
     * @param map Json {@link Map} to convert
     * @return GraphQL SDL.
     */
    @SuppressWarnings("unchecked")
    private static String convertJsonMapToGraphQLSDL(Map<String, Object> map) {
        StringBuffer sb = new StringBuffer(OPEN_CURLY);

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(SPACER).append(convertJsonToGraphQLSDL(entry.getKey(), true)).append(COLON).append(SPACER);
            sb.append(convertJsonToGraphQLSDL(entry.getValue()));
        }

        return sb.append(SPACER).append(CLOSE_CURLY).toString();
    }

}

