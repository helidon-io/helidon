/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Abstraction for SnakeYAML parsing of JSON and YAML.
 */
final class OpenAPIParser {

    private OpenAPIParser() {
    }

    static OpenAPI parse(Map<Class<?>, ExpandedTypeDescription> types, InputStream inputStream,
            OpenAPISupport.OpenAPIMediaType openAPIMediaType) throws IOException {
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return parse(types, reader, openAPIMediaType);
        }
    }

    static OpenAPI parse(Map<Class<?>, ExpandedTypeDescription> types, Reader reader,
            OpenAPISupport.OpenAPIMediaType openAPIMediaType) {
        TypeDescription openAPITD = types.get(OpenAPI.class);
        Constructor topConstructor = new CustomConstructor(openAPITD);

        types.values()
                .forEach(topConstructor::addTypeDescription);

        Yaml yaml = new Yaml(topConstructor);
        OpenAPI result = yaml.loadAs(reader, OpenAPI.class);
        return result;
    }
}
