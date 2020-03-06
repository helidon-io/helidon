package io.helidon.microprofile.graphql.server.util;

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
}

