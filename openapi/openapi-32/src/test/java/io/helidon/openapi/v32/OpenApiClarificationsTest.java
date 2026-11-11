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

package io.helidon.openapi.v32;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.OpenApiDocumentContext;
import io.helidon.openapi.OpenApiGeneratedMode;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.v30.OpenApi30Version;
import io.helidon.openapi.v30.OpenApiDocumentMapperSupport;
import io.helidon.openapi.v30.OpenApiDocumentReader;
import io.helidon.openapi.v31.OpenApi31Version;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiClarificationsTest {
    @Test
    void rejectsStylesOutsideTheirParameterLocationsAcrossPatchVersions() {
        for (OpenApiVersion version : versions()) {
            for (List<String> invalid : List.of(List.of("path", "form"),
                                                List.of("query", "simple"),
                                                List.of("header", "form"),
                                                List.of("cookie", "simple"),
                                                List.of("query", "unknown"))) {
                assertRejected(version, component("parameters", parameter(invalid.get(0), invalid.get(1), "string")),
                               "components.parameters.Test", "style", invalid.get(1), invalid.get(0));
            }
            if (!version.version().startsWith("3.2")) {
                assertRejected(version, component("parameters", parameter("cookie", "cookie", "string")),
                               "components.parameters.Test", "style", "cookie");
            }
        }
    }

    @Test
    void preservesSupportedParameterSerializationAcrossPatchVersions() {
        for (OpenApiVersion version : versions()) {
            for (List<String> valid : List.of(List.of("path", "matrix", "string"),
                                              List.of("path", "label", "array"),
                                              List.of("path", "simple", "object"),
                                              List.of("query", "form", "string"),
                                              List.of("query", "spaceDelimited", "array"),
                                              List.of("query", "pipeDelimited", "object"),
                                              List.of("query", "deepObject", "object"),
                                              List.of("header", "simple", "string"),
                                              List.of("cookie", "form", "string"))) {
                Map<String, Object> parameter = parameter(valid.get(0), valid.get(1), valid.get(2));
                assertComponentRoundTrip(version, "parameters", parameter);
            }
            if (version.version().startsWith("3.2")) {
                assertComponentRoundTrip(version, "parameters", parameter("cookie", "cookie", "object"));
            }
        }
    }

    @Test
    void keepsAbsentSerializationDefaultsAbsent() {
        for (OpenApiVersion version : versions()) {
            for (String location : List.of("path", "query", "header", "cookie")) {
                Map<String, Object> parameter = parameter(location, "simple", "string");
                parameter.remove("style");
                assertComponentRoundTrip(version, "parameters", parameter);
            }
            assertComponentRoundTrip(version, "headers", Map.of("schema", Map.of("type", "string")));
        }
    }

    @Test
    void rejectsForbiddenHeaderFieldsWithTheirLocations() {
        for (OpenApiVersion version : versions()) {
            for (Map.Entry<String, Object> field : Map.<String, Object>of("name", "X-Test",
                                                                         "in", "header",
                                                                         "allowEmptyValue", false).entrySet()) {
                Map<String, Object> header = new LinkedHashMap<>();
                header.put("schema", Map.of("type", "string"));
                header.put(field.getKey(), field.getValue());
                // The public Header model deliberately has no name, in, or allowEmptyValue properties.
                assertParseRejected(version, component("headers", header), "components.headers.Test", field.getKey());
            }
            assertRejected(version, component("headers", Map.of("schema", Map.of("type", "string"), "style", "form")),
                           "components.headers.Test", "style", "form");
            Map<String, Object> reservedHeader = Map.of("schema", Map.of("type", "string"), "allowReserved", false);
            if (version.version().startsWith("3.2")) {
                Map<String, Object> rendered = roundTrip(version, component("headers", reservedHeader));
                assertThat(version.version(), componentValue(rendered, "headers"),
                           is(Map.of("schema", Map.of("type", "string"))));
            } else {
                assertParseRejected(version, component("headers", reservedHeader),
                                    "components.headers.Test", "allowReserved");
            }
        }
    }

    @Test
    void rejectsExplicitUnsupportedSerializationTypesIn32() {
        for (OpenApiVersion version : versions32()) {
            for (List<String> invalid : List.of(List.of("deepObject", "array"),
                                                List.of("deepObject", "string"),
                                                List.of("spaceDelimited", "integer"),
                                                List.of("pipeDelimited", "boolean"))) {
                assertRejected(version, component("parameters", parameter("query", invalid.get(0), invalid.get(1))),
                               "components.parameters.Test", "style", invalid.get(0), "type");
            }
            for (String style : List.of("form", "cookie")) {
                for (String type : List.of("array", "object")) {
                    Map<String, Object> parameter = parameter("cookie", style, type);
                    parameter.put("explode", false);
                    assertRejected(version, component("parameters", parameter),
                                   "components.parameters.Test", "cookie", "explode");
                }
                Map<String, Object> primitive = parameter("cookie", style, "string");
                primitive.put("explode", false);
                assertComponentRoundTrip(version, "parameters", primitive);
            }
        }
    }

    @Test
    void defersUnknownSchemaTypesAndUndefinedSerializationCombinations() {
        for (OpenApiVersion version : versions32()) {
            for (Map<String, Object> schema : List.<Map<String, Object>>of(Map.of(),
                                                                         Map.of("$ref", "#/components/schemas/Unknown"),
                                                                         Map.of("type", List.of("object", "null")))) {
                Map<String, Object> parameter = parameter("query", "deepObject", "object");
                parameter.put("schema", schema);
                Map<String, Object> root = component("parameters", parameter);
                root.put("components", Map.of("parameters", Map.of("Test", parameter),
                                              "schemas", Map.of("Unknown", Map.of())));
                Map<String, Object> rendered = roundTrip(version, root);
                assertThat(version.version(), componentValue(rendered, "parameters"), is(parameter));
            }
            for (String style : List.of("spaceDelimited", "pipeDelimited", "deepObject")) {
                Map<String, Object> parameter = parameter("query", style, "object");
                parameter.put("explode", !"deepObject".equals(style));
                assertComponentRoundTrip(version, "parameters", parameter);
            }
        }
    }

    @Test
    void appliesParameterStyleRulesOnlyInSchemaMode() {
        for (OpenApiVersion version : versions()) {
            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put("name", "Test");
            parameter.put("in", "query");
            parameter.put("content", Map.of("text/plain", Map.of("schema", Map.of("type", "string"))));
            parameter.put("style", "simple");
            assertComponentRoundTrip(version, "parameters", parameter);
        }
    }

    @Test
    void rejectsEquivalentTemplatedPathHierarchiesAcrossPatchVersions() {
        for (OpenApiVersion version : versions()) {
            Map<String, Object> paths = new LinkedHashMap<>();
            paths.put("/pets/{id}", pathItem("id"));
            paths.put("/pets/{name}", pathItem("name"));
            Map<String, Object> document = document(paths);
            assertParseRejected(version, document,
                                "Conflicting OpenAPI path template", "/pets/{id}", "/pets/{name}");
            // Model construction already prevents an invalid document from reaching rendering.
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApiDocumentReader.read(OpenApiDocumentMapperSupport.jsonObject(document)),
                    version.version() + " model construction");
            assertThat(thrown.getMessage(), containsString("Conflicting OpenAPI path template"));
            assertThat(thrown.getMessage(), containsString("/pets/{id}"));
            assertThat(thrown.getMessage(), containsString("/pets/{name}"));
        }
    }

    @Test
    void preservesDistinctPathsExtensionsAndSlashInTemplateNames() {
        for (OpenApiVersion version : versions()) {
            Map<String, Object> paths = new LinkedHashMap<>();
            paths.put("/pets/{id}", pathItem("id"));
            paths.put("/owners/{name}", pathItem("name"));
            paths.put("/pets/mine", Map.of());
            paths.put("/pets/{group/id}/details", pathItem("group/id"));
            paths.put("x-paths", Map.of("/pets/{renamed}", Map.of()));
            Map<String, Object> rendered = roundTrip(version, document(paths));
            assertThat(version.version(), rendered.get("paths"), is(paths));
        }
    }

    @Test
    void preservesPercentEncodedHeaderAndCookieExamples() {
        for (OpenApiVersion version : versions()) {
            for (String location : List.of("header", "cookie")) {
                Map<String, Object> parameter = parameter(location,
                                                          "header".equals(location) ? "simple" : "form", "string");
                parameter.put("example", "name%3Dvalue%26more%2Fdata%2520");
                assertComponentRoundTrip(version, "parameters", parameter);
            }
            assertComponentRoundTrip(version, "headers",
                                     Map.of("schema", Map.of("type", "string"), "example", "%2F%26%3D%2520"));
        }
    }

    @Test
    void preservesMultipartEncodingMetadataAcrossPatchVersions() {
        Map<String, Object> encoding = Map.of(
                "contentType", "application/json; profile=\"a,b\", image/*",
                "style", "form",
                "explode", true,
                "allowReserved", true,
                "headers", Map.of("Content-Type", Map.of("schema", Map.of("type", "string"))));
        Map<String, Object> mediaType = Map.of(
                "schema", Map.of("type", "object", "properties", Map.of(
                        "files", Map.of("type", "array", "items", Map.of("type", "string")),
                        "label", Map.of("type", "string"))),
                "encoding", Map.of("files", encoding, "label", Map.of()));
        for (OpenApiVersion version : versions()) {
            assertComponentRoundTrip(version, "requestBodies",
                                     Map.of("content", Map.of("multipart/form-data", mediaType)));
        }
    }

    @Test
    void preservesSchemaReferenceSiblingsIn31And32() {
        Map<String, Object> schema = Map.of("$ref", "#/components/schemas/Base",
                                          "type", "string", "maxLength", 12, "description", "A short identifier");
        for (OpenApiVersion version : versions()) {
            if (version.version().startsWith("3.0")) {
                continue;
            }
            Map<String, Object> document = document(Map.of());
            document.put("components", Map.of("schemas", Map.of("Test", schema, "Base", Map.of("type", "string"))));
            assertThat(version.version(), componentValue(roundTrip(version, document), "schemas"), is(schema));
        }
    }

    private static List<OpenApiVersion> versions() {
        return List.of(OpenApi30Version.builder().version("3.0.3").build(),
                       OpenApi30Version.builder().version("3.0.4").build(),
                       OpenApi31Version.builder().version("3.1.1").build(),
                       OpenApi31Version.builder().version("3.1.2").build(),
                       OpenApi32Version.builder().version("3.2.0").build(),
                       OpenApi32Version.builder().version("3.2.1").build());
    }

    private static List<OpenApiVersion> versions32() {
        return List.of(OpenApi32Version.builder().version("3.2.0").build(),
                       OpenApi32Version.builder().version("3.2.1").build());
    }

    private static Map<String, Object> parameter(String location, String style, String type) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("name", "Test");
        parameter.put("in", location);
        parameter.put("style", style);
        parameter.put("schema", Map.of("type", type));
        if ("path".equals(location)) {
            parameter.put("required", true);
        }
        return parameter;
    }

    private static Map<String, Object> component(String kind, Map<String, Object> value) {
        Map<String, Object> document = document(Map.of());
        document.put("components", Map.of(kind, Map.of("Test", value)));
        return document;
    }

    private static Map<String, Object> document(Map<String, Object> paths) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("info", Map.of("title", "API", "version", "1"));
        document.put("paths", paths);
        return document;
    }

    private static Map<String, Object> pathItem(String name) {
        return Map.of("parameters", List.of(Map.of("name", name, "in", "path", "required", true,
                                                  "schema", Map.of("type", "string"))));
    }

    private static void assertRejected(OpenApiVersion version, Map<String, Object> document, String... diagnostics) {
        assertParseRejected(version, document, diagnostics);
        OpenApiDocument generated = OpenApiDocumentReader.read(OpenApiDocumentMapperSupport.jsonObject(document));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                   () -> version.render(context(version), generated),
                                                   version.version() + " render " + document);
        for (String diagnostic : diagnostics) {
            assertThat(version.version() + " render", thrown.getMessage(), containsString(diagnostic));
        }
    }

    private static void assertParseRejected(OpenApiVersion version,
                                            Map<String, Object> document,
                                            String... diagnostics) {
        document.put("openapi", version.version());
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                   () -> version.parse(context(version), new Yaml().dump(document),
                                                                       MediaTypes.APPLICATION_OPENAPI_YAML),
                                                   version.version() + " parse " + document);
        for (String diagnostic : diagnostics) {
            assertThat(version.version() + " parse", thrown.getMessage(), containsString(diagnostic));
        }
    }

    private static void assertComponentRoundTrip(OpenApiVersion version, String kind, Map<String, Object> value) {
        Map<String, Object> rendered = roundTrip(version, component(kind, value));
        assertThat(version.version() + " " + kind, componentValue(rendered, kind), is(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> roundTrip(OpenApiVersion version, Map<String, Object> document) {
        document.put("openapi", version.version());
        OpenApiDocument parsed = version.parse(context(version), new Yaml().dump(document),
                                               MediaTypes.APPLICATION_OPENAPI_YAML);
        return new Yaml().load(version.render(context(version), parsed));
    }

    private static Object componentValue(Map<String, Object> document, String kind) {
        return ((Map<?, ?>) ((Map<?, ?>) document.get("components")).get(kind)).get("Test");
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
}
