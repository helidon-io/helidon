/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.test.utils;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonWriterFactory;

/**
 * General json utilities.
 */
public class JsonUtils {
    private static final JsonReaderFactory READER_FACTORY = Json.createReaderFactory(Map.of());
    private static final JsonWriterFactory WRITER_FACTORY = Json.createWriterFactory(Map.of());

    private JsonUtils() {
    }

    /**
     * Marshal the provided object to a json string.
     *
     * @param obj the object to marshal
     * @return the json string.
     */
    public static String prettyPrintJson(Object obj) {
        ObjectMapper mapper = new ObjectMapper()
                        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        DefaultPrettyPrinter.Indenter indenter =
                new DefaultIndenter("    ", DefaultIndenter.SYS_LF);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        try {
            return mapper.writer(printer).writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Will take previously generated json, and reformat it according to the internal marshaller.
     *
     * @param jsonStr the json
     * @return the normalized json
     */
    public static String normalizeJson(String jsonStr) {
        JsonObject json = READER_FACTORY.createReader(new StringReader(jsonStr)).readObject();
        StringWriter jsonOut = new StringWriter();
        WRITER_FACTORY.createWriter(jsonOut).write(json);
        return jsonOut.toString();
    }

}
