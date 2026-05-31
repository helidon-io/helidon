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

package io.helidon.openapi;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.json.schema.Schema;
import io.helidon.openapi.spi.OpenApiDocumentSource;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.v30.OpenApi30Version;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiDocumentComposerTest {
    private static final String STATIC_DOCUMENT = """
            openapi: 3.0.3
            info:
              title: Static API
              version: 1.0.0
            paths:
              /static:
                get:
                  operationId: staticGet
                  responses:
                    "200":
                      description: Static response.
            """;

    @Test
    void generatedFallbackKeepsStaticDocument() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.STATIC_FIRST);
        String content = OpenApiDocumentComposer.compose(context,
                                                        context.openApiVersion(),
                                                        STATIC_DOCUMENT,
                                                        MediaTypes.APPLICATION_OPENAPI_YAML,
                                                        List.of(source()));

        assertThat(content, is(STATIC_DOCUMENT));
    }

    @Test
    void generatedFallbackUsesGeneratedSourcesWithoutStaticDocument() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.STATIC_FIRST);
        String content = OpenApiDocumentComposer.compose(context,
                                                        context.openApiVersion(),
                                                        "",
                                                        MediaTypes.APPLICATION_OPENAPI_YAML,
                                                        List.of(source()));

        Map<String, Object> parsed = parse(content);
        assertThat(parsed.get("openapi"), is("3.0.3"));
        assertThat(((Map<?, ?>) parsed.get("info")).get("title"), is("Generated API"));
        assertThat(((Map<?, ?>) parsed.get("paths")).containsKey("/generated"), is(true));
    }

    @Test
    void ignoreGeneratedReturnsEmptyWithoutStaticDocument() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.STATIC_ONLY);
        String content = OpenApiDocumentComposer.compose(context,
                                                        context.openApiVersion(),
                                                        "",
                                                        MediaTypes.APPLICATION_OPENAPI_YAML,
                                                        List.of(source()));

        assertThat(content, is(""));
    }

    @Test
    void generatedOnlyIgnoresStaticDocument() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        String content = OpenApiDocumentComposer.compose(context,
                                                        context.openApiVersion(),
                                                        STATIC_DOCUMENT,
                                                        MediaTypes.APPLICATION_OPENAPI_YAML,
                                                        List.of(source()));

        Map<String, Object> parsed = parse(content);
        assertThat(((Map<?, ?>) parsed.get("info")).get("title"), is("Generated API"));
        assertThat(((Map<?, ?>) parsed.get("paths")).containsKey("/static"), is(false));
        assertThat(((Map<?, ?>) parsed.get("paths")).containsKey("/generated"), is(true));
    }

    @Test
    void mergeStaticFailsOnConflictingOperation() {
        OpenApiDocumentSource conflicting = (context, document) -> document.putOperation(
                "/static",
                "GET",
                OpenApiDocument.Operation.builder()
                        .operationId("other")
                        .build());

        assertThrows(IllegalStateException.class,
                     () -> {
                         OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);
                         OpenApiDocumentComposer.compose(context,
                                                         context.openApiVersion(),
                                                         STATIC_DOCUMENT,
                                                         MediaTypes.APPLICATION_OPENAPI_YAML,
                                                         List.of(conflicting));
                     });
    }

    @Test
    void mergeStaticUsesStaticDocumentVersionParser() {
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        OpenApiVersion staticVersion = new TestOpenApiVersion("3.1", "3.1.0", false);
        OpenApiDocumentContext context = new OpenApiDocumentContext("openapi",
                                                                    "/openapi",
                                                                    "default",
                                                                    OpenApiGeneratedMode.MERGE,
                                                                    renderVersion);

        String content = OpenApiDocumentComposer.compose(context,
                                                        staticVersion,
                                                        STATIC_DOCUMENT,
                                                        MediaTypes.APPLICATION_OPENAPI_YAML,
                                                        List.of(operationSource()));

        Map<String, Object> parsed = parse(content);
        assertThat(((Map<?, ?>) parsed.get("paths")).containsKey("/static"), is(true));
        assertThat(((Map<?, ?>) parsed.get("paths")).containsKey("/generated"), is(true));
    }

    @Test
    void mergeStaticKeepsAdditionalAndFixedOperations() {
        OpenApiDocumentContext context = new OpenApiDocumentContext("openapi",
                                                                    "/openapi",
                                                                    "default",
                                                                    OpenApiGeneratedMode.MERGE,
                                                                    RawOpenApiVersion.INSTANCE);
        OpenApiDocumentSource generated = (ignored, document) -> document
                .putOperation("/static", "COPY", responseOperation("copyStatic"))
                .putOperation("/static", "POST", responseOperation("createStatic"));

        String content = OpenApiDocumentComposer.compose(context,
                                                        RawOpenApiVersion.INSTANCE,
                                                        STATIC_DOCUMENT,
                                                        MediaTypes.APPLICATION_OPENAPI_YAML,
                                                        List.of(generated));

        Map<String, Object> path = map(map(parse(content), "paths"), "/static");
        assertThat(map(path, "additionalOperations").containsKey("COPY"), is(true));
        assertThat(path.containsKey("post"), is(true));
    }

    @Test
    void mergeStaticFailsOnConflictingAdditionalOperation() {
        OpenApiDocumentContext context = new OpenApiDocumentContext("openapi",
                                                                    "/openapi",
                                                                    "default",
                                                                    OpenApiGeneratedMode.MERGE,
                                                                    RawOpenApiVersion.INSTANCE);
        OpenApiDocumentSource generated = (ignored, document) -> document.putOperation("/static",
                                                                                      "COPY",
                                                                                      responseOperation("copyOther"));

        assertThrows(IllegalStateException.class,
                     () -> OpenApiDocumentComposer.compose(context,
                                                           RawOpenApiVersion.INSTANCE,
                                                           STATIC_DOCUMENT_WITH_ADDITIONAL_OPERATION,
                                                           MediaTypes.APPLICATION_OPENAPI_YAML,
                                                           List.of(generated)));
    }

    @Test
    void putSchemaUsesJsonSchemaModelWithoutRootKeywords() {
        Schema schema = Schema.builder()
                .id(URI.create("https://example.com/schemas/item"))
                .rootObject(builder -> builder.addStringProperty("name", name -> name.description("Item name")))
                .build();
        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .putSchema("Item", schema)
                .build();

        Map<String, Object> item = map(map(map(parse(OpenApi30Version.create().render(context(OpenApiGeneratedMode.STATIC_ONLY),
                                                                                     document)),
                                            "components"),
                                        "schemas"),
                                      "Item");

        assertThat(item.containsKey("$schema"), is(false));
        assertThat(item.containsKey("$id"), is(false));
        assertThat(item.get("type"), is("object"));
        assertThat(map(item, "properties").containsKey("name"), is(true));
    }

    private static OpenApiDocumentSource source() {
        return (context, document) -> document.info("Generated API", "1.0.0")
                .putOperation("/generated",
                              "GET",
                              OpenApiDocument.Operation.builder()
                                      .operationId("generatedGet")
                                      .response("200", "Generated response.")
                                      .build());
    }

    private static OpenApiDocumentSource operationSource() {
        return (context, document) -> document.putOperation("/generated",
                                                            "GET",
                                                            OpenApiDocument.Operation.builder()
                                                                    .operationId("generatedGet")
                                                                    .response("200", "Generated response.")
                                                                    .build());
    }

    private static OpenApiDocument.Operation responseOperation(String operationId) {
        return OpenApiDocument.Operation.builder()
                .operationId(operationId)
                .response("200", "OK")
                .build();
    }

    private static OpenApiDocumentContext context(OpenApiGeneratedMode mode) {
        return new OpenApiDocumentContext("openapi",
                                          "/openapi",
                                          "default",
                                          mode,
                                          OpenApi30Version.create());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String content) {
        return new Yaml().load(content);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<?, ?> map, String name) {
        return (Map<String, Object>) map.get(name);
    }

    private record TestOpenApiVersion(String type, String version, boolean failParse) implements OpenApiVersion {
        @Override
        public OpenApiDocument parse(OpenApiDocumentContext context,
                                     String content,
                                     io.helidon.common.media.type.MediaType mediaType) {
            if (failParse) {
                throw new AssertionError("Configured render version must not parse static content.");
            }
            return OpenApi30Version.create().parse(context, content, mediaType);
        }

        @Override
        public String render(OpenApiDocumentContext context, OpenApiDocument document) {
            return OpenApi30Version.create().render(context, document);
        }

        @Override
        public String name() {
            return type;
        }
    }

    private static final class RawOpenApiVersion implements OpenApiVersion {
        private static final RawOpenApiVersion INSTANCE = new RawOpenApiVersion();

        private RawOpenApiVersion() {
        }

        @Override
        public OpenApiDocument parse(OpenApiDocumentContext context,
                                     String content,
                                     io.helidon.common.media.type.MediaType mediaType) {
            OpenApiDocument document = OpenApi30Version.create().parse(context, content, mediaType);
            if (content.contains("operationId: staticCopy")) {
                return OpenApiDocument.builder()
                        .merge(document)
                        .putOperation("/static", "COPY", responseOperation("staticCopy"))
                        .build();
            }
            return document;
        }

        @Override
        public String render(OpenApiDocumentContext context, OpenApiDocument document) {
            return document.toJsonObject().toString();
        }

        @Override
        public String version() {
            return "raw";
        }

        @Override
        public String type() {
            return "raw";
        }

        @Override
        public String name() {
            return "raw";
        }
    }

    private static final String STATIC_DOCUMENT_WITH_ADDITIONAL_OPERATION = """
            openapi: 3.0.3
            info:
              title: Static API
              version: 1.0.0
            paths:
              /static:
                additionalOperations:
                  COPY:
                    operationId: staticCopy
                    responses:
                      "200":
                        description: Static copy response.
            """;
}
