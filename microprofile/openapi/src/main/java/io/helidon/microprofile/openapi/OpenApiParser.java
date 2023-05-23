/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Abstraction for SnakeYAML parsing of JSON and YAML.
 */
final class OpenApiParser {

    private OpenApiParser() {
    }

    /**
     * Parse open API.
     *
     * @param types types
     * @param inputStream input stream to parse from
     * @return parsed document
     * @throws IOException in case of I/O problems
     */
    static OpenAPI parse(Map<Class<?>, ExpandedTypeDescription> types, InputStream inputStream) throws IOException {
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return parse(types, OpenAPI.class, reader);
        }
    }

//    static OpenAPI parse(Map<Class<?>, ExpandedTypeDescription> types, Reader reader) {
//        TypeDescription openAPITD = types.get(OpenAPI.class);
//        Constructor topConstructor = new CustomConstructor(openAPITD);
//
//        types.values()
//                .forEach(topConstructor::addTypeDescription);
//
//        Yaml yaml = new Yaml(topConstructor);
//        OpenAPI result = yaml.loadAs(reader, OpenAPI.class);
//        return result;
//    }

    /**
     * Parse YAML or JSON using the specified types, returning the specified type with input taken from the indicated reader.
     *
     * @param types type descriptions
     * @param tClass POJO type to be parsed
     * @param reader {@code Reader} containing the JSON or YAML input
     * @return the parsed object
     * @param <T> the type to be returned
     */
    static <T> T parse(Map<Class<?>, ExpandedTypeDescription> types, Class<T> tClass, Reader reader) {
        return parse(types, tClass, reader, new Representer(new DumperOptions()));
    }

    /**
     * Parse YAML or JSON using the specified types, returning the indicated type with input from the specified reader.
     *
     * @param types type descriptions
     * @param tClass POJO type to be parsed
     * @param reader {@code Reader} containing the JSON or YAML input
     * @param representer the {@code Representer} to use during parsing
     * @return the parsed object
     * @param <T> the type to be returned
     */
    static <T> T parse(Map<Class<?>, ExpandedTypeDescription> types, Class<T> tClass, Reader reader, Representer representer) {
        TypeDescription td = types.get(tClass);
        Constructor topConstructor = new CustomConstructor(td);
        types.values()
                .forEach(topConstructor::addTypeDescription);

        Yaml yaml = new Yaml(topConstructor, representer);
        return yaml.loadAs(reader, tClass);
    }
}
