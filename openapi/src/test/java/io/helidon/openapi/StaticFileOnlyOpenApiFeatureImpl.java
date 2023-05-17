/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.io.StringReader;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;
import org.yaml.snakeyaml.Yaml;

public class StaticFileOnlyOpenApiFeatureImpl extends OpenApiFeature {

    private static final System.Logger LOGGER = System.getLogger(StaticFileOnlyOpenApiFeatureImpl.class.getName());

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    static Builder builder() {
        return new Builder();
    }

    private final Map<OpenAPIMediaType, String> staticContent = new HashMap<>();

    /**
     * Constructor for the feature.
     *
     * @param builder builder to use for initializing the feature
     */
    protected StaticFileOnlyOpenApiFeatureImpl(Builder builder) {
        super(LOGGER, builder);
        // We should have a static file containing either YAML or JSON. Create a static file of the other type so we have it.
        if (staticContent().isEmpty()) {
            throw new IllegalArgumentException("Static-only OpenAPI feature does not have static content!");
        }
        OpenApiStaticFile staticFile = staticContent().get();
        staticContent.put(staticFile.openApiMediaType(), staticFile.content());
        if (staticFile.openApiMediaType().equals(OpenAPIMediaType.YAML)) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(staticFile.content());
            // Simplistic - change Date to String because Json does not know how to handle Date
            map = clean(map);
            JsonObject json = JSON.createObjectBuilder(map).build();
            staticContent.put(OpenAPIMediaType.JSON, json.toString());
        } else {
            Yaml yaml = new Yaml();
            yaml.load(staticFile.content());
            staticContent.put(OpenAPIMediaType.YAML, yaml.toString());
        }
    }

    @Override
    protected String openApiContent(OpenAPIMediaType openApiMediaType) {
        // A real implemention would have only one static content instance. This test implementation
        // has two, one each for JSON and YAML.
        return staticContent.get(openApiMediaType);
    }

    private static Map<String, Object> clean(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        map.forEach((k, v) -> {
            if (v instanceof Map vMap) {
                result.put(k, clean(vMap));
            } else if (v instanceof Date date) {
                result.put(k, date.toString());
            } else {
                result.put(k, v);
            }
        });
        return result;
    }

    static class Builder extends OpenApiFeature.Builder<Builder, StaticFileOnlyOpenApiFeatureImpl> {


        private static final System.Logger LOGGER = System.getLogger(Builder.class.getName());

        @Override
        public StaticFileOnlyOpenApiFeatureImpl build() {
            return new StaticFileOnlyOpenApiFeatureImpl(this);
        }

        @Override
        System.Logger logger() {
            return LOGGER;
        }
    }
}
