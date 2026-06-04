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

package io.helidon.openapi.v31;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.OpenApiDocumentContext;
import io.helidon.openapi.OpenApiGeneratedMode;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.spi.OpenApiVersionProvider;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApi31VersionTest {
    @Test
    void rendersJsonSchemaVocabularyWithoutOpenApi30Translation() {
        OpenApiDocument document = OpenApiDocument.builder()
                .openapi("3.1.0")
                .jsonSchemaDialect("https://json-schema.org/draft/2020-12/schema")
                .info(info -> info.title("Generated API")
                        .version("1.0.0")
                        .summary("Generated summary."))
                .components(components -> components.schema("Item",
                                                            JsonObject.builder()
                                                                    .set("type", "object")
                                                                    .set("properties", properties -> properties
                                                                            .set("status", JsonObject.builder()
                                                                                    .setValues("type", List.of(
                                                                                            JsonString.create("string"),
                                                                                            JsonString.create("null")))
                                                                                    .setValues("enum", List.of(
                                                                                            JsonString.create("new"),
                                                                                            JsonString.create("done"),
                                                                                            JsonNull.instance()))
                                                                                    .build())
                                                                            .set("payload", JsonBoolean.TRUE)
                                                                            .set("mode", JsonObject.builder()
                                                                                    .set("const", "modern")
                                                                                    .build()))
                                                                    .build()))
                .build();

        OpenApi31Version version = OpenApi31Version.create();
        Map<String, Object> rendered = parse(version.render(context(version), document));

        assertThat(rendered.get("openapi"), is("3.1.1"));
        assertThat(rendered.get("jsonSchemaDialect"), is("https://json-schema.org/draft/2020-12/schema"));
        assertThat(map(rendered, "info").get("summary"), is("Generated summary."));

        Map<String, Object> status = schemaProperty(rendered, "Item", "status");
        assertThat(status.get("type"), is(List.of("string", "null")));
        assertThat(((List<?>) status.get("enum")).contains(null), is(true));

        Object payload = schemaPropertyValue(rendered, "Item", "payload");
        assertThat(payload, is(true));

        Map<String, Object> mode = schemaProperty(rendered, "Item", "mode");
        assertThat(mode.get("const"), is("modern"));
    }

    @Test
    void parsesJsonSchemaVocabularyIntoCanonicalDocument() {
        OpenApi31Version version = OpenApi31Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = version.parse(context, static31(), MediaTypes.APPLICATION_OPENAPI_YAML);

        Map<String, Object> rendered = parse(version.render(context, document));

        assertThat(rendered.get("openapi"), is("3.1.1"));
        assertThat(rendered.get("jsonSchemaDialect"), is("https://spec.openapis.org/oas/3.1/dialect/base"));
        assertThat(map(rendered, "info").get("summary"), is("Static document using OpenAPI 3.1 features."));
        assertThat(map(rendered, "webhooks").containsKey("itemChanged"), is(true));
        assertThat(schemaProperty(rendered, "StaticItem", "status").get("type"), is(List.of("string", "null")));
        assertThat(((List<?>) schemaProperty(rendered, "StaticItem", "status").get("enum")).contains(null), is(true));
        assertThat(schemaPropertyValue(rendered, "StaticItem", "payload"), is(true));
    }

    @Test
    void parsesOnlyOpenApi31Documents() {
        OpenApi31Version version = OpenApi31Version.create();

        assertThrows(IllegalStateException.class,
                     () -> version.parse(context(version),
                                         """
                                         openapi: 3.0.3
                                         info:
                                           title: Static API
                                           version: 1.0.0
                                         """,
                                         MediaTypes.APPLICATION_OPENAPI_YAML));
    }

    @Test
    void rejectsNullArguments() {
        OpenApi31Version version = OpenApi31Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = OpenApiDocument.builder().build();

        assertThrows(NullPointerException.class, () -> OpenApi31Version.create((OpenApi31VersionConfig) null));
        assertThrows(NullPointerException.class, () -> version.parse(null, "", MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThrows(NullPointerException.class, () -> version.parse(context, null, MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThrows(NullPointerException.class, () -> version.parse(context, "", null));
        assertThrows(NullPointerException.class, () -> version.render(null, document));
        assertThrows(NullPointerException.class, () -> version.render(context, null));
    }

    @Test
    void serviceLoaderDiscoversProvider() {
        boolean found = ServiceLoader.load(OpenApiVersionProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(provider -> "3.1".equals(provider.configKey()));

        assertThat(found, is(true));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String content) {
        return new Yaml().load(content);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperty(Map<String, Object> document, String schemaName, String propertyName) {
        return (Map<String, Object>) schemaPropertyValue(document, schemaName, propertyName);
    }

    private static Object schemaPropertyValue(Map<String, Object> document, String schemaName, String propertyName) {
        return map(map(map(map(document, "components"), "schemas"), schemaName), "properties")
                .get(propertyName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<?, ?> map, String name) {
        return (Map<String, Object>) map.get(name);
    }

    private static OpenApiDocumentContext context(OpenApiVersion version) {
        return new TestOpenApiDocumentContext(version);
    }

    private record TestOpenApiDocumentContext(OpenApiVersion openApiVersion) implements OpenApiDocumentContext {
        @Override
        public String featureName() {
            return "openapi";
        }

        @Override
        public String webContext() {
            return "/openapi";
        }

        @Override
        public String listener() {
            return "default";
        }

        @Override
        public OpenApiGeneratedMode generatedMode() {
            return OpenApiGeneratedMode.STATIC_ONLY;
        }
    }

    private static String static31() {
        try (InputStream is = OpenApi31VersionTest.class.getResourceAsStream("/static-3.1.yaml")) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: static-3.1.yaml");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
