package io.helidon.microprofile.graphql.server.util;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Various Json utilities.
 */
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    public static Map<String, Object> convertJSONtoMap(String json) throws JsonProcessingException {
        if (json == null || json.trim().length() == 0) {
            return Collections.emptyMap();
        }
        return OBJECT_MAPPER.readValue(json, new TypeReference<>() { });
    }

    /**
     * Convert an {@link Map} to a Json String representation.
     *
     * @param map {@link Map} to convert toJson
     * @return a Json String representation
     */
    public static String convertMapToJson(Map map) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(map);
    }
}

