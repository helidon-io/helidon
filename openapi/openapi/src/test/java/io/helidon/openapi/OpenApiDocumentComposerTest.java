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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonString;
import io.helidon.json.schema.Schema;
import io.helidon.openapi.spi.OpenApiDocumentSource;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.v30.OpenApi30Version;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiDocumentComposerTest {
    private static final int BRANCHING_REFERENCE_DEPTH = 24;
    private static final int DEEP_REFERENCE_CHAIN_LENGTH = 1500;
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

    private static final String STATIC_PUBLIC_OPERATION_DOCUMENT = """
            openapi: 3.0.3
            info:
              title: Static API
              version: 1.0.0
            security:
              - staticAuth: []
            components:
              securitySchemes:
                staticAuth:
                  type: http
                  scheme: bearer
            paths:
              /public:
                get:
                  operationId: publicGet
                  security: []
                  responses:
                    "200":
                      description: Public response.
            """;

    private static final String STATIC_NULL_EXTENSION_DOCUMENT = """
            openapi: 3.0.3
            info:
              title: Static API
              version: 1.0.0
            paths: {}
            x-null: null
            """;

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

    private static final String STATIC_MERGE_DOCUMENT = """
            openapi: 3.0.3
            info:
              title: Static API
              version: 1.0.0
            tags:
              - name: static
                description: Static resources
            security:
              - staticAuth: []
            paths:
              /static:
                get:
                  operationId: staticGet
                  x-static-operation: preserved
                  responses:
                    "200":
                      description: Static response.
                      headers:
                        X-Static:
                          description: Static response header.
                          required: true
                          deprecated: true
                          allowEmptyValue: true
                          style: simple
                          explode: false
                          allowReserved: true
                          schema:
                            type: string
                          example: static-value
                        X-Static-Examples:
                          schema:
                            type: string
                          examples:
                            named:
                              value: named-static-value
            components:
              schemas:
                StaticItem:
                  type: object
              securitySchemes:
                staticAuth:
                  type: http
                  scheme: bearer
            """;

    private static final String STATIC_TEMPLATE_DOCUMENT = """
            openapi: 3.0.3
            info:
              title: Static API
              version: 1.0.0
            paths:
              /static/{id}:
                parameters:
                  - name: id
                    in: path
                    required: true
                    schema:
                      type: string
                get:
                  operationId: staticGet
                  responses:
                    "200":
                      description: Static response.
            """;

    @Test
    void generatedFallbackKeepsStaticDocumentWithoutParsingIt() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.STATIC_FIRST);
        String content = compose(context,
                                 new TestOpenApiVersion("3.0", "3.0.3", true),
                                 STATIC_DOCUMENT,
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source()));

        assertThat(content, is(STATIC_DOCUMENT));
    }

    @Test
    void generatedFallbackUsesGeneratedSourcesWithoutStaticDocument() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.STATIC_FIRST);
        String content = compose(context,
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
        String content = compose(context,
                                 context.openApiVersion(),
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source()));

        assertThat(content, is(""));
    }

    @Test
    void ignoreGeneratedKeepsStaticDocument() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.STATIC_ONLY);
        String content = compose(context,
                                 context.openApiVersion(),
                                 STATIC_DOCUMENT,
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source()));

        assertThat(content, is(STATIC_DOCUMENT));
    }

    @Test
    void generatedOnlyIgnoresStaticDocument() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        String content = compose(context,
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
    void generatedDocumentRequiresInfo() {
        for (OpenApiGeneratedMode mode : List.of(OpenApiGeneratedMode.STATIC_FIRST,
                                                 OpenApiGeneratedMode.MERGE,
                                                 OpenApiGeneratedMode.GENERATED_ONLY)) {
            OpenApiDocumentContext context = context(mode);
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> compose(context,
                                  context.openApiVersion(),
                                  "",
                                  MediaTypes.APPLICATION_OPENAPI_YAML,
                                  List.of(operationSource())),
                    mode.name());

            assertThat(thrown.getMessage(), containsString("requires Info metadata"));
        }
    }

    @Test
    void generatedEndpointUsesInfoFromSeparateSource() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource metadata = (ignored, document) -> document.info("Generated API", "1.0.0");

        String content = compose(context,
                                 context.openApiVersion(),
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(metadata, operationSource()));

        Map<String, Object> parsed = parse(content);
        assertThat(map(parsed, "info").get("title"), is("Generated API"));
        assertThat(map(parsed, "paths").containsKey("/generated"), is(true));
    }

    @Test
    void generatedOnlyFailsOnDuplicateOperationId() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> compose(
                                                            context,
                                                            context.openApiVersion(),
                                                            "",
                                                            MediaTypes.APPLICATION_OPENAPI_YAML,
                                                            List.of(operationSource("/first", "duplicate"),
                                                                    operationSource("/second", "duplicate"))));

        assertThat(thrown.getMessage(),
                   is("Duplicate OpenAPI operationId duplicate at paths./first.get and paths./second.get"));
    }

    @Test
    void generatedOnlyFailsOnDuplicateWebhookOperationId() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated", path -> path.operation("GET", responseOperation("duplicate")))
                .webhook("x-events", path -> path.operation("POST", responseOperation("duplicate")));

        assertDuplicateOperationId(
                source,
                "Duplicate OpenAPI operationId duplicate at paths./generated.get and webhooks.x-events.post");
    }

    @Test
    void exposesXPrefixedWebhook() {
        OpenApiDocument document = OpenApiDocument.builder()
                .webhook("x-events", path -> path.operation("POST", responseOperation("created")))
                .build();

        assertThat(document.webhooks().containsKey("x-events"), is(true));
    }

    @Test
    void generatedOnlyFailsOnDuplicateAdditionalOperationId() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated", path -> path.operation("GET", responseOperation("duplicate"))
                        .additionalOperation("SUBSCRIBE", responseOperation("duplicate")));

        assertDuplicateOperationId(
                source,
                "Duplicate OpenAPI operationId duplicate at paths./generated.get "
                        + "and paths./generated.additionalOperations.SUBSCRIBE");
    }

    @Test
    void generatedOnlyFailsOnDuplicateCallbackOperationId() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated",
                      path -> path.operation("GET",
                                             operation -> operation.operationId("duplicate")
                                                     .response("200", "OK")
                                                     .callback("onEvent",
                                                               callback -> callback.expression(
                                                                       "{$request.body#/callbackUrl}",
                                                                       pathItem -> pathItem.operation(
                                                                               "POST",
                                                                               responseOperation("duplicate"))))));

        assertDuplicateOperationId(
                source,
                "Duplicate OpenAPI operationId duplicate at paths./generated.get "
                        + "and paths./generated.get.callbacks.onEvent.{$request.body#/callbackUrl}.post");
    }

    @Test
    void generatedOnlyAcceptsDuplicateOperationIdInUnreferencedComponentPathItem() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated", path -> path.operation("GET", responseOperation("duplicate")))
                .components(components -> components.pathItem(
                        "Unused",
                        path -> path.operation("GET", responseOperation("duplicate"))));

        String content = compose(rawContext(OpenApiGeneratedMode.GENERATED_ONLY, RawOpenApiVersion.OPEN_API_31),
                                 RawOpenApiVersion.OPEN_API_31,
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source));

        assertThat(map(map(parse(content), "paths"), "/generated").containsKey("get"), is(true));
    }

    @Test
    void generatedOnlyAcceptsDuplicateOperationIdInUnreferencedComponentCallback() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated", path -> path.operation("GET", responseOperation("duplicate")))
                .components(components -> components.callback(
                        "Unused",
                        callback -> callback.expression(
                                "{$request.body#/callbackUrl}",
                                path -> path.operation("POST", responseOperation("duplicate")))));

        String content = compose(rawContext(OpenApiGeneratedMode.GENERATED_ONLY, RawOpenApiVersion.OPEN_API_31),
                                 RawOpenApiVersion.OPEN_API_31,
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source));

        assertThat(map(map(parse(content), "paths"), "/generated").containsKey("get"), is(true));
    }

    @Test
    void generatedOnlyFailsOnDuplicateReferencedComponentPathItemOperationId() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated", path -> path.operation("GET", responseOperation("duplicate")))
                .path("/referenced", path -> path.ref("#/components/pathItems/Reusable"))
                .components(components -> components.pathItem(
                        "Reusable",
                        path -> path.operation("GET", responseOperation("duplicate"))));

        assertDuplicateOperationId(
                source,
                RawOpenApiVersion.OPEN_API_31,
                "Duplicate OpenAPI operationId duplicate at paths./generated.get "
                        + "and paths./referenced.$ref.get");
    }

    @Test
    void generatedOnlyFailsOnDuplicateSelfReferencedComponentPathItemOperationId() {
        OpenApiDocumentSource source = (_, document) -> document.self("https://example.test/api")
                .info("Generated API", "1.0.0")
                .path("/generated", path -> path.operation("GET", responseOperation("duplicate")))
                .path("/referenced",
                      path -> path.ref("https://example.test/api#/components/pathItems/Reusable"))
                .components(components -> components.pathItem(
                        "Reusable",
                        path -> path.operation("GET", responseOperation("duplicate"))));

        assertDuplicateOperationId(
                source,
                RawOpenApiVersion.OPEN_API_32,
                "Duplicate OpenAPI operationId duplicate at paths./generated.get "
                        + "and paths./referenced.$ref.get");
    }

    @Test
    void generatedOnlyFailsOnDuplicateReferencedComponentCallbackOperationId() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated", path -> path.operation("GET", responseOperation("duplicate")))
                .path("/callbacks",
                      path -> path.operation("POST",
                                             operation -> operation.operationId("register")
                                                     .response("200", "OK")
                                                     .callback("Alias",
                                                               callback -> callback.ref(
                                                                       "#/components/callbacks/Reusable"))))
                .components(components -> components.callback(
                        "Reusable",
                        callback -> callback.expression(
                                "{$request.body#/callbackUrl}",
                                path -> path.operation("POST", responseOperation("duplicate")))));

        assertDuplicateOperationId(
                source,
                RawOpenApiVersion.OPEN_API_31,
                "Duplicate OpenAPI operationId duplicate at paths./generated.get "
                        + "and paths./callbacks.post.callbacks.Alias.$ref.{$request.body#/callbackUrl}.post");
    }

    @Test
    void generatedOnlyFailsOnDuplicateMultiplyReferencedComponentPathItemOperationId() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/first", path -> path.ref("#/components/pathItems/Reusable"))
                .path("/second", path -> path.ref("#/components/pathItems/Reusable"))
                .components(components -> components.pathItem(
                        "Reusable",
                        path -> path.operation("GET", responseOperation("duplicate"))));

        assertDuplicateOperationId(
                source,
                RawOpenApiVersion.OPEN_API_31,
                "Duplicate OpenAPI operationId duplicate at paths./first.$ref.get "
                        + "and paths./second.$ref.get");
    }

    @Test
    void generatedOnlyHandlesBranchingComponentReferenceDag() {
        OpenApiDocumentSource source = (_, document) -> {
            document.info("Generated API", "1.0.0")
                    .path("/dag",
                          path -> path.operation("GET",
                                                 operation -> operation.response("200", "OK")
                                                         .callback("left",
                                                                   callback -> callback.ref(callbackReference(0)))
                                                         .callback("right",
                                                                   callback -> callback.ref(callbackReference(0)))));
            document.components(components -> {
                // This bounded graph has only 24 named callback components but more than 16 million root-to-leaf paths.
                for (int i = 0; i < BRANCHING_REFERENCE_DEPTH; i++) {
                    int level = i;
                    components.callback(callbackName(level),
                                        callback -> callback.expression(
                                                "{$request.body#/callbackUrl/" + level + "}",
                                                path -> path.operation("POST", operation -> {
                                                    operation.response("200", "OK");
                                                    if (level + 1 < BRANCHING_REFERENCE_DEPTH) {
                                                        operation.callback("left",
                                                                           next -> next.ref(
                                                                                   callbackReference(level + 1)));
                                                        operation.callback("right",
                                                                           next -> next.ref(
                                                                                   callbackReference(level + 1)));
                                                    }
                                                })));
                }
            });
        };

        String content = compose(rawContext(OpenApiGeneratedMode.GENERATED_ONLY, RawOpenApiVersion.OPEN_API_31),
                                 RawOpenApiVersion.OPEN_API_31,
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source));

        assertThat(map(parse(content), "paths").containsKey("/dag"), is(true));
    }

    @Test
    void generatedOnlyHandlesDeepComponentReferenceChain() {
        OpenApiDocumentSource source = (_, document) -> {
            document.info("Generated API", "1.0.0")
                    .path("/deep", path -> path.ref(pathItemReference(0)));
            document.components(components -> {
                // Each bounded level adds both a Path Item and Callback reference to the old recursive call chain.
                for (int i = 0; i < DEEP_REFERENCE_CHAIN_LENGTH; i++) {
                    int level = i;
                    components.pathItem(pathItemName(level),
                                        path -> path.operation("GET", operation -> {
                                            operation.response("200", "OK");
                                            if (level + 1 < DEEP_REFERENCE_CHAIN_LENGTH) {
                                                operation.callback(
                                                        "next",
                                                        callback -> callback.ref(callbackReference(level)));
                                            }
                                        }));
                    if (level + 1 < DEEP_REFERENCE_CHAIN_LENGTH) {
                        components.callback(callbackName(level),
                                            callback -> callback.expression(
                                                    "{$request.body#/callbackUrl/" + level + "}",
                                                    path -> path.ref(pathItemReference(level + 1))));
                    }
                }
            });
        };

        String content = compose(rawContext(OpenApiGeneratedMode.GENERATED_ONLY, RawOpenApiVersion.OPEN_API_31),
                                 RawOpenApiVersion.OPEN_API_31,
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source));

        assertThat(content, containsString("\"/deep\""));
    }

    @Test
    void generatedOnlyValidatesOperationsOnIntermediateReferencedPathItems() {
        OpenApiDocumentSource source = (_, document) -> document.info("Generated API", "1.0.0")
                .path("/generated", path -> path.operation("GET", responseOperation("duplicate")))
                .path("/referenced", path -> path.ref("#/components/pathItems/Intermediate"))
                .components(components -> components
                        .pathItem("Intermediate",
                                  path -> path.ref("#/components/pathItems/Terminal")
                                          .operation("POST", responseOperation("duplicate")))
                        .pathItem("Terminal",
                                  path -> path.operation("GET", responseOperation("terminal"))));

        assertDuplicateOperationId(
                source,
                RawOpenApiVersion.OPEN_API_31,
                "Duplicate OpenAPI operationId duplicate at paths./generated.get "
                        + "and paths./referenced.$ref.post");
    }

    @Test
    void generatedOnlyHandlesComponentOperationReferenceCycle() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/cycle", path -> path.ref("#/components/pathItems/Cycle"))
                .components(components -> components
                        .pathItem("Cycle",
                                  path -> path.operation(
                                          "GET",
                                          operation -> operation.operationId("cycle")
                                                  .response("200", "OK")
                                                  .callback("loop",
                                                            callback -> callback.ref(
                                                                    "#/components/callbacks/Cycle"))))
                        .callback("Cycle",
                                  callback -> callback.expression(
                                          "{$request.body#/callbackUrl}",
                                          path -> path.ref("#/components/pathItems/Cycle"))));

        String content = compose(rawContext(OpenApiGeneratedMode.GENERATED_ONLY, RawOpenApiVersion.OPEN_API_31),
                                 RawOpenApiVersion.OPEN_API_31,
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source));

        assertThat(map(parse(content), "paths").containsKey("/cycle"), is(true));
    }

    @Test
    void generatedOnlyIgnoresExternalPathItemReferenceOperationIds() {
        OpenApiDocumentSource source = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/external", path -> path.ref("https://example.test/openapi.yaml#/paths/~1external"));

        String content = compose(rawContext(OpenApiGeneratedMode.GENERATED_ONLY, RawOpenApiVersion.OPEN_API_31),
                                 RawOpenApiVersion.OPEN_API_31,
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source));

        assertThat(map(parse(content), "paths").containsKey("/external"), is(true));
    }

    @Test
    void mergeAcceptsParentLinkAcrossStaticAndGeneratedTags() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.MERGE,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocument staticDocument = OpenApiDocument.builder()
                .info("Static API", "1.0.0")
                .tag(tag -> tag.name("static-child").parent("generated-parent"))
                .build();
        OpenApiDocumentSource generated = (ignored, document) -> document
                .tag(tag -> tag.name("generated-parent"));

        String content = OpenApiDocumentComposer.compose(context,
                                                         Optional.of(() -> staticDocument),
                                                         "static",
                                                         List.of(generated));

        List<Object> tags = list(parse(content), "tags");
        assertThat(((Map<?, ?>) tags.get(0)).get("parent"), is("generated-parent"));
        assertThat(((Map<?, ?>) tags.get(1)).get("name"), is("generated-parent"));
    }

    @Test
    void generatedOnlyRejectsMissingTagParent() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocumentSource source = (ignored, document) -> document
                .info("Generated API", "1.0.0")
                .tag(tag -> tag.name("child").parent("missing"));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> compose(context,
                              RawOpenApiVersion.OPEN_API_32,
                              "",
                              MediaTypes.APPLICATION_OPENAPI_YAML,
                              List.of(source)));

        assertThat(thrown.getMessage(), is("OpenAPI tag child references missing parent tag missing"));
    }

    @Test
    void generatedOnlyOmitsMissingTagParentForOpenApi30() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource source = (ignored, document) -> document
                .info("Generated API", "1.0.0")
                .paths(Map.of())
                .tag(tag -> tag.name("child").parent("missing"));

        String content = compose(context,
                                 context.openApiVersion(),
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source));

        Map<?, ?> childTag = (Map<?, ?>) list(parse(content), "tags").get(0);
        assertThat(childTag.get("name"), is("child"));
        assertThat(childTag.containsKey("parent"), is(false));
    }

    @Test
    void generatedOnlyOmitsMissingTagParentForOpenApi31Abstraction() {
        OpenApiVersion version = new TestOpenApiVersion("3.1", "3.1.1", false);
        OpenApiDocumentContext context = new OpenApiDocumentContextImpl("openapi",
                                                                        "/openapi",
                                                                        "default",
                                                                        OpenApiGeneratedMode.GENERATED_ONLY,
                                                                        version);
        OpenApiDocumentSource source = (ignored, document) -> document
                .info("Generated API", "1.0.0")
                .paths(Map.of())
                .tag(tag -> tag.name("child").parent("missing"));

        String content = compose(context,
                                 version,
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source));

        Map<?, ?> childTag = (Map<?, ?>) list(parse(content), "tags").get(0);
        assertThat(childTag.get("name"), is("child"));
        assertThat(childTag.containsKey("parent"), is(false));
    }

    @Test
    void generatedOnlyRejectsSelfParentingTag() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocumentSource source = (ignored, document) -> document
                .info("Generated API", "1.0.0")
                .tag(tag -> tag.name("child").parent("child"));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> compose(context,
                              RawOpenApiVersion.OPEN_API_32,
                              "",
                              MediaTypes.APPLICATION_OPENAPI_YAML,
                              List.of(source)));

        assertThat(thrown.getMessage(), is("OpenAPI tag child cannot be its own parent"));
    }

    @Test
    void generatedOnlyAcceptsLongTagParentChain() {
        int tagCount = 10_000;
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocumentSource source = (ignored, document) -> {
            document.info("Generated API", "1.0.0")
                    .tag(tag -> tag.name("tag-0"));
            for (int i = 1; i < tagCount; i++) {
                String tagName = "tag-" + i;
                String parentName = "tag-" + (i - 1);
                document.tag(tag -> tag.name(tagName).parent(parentName));
            }
        };

        String content = compose(context,
                                 RawOpenApiVersion.OPEN_API_32,
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(source));

        List<Object> tags = list(parse(content), "tags");
        assertThat(tags.size(), is(tagCount));
        assertThat(((Map<?, ?>) tags.get(tagCount - 1)).get("parent"), is("tag-" + (tagCount - 2)));
    }

    @Test
    void mergeRejectsTagParentCycleAcrossStaticAndGeneratedTags() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.MERGE,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocument staticDocument = OpenApiDocument.builder()
                .info("Static API", "1.0.0")
                .tag(tag -> tag.name("static-tag").parent("generated-tag"))
                .build();
        OpenApiDocumentSource generated = (ignored, document) -> document
                .tag(tag -> tag.name("generated-tag").parent("static-tag"));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> OpenApiDocumentComposer.compose(context,
                                                      Optional.of(() -> staticDocument),
                                                      "static",
                                                      List.of(generated)));

        assertThat(thrown.getMessage(),
                   is("OpenAPI tag parent cycle: static-tag -> generated-tag -> static-tag"));
    }

    @Test
    void generatedOperationIdOverrideResolvesDuplicate() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY,
                                                 Map.of("com.example.First#get()", "firstGet"));
        OpenApiDocumentSource first = (documentContext, document) -> document.info("Generated API", "1.0.0")
                .path("/first",
                      path -> path.operation("GET",
                                             operation -> operation
                                                     .operationId(OpenApiDocumentContextSupport.operationId(
                                                             documentContext,
                                                             "com.example.First#get()",
                                                             "duplicate"))
                                                     .response("200", "OK")));

        String content = compose(context,
                                 context.openApiVersion(),
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(first, operationSource("/second", "duplicate")));

        Map<String, Object> paths = map(parse(content), "paths");
        assertThat(map(map(paths, "/first"), "get").get("operationId"), is("firstGet"));
        assertThat(map(map(paths, "/second"), "get").get("operationId"), is("duplicate"));
    }

    @Test
    void mergeStaticKeepsStaticAndGeneratedDocumentSections() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);

        String content = compose(context,
                                 context.openApiVersion(),
                                 STATIC_MERGE_DOCUMENT,
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(mergeSource()));

        Map<String, Object> parsed = parse(content);
        Map<String, Object> paths = map(parsed, "paths");
        assertThat(paths.containsKey("/static"), is(true));
        assertThat(paths.containsKey("/generated"), is(true));

        Map<String, Object> components = map(parsed, "components");
        assertThat(map(components, "schemas").containsKey("StaticItem"), is(true));
        assertThat(map(components, "schemas").containsKey("GeneratedItem"), is(true));
        assertThat(map(components, "securitySchemes").containsKey("staticAuth"), is(true));
        assertThat(map(components, "securitySchemes").containsKey("generatedAuth"), is(true));

        Map<String, Object> operation = map(map(paths, "/static"), "get");
        assertThat(operation.get("x-static-operation"), is("preserved"));

        Map<String, Object> response = map(map(operation, "responses"), "200");
        Map<String, Object> staticHeader = map(map(response, "headers"), "X-Static");
        assertThat(staticHeader.get("description"), is("Static response header."));
        assertThat(staticHeader.get("required"), is(true));
        assertThat(staticHeader.get("deprecated"), is(true));
        assertThat(staticHeader.containsKey("allowEmptyValue"), is(false));
        assertThat(staticHeader.get("style"), is("simple"));
        assertThat(staticHeader.get("explode"), is(false));
        assertThat(staticHeader.containsKey("allowReserved"), is(false));
        assertThat(map(staticHeader, "schema").get("type"), is("string"));
        assertThat(staticHeader.get("example"), is("static-value"));
        Map<String, Object> staticExamplesHeader = map(map(response, "headers"), "X-Static-Examples");
        assertThat(map(staticExamplesHeader, "schema").get("type"), is("string"));
        assertThat(map(map(staticExamplesHeader, "examples"), "named").get("value"), is("named-static-value"));

        assertThat(((Map<?, ?>) list(parsed, "tags").get(0)).get("name"), is("static"));
        assertThat(((Map<?, ?>) list(parsed, "tags").get(1)).get("name"), is("generated"));
        assertThat(((Map<?, ?>) list(parsed, "security").get(0)).get("staticAuth"), is(List.of()));
        assertThat(((Map<?, ?>) list(parsed, "security").get(1)).get("generatedAuth"), is(List.of("generated:read")));
    }

    @Test
    void generatedDocumentReusesEquivalentSchemasAcrossSources() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "First",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components.schema(
                        "Second",
                        JsonObject.builder().set("type", "string").build()))
                .path("/second",
                      path -> path.operation(
                              "GET",
                              operation -> operation.operationId("secondGet")
                                      .response("200",
                                                response -> response.description("OK")
                                                        .content(MediaTypes.APPLICATION_JSON_VALUE,
                                                                 media -> media.schema(JsonObject.builder()
                                                                                                .set("$ref",
                                                                                                     "#/components/schemas/Second")
                                                                                                .build())))));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");
        Map<String, Object> operation = map(map(map(document, "paths"), "/second"), "get");
        Map<String, Object> response = map(map(operation, "responses"), "200");
        Map<String, Object> content = map(map(response, "content"), MediaTypes.APPLICATION_JSON_VALUE);

        assertThat(schemas.size(), is(1));
        assertThat(schemas.containsKey("First"), is(true));
        assertThat(map(content, "schema").get("$ref"), is("#/components/schemas/First"));
    }

    @Test
    void generatedDocumentKeepsCollisionFreeSchemasAcrossSources() {
        int sourceCount = 64;
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        List<OpenApiDocumentSource> sources = new ArrayList<>(sourceCount);
        for (int i = 0; i < sourceCount; i++) {
            boolean first = i == 0;
            String schemaName = "Schema" + i;
            String description = "Schema " + i;
            sources.add((_, document) -> {
                if (first) {
                    document.info("Generated API", "1.0.0")
                            .paths(Map.of());
                }
                document.components(components -> components.schema(
                        schemaName,
                        JsonObject.builder()
                                .set("type", "string")
                                .set("description", description)
                                .build()));
            });
        }

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                sources));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");

        assertThat(schemas.size(), is(sourceCount));
        assertThat(schemas.containsKey("Schema0"), is(true));
        assertThat(schemas.containsKey("Schema63"), is(true));
    }

    @Test
    void generatedDocumentReusesEquivalentSchemasAfterReferenceRewriting() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components
                        .schema("First", JsonObject.builder().set("type", "string").build())
                        .schema("Envelope",
                                JsonObject.builder()
                                        .set("type", "object")
                                        .set("properties",
                                             JsonObject.builder()
                                                     .set("value",
                                                          JsonObject.builder()
                                                                  .set("$ref", "#/components/schemas/First")
                                                                  .build())
                                                     .build())
                                        .build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Second", JsonObject.builder().set("type", "string").build())
                        .schema("SecondEnvelope",
                                JsonObject.builder()
                                        .set("type", "object")
                                        .set("properties",
                                             JsonObject.builder()
                                                     .set("value",
                                                          JsonObject.builder()
                                                                  .set("$ref", "#/components/schemas/Second")
                                                                  .build())
                                                     .build())
                                        .build()))
                .path("/second",
                      path -> path.operation(
                              "GET",
                              operation -> operation.operationId("secondGet")
                                      .response("200",
                                                response -> response.description("OK")
                                                        .content(MediaTypes.APPLICATION_JSON_VALUE,
                                                                 media -> media.schema(JsonObject.builder()
                                                                                                .set("$ref",
                                                                                                     "#/components/schemas/SecondEnvelope")
                                                                                                .build())))));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");
        Map<String, Object> operation = map(map(map(document, "paths"), "/second"), "get");
        Map<String, Object> response = map(map(operation, "responses"), "200");
        Map<String, Object> content = map(map(response, "content"), MediaTypes.APPLICATION_JSON_VALUE);

        assertThat(schemas.size(), is(2));
        assertThat(schemas.containsKey("First"), is(true));
        assertThat(schemas.containsKey("Envelope"), is(true));
        assertThat(map(content, "schema").get("$ref"), is("#/components/schemas/Envelope"));
    }

    @Test
    void generatedDocumentReusesLongEquivalentSchemaChains() {
        int schemaDepth = 128;
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource first = (_, document) -> {
            document.info("Generated API", "1.0.0");
            document.components(components -> {
                components.schema("First0", JsonObject.builder().set("type", "string").build());
                for (int i = 1; i <= schemaDepth; i++) {
                    components.schema("First" + i,
                                      JsonObject.builder()
                                              .set("$ref", "#/components/schemas/First" + (i - 1))
                                              .build());
                }
            });
        };
        OpenApiDocumentSource second = (_, document) -> {
            document.components(components -> {
                components.schema("Second0", JsonObject.builder().set("type", "string").build());
                for (int i = 1; i <= schemaDepth; i++) {
                    components.schema("Second" + i,
                                      JsonObject.builder()
                                              .set("$ref", "#/components/schemas/Second" + (i - 1))
                                              .build());
                }
            });
            document.path("/second",
                          path -> path.operation(
                                  "GET",
                                  operation -> operation.operationId("secondGet")
                                          .response("200",
                                                    response -> response.description("OK")
                                                            .content(MediaTypes.APPLICATION_JSON_VALUE,
                                                                     media -> media.schema(JsonObject.builder()
                                                                                                    .set("$ref",
                                                                                                         "#/components/schemas/Second"
                                                                                                                 + schemaDepth)
                                                                                                    .build())))));
        };

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");
        Map<String, Object> operation = map(map(map(document, "paths"), "/second"), "get");
        Map<String, Object> response = map(map(operation, "responses"), "200");
        Map<String, Object> content = map(map(response, "content"), MediaTypes.APPLICATION_JSON_VALUE);

        assertThat(schemas.size(), is(schemaDepth + 1));
        assertThat(schemas.containsKey("Second" + schemaDepth), is(false));
        assertThat(map(content, "schema").get("$ref"), is("#/components/schemas/First" + schemaDepth));
    }

    @Test
    void generatedDocumentRewritesRenamedSchemaAliasesAfterReuse() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components
                        .schema("Base", JsonObject.builder().set("type", "string").build())
                        .schema("B", JsonObject.builder().set("type", "integer").build())
                        .schema("Canonical",
                                JsonObject.builder()
                                        .set("$ref", "#/components/schemas/Base")
                                        .build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("EquivalentBase", JsonObject.builder().set("type", "string").build())
                        .schema("B",
                                JsonObject.builder()
                                        .set("$ref", "#/components/schemas/EquivalentBase")
                                        .build()))
                .path("/second",
                      path -> path.operation(
                              "GET",
                              operation -> operation.operationId("secondGet")
                                      .response("200",
                                                response -> response.description("OK")
                                                        .content(MediaTypes.APPLICATION_JSON_VALUE,
                                                                 media -> media.schema(JsonObject.builder()
                                                                                                .set("$ref",
                                                                                                     "#/components/schemas/B")
                                                                                                .build())))));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");
        Map<String, Object> operation = map(map(map(document, "paths"), "/second"), "get");
        Map<String, Object> response = map(map(operation, "responses"), "200");
        Map<String, Object> content = map(map(response, "content"), MediaTypes.APPLICATION_JSON_VALUE);

        assertThat(schemas.size(), is(3));
        assertThat(schemas.containsKey("B2"), is(false));
        assertThat(map(content, "schema").get("$ref"), is("#/components/schemas/Canonical"));
    }

    @Test
    void generatedDocumentDoesNotRewriteRenamedSchemasTwice() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components
                        .schema("B", JsonObject.builder().set("type", "string").build())
                        .schema("Wrapper",
                                JsonObject.builder()
                                        .set("type", "object")
                                        .set("properties",
                                             JsonObject.builder()
                                                     .set("value",
                                                          JsonObject.builder()
                                                                  .set("$ref", "#/components/schemas/B")
                                                                  .build())
                                                     .build())
                                        .build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("A", JsonObject.builder().set("type", "string").build())
                        .schema("B", JsonObject.builder().set("type", "integer").build())
                        .schema("Wrapper2",
                                JsonObject.builder()
                                        .set("type", "object")
                                        .set("properties",
                                             JsonObject.builder()
                                                     .set("value",
                                                          JsonObject.builder()
                                                                  .set("$ref", "#/components/schemas/A")
                                                                  .build())
                                                     .build())
                                        .build())
                        .schema("Choice",
                                JsonObject.builder()
                                        .setValues("oneOf",
                                                   List.of(JsonObject.builder()
                                                                   .set("$ref", "#/components/schemas/A")
                                                                   .build()))
                                        .set("discriminator",
                                             JsonObject.builder()
                                                     .set("propertyName", "kind")
                                                     .set("mapping",
                                                          JsonObject.builder()
                                                                  .set("selected", "A")
                                                                  .build())
                                                     .build())
                                        .build()))
                .path("/second",
                      path -> path.operation(
                              "GET",
                              operation -> operation.operationId("secondGet")
                                      .response("200",
                                                response -> response.description("OK")
                                                        .content(MediaTypes.APPLICATION_JSON_VALUE,
                                                                 media -> media.schema(JsonObject.builder()
                                                                                                .set("$ref",
                                                                                                     "#/components/schemas/Wrapper2")
                                                                                                .build())))));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");
        Map<String, Object> operation = map(map(map(document, "paths"), "/second"), "get");
        Map<String, Object> response = map(map(operation, "responses"), "200");
        Map<String, Object> content = map(map(response, "content"), MediaTypes.APPLICATION_JSON_VALUE);
        Map<String, Object> choice = map(schemas, "Choice");
        Map<String, Object> mapping = map(map(choice, "discriminator"), "mapping");

        assertThat(schemas.size(), is(4));
        assertThat(map(schemas, "B").get("type"), is("string"));
        assertThat(map(schemas, "B2").get("type"), is("integer"));
        assertThat(schemas.containsKey("Wrapper2"), is(false));
        assertThat(((Map<?, ?>) list(choice, "oneOf").getFirst()).get("$ref"), is("#/components/schemas/B"));
        assertThat(mapping.get("selected"), is("B"));
        assertThat(map(content, "schema").get("$ref"), is("#/components/schemas/Wrapper"));
    }

    @Test
    void generatedDocumentRenamesSelfQualifiedSchemaReferences() {
        String self = "https://example.test/api";
        String absoluteItemRef = self + "#/components/schemas/Item";
        String relativeItemRef = "/api#/components/schemas/Item";
        String externalItemRef = "https://example.test/other#/components/schemas/Item";
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocumentSource first = (_, document) -> document
                .self(self)
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .self(self)
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .schema("AbsoluteEnvelope", JsonObject.builder().set("$ref", absoluteItemRef).build())
                        .schema("RelativeEnvelope", JsonObject.builder().set("$ref", relativeItemRef).build())
                        .schema("ExternalEnvelope", JsonObject.builder().set("$ref", externalItemRef).build())
                        .schema("EmbeddedEnvelope",
                                JsonObject.builder()
                                        .set("$id", "https://schemas.example/embedded.json")
                                        .set("properties",
                                             JsonObject.builder()
                                                     .set("qualified",
                                                          JsonObject.builder().set("$ref", absoluteItemRef).build())
                                                     .set("local",
                                                          JsonObject.builder()
                                                                  .set("$ref", "#/components/schemas/Item")
                                                                  .build())
                                                     .set("relative",
                                                          JsonObject.builder()
                                                                  .set("$ref", "api#/components/schemas/Item")
                                                                  .build())
                                                     .set("originRelative",
                                                          JsonObject.builder()
                                                                  .set("$ref", "/api#/components/schemas/Item")
                                                                  .build())
                                                     .set("qualifiedDynamic",
                                                          JsonObject.builder().set("$dynamicRef", absoluteItemRef).build())
                                                     .set("localDynamic",
                                                          JsonObject.builder()
                                                                  .set("$dynamicRef", "#/components/schemas/Item")
                                                                  .build())
                                                     .set("originRelativeDynamic",
                                                          JsonObject.builder()
                                                                  .set("$dynamicRef", "/api#/components/schemas/Item")
                                                                  .build())
                                                     .build())
                                        .set("discriminator",
                                             JsonObject.builder()
                                                     .set("propertyName", "kind")
                                                     .set("mapping",
                                                          JsonObject.builder()
                                                                  .set("qualified", absoluteItemRef)
                                                                  .set("local", "#/components/schemas/Item")
                                                                  .set("originRelative",
                                                                       "/api#/components/schemas/Item")
                                                                  .set("byName", "Item")
                                                                  .build())
                                                     .set("defaultMapping", absoluteItemRef)
                                                     .build())
                                        .build()));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");

        assertThat(map(schemas, "Item").get("type"), is("string"));
        assertThat(map(schemas, "Item2").get("type"), is("integer"));
        assertThat(map(schemas, "AbsoluteEnvelope").get("$ref"),
                   is(self + "#/components/schemas/Item2"));
        assertThat(map(schemas, "RelativeEnvelope").get("$ref"),
                   is("/api#/components/schemas/Item2"));
        assertThat(map(schemas, "ExternalEnvelope").get("$ref"), is(externalItemRef));
        Map<String, Object> embeddedEnvelope = map(schemas, "EmbeddedEnvelope");
        Map<String, Object> embeddedProperties = map(embeddedEnvelope, "properties");
        assertThat(map(embeddedProperties, "qualified").get("$ref"),
                   is(self + "#/components/schemas/Item2"));
        assertThat(map(embeddedProperties, "local").get("$ref"), is("#/components/schemas/Item"));
        assertThat(map(embeddedProperties, "relative").get("$ref"), is("api#/components/schemas/Item"));
        assertThat(map(embeddedProperties, "originRelative").get("$ref"),
                   is("/api#/components/schemas/Item"));
        assertThat(map(embeddedProperties, "qualifiedDynamic").get("$dynamicRef"),
                   is(self + "#/components/schemas/Item2"));
        assertThat(map(embeddedProperties, "localDynamic").get("$dynamicRef"),
                   is("#/components/schemas/Item"));
        assertThat(map(embeddedProperties, "originRelativeDynamic").get("$dynamicRef"),
                   is("/api#/components/schemas/Item"));
        Map<String, Object> discriminator = map(embeddedEnvelope, "discriminator");
        Map<String, Object> mapping = map(discriminator, "mapping");
        assertThat(mapping.get("qualified"), is(self + "#/components/schemas/Item2"));
        assertThat(mapping.get("local"), is("#/components/schemas/Item"));
        assertThat(mapping.get("originRelative"), is("/api#/components/schemas/Item"));
        assertThat(mapping.get("byName"), is("Item2"));
        assertThat(discriminator.get("defaultMapping"), is(self + "#/components/schemas/Item2"));
    }

    @Test
    void generatedDocumentUsesComposedSelfForQualifiedSchemaReferences() {
        String self = "https://example.test/api";
        String itemRef = self + "#/components/schemas/Item";
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocumentSource first = (_, document) -> document
                .self(self)
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .schema("Envelope", JsonObject.builder().set("$ref", itemRef).build()));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");

        assertThat(map(schemas, "Item").get("type"), is("string"));
        assertThat(map(schemas, "Item2").get("type"), is("integer"));
        assertThat(map(schemas, "Envelope").get("$ref"), is(self + "#/components/schemas/Item2"));
    }

    @Test
    void generatedDocumentResolvesRelativeSelfAgainstWebContext() {
        String relativeItemRef = "openapi#/components/schemas/Item";
        String originRelativeItemRef = "/openapi#/components/schemas/Item";
        String absoluteExternalItemRef = "https://example.test/openapi#/components/schemas/Item";
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocumentSource first = (_, document) -> document
                .self("openapi")
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .schema("RelativeEnvelope",
                                JsonObject.builder().set("$ref", relativeItemRef).build())
                        .schema("OriginRelativeEnvelope",
                                JsonObject.builder().set("$ref", originRelativeItemRef).build())
                        .schema("AbsoluteExternalEnvelope",
                                JsonObject.builder().set("$ref", absoluteExternalItemRef).build()));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");

        assertThat(map(schemas, "Item").get("type"), is("string"));
        assertThat(map(schemas, "Item2").get("type"), is("integer"));
        assertThat(map(schemas, "RelativeEnvelope").get("$ref"),
                   is("openapi#/components/schemas/Item2"));
        assertThat(map(schemas, "OriginRelativeEnvelope").get("$ref"),
                   is("/openapi#/components/schemas/Item2"));
        assertThat(map(schemas, "AbsoluteExternalEnvelope").get("$ref"), is(absoluteExternalItemRef));
    }

    @Test
    void generatedDocumentResolvesEmptyPathSelfAgainstDocumentBase() {
        String documentBase = "/api-description";
        String itemRef = documentBase + "#/components/schemas/Item";
        OpenApiDocumentContext context = new OpenApiDocumentContextImpl("openapi",
                                                                        documentBase,
                                                                        "default",
                                                                        OpenApiGeneratedMode.GENERATED_ONLY,
                                                                        RawOpenApiVersion.OPEN_API_32);

        for (String self : List.of("", "#source", documentBase)) {
            Map<String, Object> schemas = collidingSchemas(context, self, Map.of("Envelope", itemRef));

            assertThat(self, map(schemas, "Item").get("type"), is("string"));
            assertThat(self, map(schemas, "Item2").get("type"), is("integer"));
            assertThat(self, map(schemas, "Envelope").get("$ref"),
                       is(documentBase + "#/components/schemas/Item2"));
        }

        String querySelf = "?revision=2";
        Map<String, Object> querySchemas = collidingSchemas(
                context,
                querySelf,
                Map.of("QualifiedEnvelope", documentBase + querySelf + "#/components/schemas/Item",
                       "UnqualifiedEnvelope", itemRef));

        assertThat(map(querySchemas, "QualifiedEnvelope").get("$ref"),
                   is(documentBase + querySelf + "#/components/schemas/Item2"));
        assertThat(map(querySchemas, "UnqualifiedEnvelope").get("$ref"), is(itemRef));
    }

    @Test
    void generatedDocumentRenamesCollidingSchemasAndReferences() {
        String nestedItemRef = "#/components/schemas/Item/properties/a~1b/$defs/m~0n";
        String renamedNestedItemRef = "#/components/schemas/Item2/properties/a~1b/$defs/m~0n";
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        JsonObject literalDiscriminator = JsonObject.builder()
                .set("$ref", "#/components/schemas/Item")
                .set("$dynamicRef", "#/components/schemas/Item")
                .setValues("oneOf",
                           List.of(JsonObject.builder()
                                           .set("$ref", "#/components/schemas/Item")
                                           .build()))
                .set("discriminator",
                     JsonObject.builder()
                             .set("propertyName", "kind")
                             .set("mapping", JsonObject.builder().set("literal", "Item").build())
                             .set("defaultMapping", "Item")
                             .build())
                .build();
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .schema("Envelope",
                                JsonObject.builder()
                                        .set("type", "object")
                                        .setValues("oneOf",
                                                   List.of(JsonObject.builder()
                                                                   .set("$ref", "#/components/schemas/Item")
                                                                   .build()))
                                        .set("properties",
                                             JsonObject.builder()
                                                     .set("item",
                                                          JsonObject.builder()
                                                                  .set("$ref", "#/components/schemas/Item")
                                                                  .build())
                                                     .set("encodedItem",
                                                          JsonObject.builder()
                                                                  .set("$ref", "#/%63omponents/schemas/%49tem")
                                                                  .build())
                                                     .set("nestedItem",
                                                          JsonObject.builder()
                                                                  .set("$ref", nestedItemRef)
                                                                  .build())
                                                     .set("dynamicItem",
                                                          JsonObject.builder()
                                                                  .set("$dynamicRef", "#/components/schemas/Item")
                                                                  .build())
                                                     .set("encodedDynamicItem",
                                                          JsonObject.builder()
                                                                  .set("$dynamicRef", "#/%63omponents/schemas/%49tem")
                                                                  .build())
                                                     .set("dynamicNestedItem",
                                                          JsonObject.builder()
                                                                  .set("$dynamicRef", nestedItemRef)
                                                                  .build())
                                                     .set("dynamicAnchor",
                                                          JsonObject.builder()
                                                                  .set("$dynamicRef", "#item")
                                                                  .build())
                                                     .set("dynamicExternal",
                                                          JsonObject.builder()
                                                                  .set("$dynamicRef", "https://example.com/schemas/Item")
                                                                  .build())
                                                     .set("embeddedResource",
                                                          JsonObject.builder()
                                                                  .set("$id", "embedded.json")
                                                                  .set("$ref", nestedItemRef)
                                                                  .set("$dynamicRef", "#/components/schemas/Item")
                                                                  .set("properties",
                                                                       JsonObject.builder()
                                                                               .set("nested",
                                                                                    JsonObject.builder()
                                                                                            .set("$dynamicRef",
                                                                                                 "#/components/schemas/Item")
                                                                                            .build())
                                                                               .set("nestedRef",
                                                                                    JsonObject.builder()
                                                                                            .set("$ref", nestedItemRef)
                                                                                            .build())
                                                                               .build())
                                                                  .set("discriminator",
                                                                       JsonObject.builder()
                                                                               .set("propertyName", "kind")
                                                                               .set("mapping",
                                                                                    JsonObject.builder()
                                                                                            .set("byRef",
                                                                                                 nestedItemRef)
                                                                                            .set("byName", "Item")
                                                                                            .build())
                                                                               .set("defaultMapping",
                                                                                    nestedItemRef)
                                                                               .build())
                                                                  .build())
                                                     .set("example",
                                                          JsonObject.builder()
                                                                  .setValues("oneOf",
                                                                             List.of(JsonObject.builder()
                                                                                             .set("$ref",
                                                                                                  "#/components/schemas/Item")
                                                                                             .build()))
                                                                  .set("discriminator",
                                                                       JsonObject.builder()
                                                                               .set("propertyName", "kind")
                                                                               .set("defaultMapping", "Item")
                                                                               .build())
                                                                  .build())
                                                     .set("external",
                                                          JsonObject.builder()
                                                                  .setValues("oneOf",
                                                                             List.of(JsonObject.builder()
                                                                                             .set("$ref",
                                                                                                  "#/components/schemas/Item")
                                                                                             .build()))
                                                                  .set("discriminator",
                                                                       JsonObject.builder()
                                                                               .set("propertyName", "kind")
                                                                               .set("defaultMapping",
                                                                                    "https://example.com/schemas/Item")
                                                                               .build())
                                                                  .build())
                                                     .build())
                                        .set("discriminator",
                                             JsonObject.builder()
                                                     .set("propertyName", "kind")
                                                     .set("mapping",
                                                          JsonObject.builder()
                                                                  .set("second", "#/components/schemas/Item")
                                                                  .set("encodedSecond", "#/%63omponents/schemas/%49tem")
                                                                  .set("secondByName", "Item")
                                                                  .set("external",
                                                                       "https://example.com/schemas/Item")
                                                                  .build())
                                                     .set("defaultMapping", "#/%63omponents/schemas/%49tem")
                                                     .build())
                                        .set("example", literalDiscriminator)
                                        .set("default", literalDiscriminator)
                                        .set("x-payload", literalDiscriminator)
                                        .build())
                        .schema("CustomDialect",
                                JsonObject.builder()
                                        .set("$schema", "https://example.com/dialect")
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build())
                        .schema("Draft2020Dialect",
                                JsonObject.builder()
                                        .set("$schema", "https://json-schema.org/draft/2020-12/schema")
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build())
                        .schema("OasDialect",
                                JsonObject.builder()
                                        .set("$schema", "https://spec.openapis.org/oas/3.1/dialect/base")
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build()))
                .path("/second",
                      path -> path.operation(
                              "GET",
                              operation -> operation.operationId("secondGet")
                                      .response("200",
                                                response -> response.description("OK")
                                                        .content(MediaTypes.APPLICATION_JSON_VALUE,
                                                                 media -> media.schema(JsonObject.builder()
                                                                                                .set("$ref",
                                                                                                     "#/components/schemas/Envelope")
                                                                                                .build())))));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");
        Map<String, Object> operation = map(map(map(document, "paths"), "/second"), "get");
        Map<String, Object> response = map(map(operation, "responses"), "200");
        Map<String, Object> content = map(map(response, "content"), MediaTypes.APPLICATION_JSON_VALUE);
        Map<String, Object> envelope = map(schemas, "Envelope");
        Map<String, Object> mapping = map(map(envelope, "discriminator"), "mapping");
        Map<String, Object> properties = map(envelope, "properties");
        Map<String, Object> example = map(envelope, "example");
        Map<String, Object> defaultValue = map(envelope, "default");
        Map<String, Object> extension = map(envelope, "x-payload");

        assertThat(map(schemas, "Item").get("type"), is("string"));
        assertThat(map(schemas, "Item2").get("type"), is("integer"));
        assertThat(map(properties, "item").get("$ref"), is("#/components/schemas/Item2"));
        assertThat(map(properties, "encodedItem").get("$ref"), is("#/components/schemas/Item2"));
        assertThat(map(properties, "nestedItem").get("$ref"),
                   is(renamedNestedItemRef));
        assertThat(map(properties, "dynamicItem").get("$dynamicRef"), is("#/components/schemas/Item2"));
        assertThat(map(properties, "encodedDynamicItem").get("$dynamicRef"),
                   is("#/components/schemas/Item2"));
        assertThat(map(properties, "dynamicNestedItem").get("$dynamicRef"),
                   is(renamedNestedItemRef));
        assertThat(map(properties, "dynamicAnchor").get("$dynamicRef"), is("#item"));
        assertThat(map(properties, "dynamicExternal").get("$dynamicRef"),
                   is("https://example.com/schemas/Item"));
        Map<String, Object> embeddedResource = map(properties, "embeddedResource");
        assertThat(embeddedResource.get("$ref"),
                   is(nestedItemRef));
        assertThat(embeddedResource.get("$dynamicRef"), is("#/components/schemas/Item"));
        assertThat(map(map(embeddedResource, "properties"), "nested").get("$dynamicRef"),
                   is("#/components/schemas/Item"));
        assertThat(map(map(embeddedResource, "properties"), "nestedRef").get("$ref"),
                   is(nestedItemRef));
        Map<String, Object> embeddedDiscriminator = map(embeddedResource, "discriminator");
        Map<String, Object> embeddedMapping = map(embeddedDiscriminator, "mapping");
        assertThat(embeddedMapping.get("byRef"),
                   is(nestedItemRef));
        assertThat(embeddedMapping.get("byName"), is("Item2"));
        assertThat(embeddedDiscriminator.get("defaultMapping"),
                   is(nestedItemRef));
        assertThat(map(schemas, "CustomDialect").get("$dynamicRef"), is("#/components/schemas/Item2"));
        assertThat(map(schemas, "Draft2020Dialect").get("$dynamicRef"),
                   is("#/components/schemas/Item2"));
        assertThat(map(schemas, "OasDialect").get("$dynamicRef"), is("#/components/schemas/Item2"));
        assertThat(mapping.get("second"), is("#/components/schemas/Item2"));
        assertThat(mapping.get("encodedSecond"), is("#/components/schemas/Item2"));
        assertThat(mapping.get("secondByName"), is("Item2"));
        assertThat(mapping.get("external"), is("https://example.com/schemas/Item"));
        assertThat(map(envelope, "discriminator").get("defaultMapping"), is("#/components/schemas/Item2"));
        assertThat(map(map(properties, "example"), "discriminator").get("defaultMapping"), is("Item2"));
        assertThat(map(map(properties, "external"), "discriminator").get("defaultMapping"),
                   is("https://example.com/schemas/Item"));
        assertThat(map(map(example, "discriminator"), "mapping").get("literal"), is("Item"));
        assertThat(map(example, "discriminator").get("defaultMapping"), is("Item"));
        assertThat(example.get("$ref"), is("#/components/schemas/Item"));
        assertThat(example.get("$dynamicRef"), is("#/components/schemas/Item"));
        assertThat(defaultValue.get("$ref"), is("#/components/schemas/Item"));
        assertThat(map(map(extension, "discriminator"), "mapping").get("literal"), is("Item"));
        assertThat(map(extension, "discriminator").get("defaultMapping"), is("Item"));
        assertThat(extension.get("$ref"), is("#/components/schemas/Item"));
        assertThat(map(content, "schema").get("$ref"), is("#/components/schemas/Envelope"));
    }

    @Test
    void generatedDocumentTraversesAdditionalItemsOnlyForOpenApi30() {
        for (RawOpenApiVersion version : List.of(RawOpenApiVersion.OPEN_API_30,
                                                 RawOpenApiVersion.OPEN_API_31,
                                                 RawOpenApiVersion.OPEN_API_32)) {
            OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY, version);
            OpenApiDocumentSource first = (_, document) -> document
                    .info("Generated API", "1.0.0")
                    .components(components -> components.schema(
                            "Item",
                            JsonObject.builder().set("type", "string").build()));
            OpenApiDocumentSource second = (_, document) -> document
                    .components(components -> components
                            .schema("Item", JsonObject.builder().set("type", "integer").build())
                            .schema("Envelope",
                                    JsonObject.builder()
                                            .set("additionalItems",
                                                 JsonObject.builder()
                                                         .set("$ref", "#/components/schemas/Item")
                                                         .build())
                                            .build()));

            Map<String, Object> document = parse(compose(
                    context,
                    context.openApiVersion(),
                    "",
                    MediaTypes.APPLICATION_OPENAPI_YAML,
                    List.of(first, second)));
            Map<String, Object> envelope = map(map(map(document, "components"), "schemas"), "Envelope");
            Map<String, Object> additionalItems = map(envelope, "additionalItems");
            String expectedRef = version == RawOpenApiVersion.OPEN_API_30
                    ? "#/components/schemas/Item2"
                    : "#/components/schemas/Item";

            assertThat(version.type(), additionalItems.get("$ref"), is(expectedRef));
        }
    }

    @Test
    void generatedDocumentPreservesDynamicRefsForOpenApi30() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_30);
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .schema("Envelope",
                                JsonObject.builder()
                                        .set("$ref", "#/components/schemas/Item")
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build()));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> envelope = map(map(map(document, "components"), "schemas"), "Envelope");

        assertThat(envelope.get("$ref"), is("#/components/schemas/Item2"));
        assertThat(envelope.get("$dynamicRef"), is("#/components/schemas/Item"));
    }

    @Test
    void generatedDocumentRewritesDynamicRefsForOpenApi31() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_31);
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .schema("Envelope",
                                JsonObject.builder()
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build()));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> envelope = map(map(map(document, "components"), "schemas"), "Envelope");

        assertThat(envelope.get("$dynamicRef"), is("#/components/schemas/Item2"));
    }

    @Test
    void generatedDocumentRewritesDynamicRefsForCustomDocumentDialect() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .schema("Envelope",
                                JsonObject.builder()
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build())
                        .schema("Draft2020Envelope",
                                JsonObject.builder()
                                        .set("$schema", "https://json-schema.org/draft/2020-12/schema")
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build())
                        .schema("OasEnvelope",
                                JsonObject.builder()
                                        .set("$schema", "https://spec.openapis.org/oas/3.1/dialect/base")
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build()));
        OpenApiDocumentSource third = (_, document) -> document
                .jsonSchemaDialect("https://example.com/dialect");

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second, third)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");
        Map<String, Object> envelope = map(schemas, "Envelope");

        assertThat(envelope.get("$dynamicRef"), is("#/components/schemas/Item2"));
        assertThat(map(schemas, "Draft2020Envelope").get("$dynamicRef"),
                   is("#/components/schemas/Item2"));
        assertThat(map(schemas, "OasEnvelope").get("$dynamicRef"),
                   is("#/components/schemas/Item2"));
    }

    @Test
    void generatedDocumentRewritesDynamicRefsForDraft2020DocumentDialect() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .jsonSchemaDialect("https://json-schema.org/draft/2020-12/schema")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .schema("Envelope",
                                JsonObject.builder()
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build()));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> envelope = map(map(map(document, "components"), "schemas"), "Envelope");

        assertThat(envelope.get("$dynamicRef"), is("#/components/schemas/Item2"));
    }

    @Test
    void mergeRewritesGeneratedDynamicRefsForCustomDocumentDialect() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.MERGE,
                                                    RawOpenApiVersion.OPEN_API_32);
        OpenApiDocument staticDocument = OpenApiDocument.builder()
                .info("Static API", "1.0.0")
                .jsonSchemaDialect("https://example.com/dialect")
                .build();
        OpenApiDocumentSource first = (_, document) -> document
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .schema("Envelope",
                                JsonObject.builder()
                                        .set("$ref", "#/components/schemas/Item")
                                        .set("$dynamicRef", "#/components/schemas/Item")
                                        .build()));

        String content = OpenApiDocumentComposer.compose(context,
                                                         Optional.of(() -> staticDocument),
                                                         "static",
                                                         List.of(first, second));
        Map<String, Object> envelope = map(map(map(parse(content), "components"), "schemas"), "Envelope");

        assertThat(envelope.get("$ref"), is("#/components/schemas/Item2"));
        assertThat(envelope.get("$dynamicRef"), is("#/components/schemas/Item2"));
    }

    @Test
    void generatedDocumentRewritesDiscriminatorUnderComponentNamedExample() {
        OpenApiDocumentContext context = rawContext(OpenApiGeneratedMode.GENERATED_ONLY,
                                                    RawOpenApiVersion.OPEN_API_32);
        JsonObject itemSchema = JsonObject.builder()
                .setValues("oneOf",
                           List.of(JsonObject.builder()
                                           .set("$ref", "#/components/schemas/Item")
                                           .build()))
                .set("discriminator",
                     JsonObject.builder()
                             .set("propertyName", "kind")
                             .set("mapping", JsonObject.builder().set("second", "Item").build())
                             .set("defaultMapping", "#/components/schemas/Item")
                             .build())
                .build();
        JsonObject literalDataValue = JsonObject.builder()
                .set("$ref", "#/components/schemas/Item")
                .set("schema",
                     JsonObject.builder()
                             .set("discriminator",
                                  JsonObject.builder()
                                          .set("propertyName", "kind")
                                          .set("mapping", JsonObject.builder().set("literal", "Item").build())
                                          .set("defaultMapping", "Item")
                                          .build())
                             .build())
                .build();
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> document
                .components(components -> components
                        .schema("Item", JsonObject.builder().set("type", "integer").build())
                        .requestBody("example",
                                     requestBody -> requestBody.content(MediaTypes.APPLICATION_JSON_VALUE,
                                                                        media -> media
                                                                                .itemSchema(itemSchema)
                                                                                .example(
                                                                                        "literal",
                                                                                        OpenApiDocument.Example.builder()
                                                                                                .dataValue(literalDataValue)
                                                                                                .build()))));

        Map<String, Object> document = parse(compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> components = map(document, "components");
        Map<String, Object> requestBody = map(map(components, "requestBodies"), "example");
        Map<String, Object> content = map(requestBody, "content");
        Map<String, Object> mediaType = map(content, MediaTypes.APPLICATION_JSON_VALUE);
        Map<String, Object> rewrittenSchema = map(mediaType, "itemSchema");
        Map<String, Object> discriminator = map(rewrittenSchema, "discriminator");
        Map<String, Object> dataValue = map(map(map(mediaType, "examples"), "literal"), "dataValue");
        Map<String, Object> literalDiscriminator = map(map(dataValue, "schema"), "discriminator");

        assertThat(map(map(components, "schemas"), "Item2").get("type"), is("integer"));
        assertThat(((Map<?, ?>) list(rewrittenSchema, "oneOf").getFirst()).get("$ref"),
                   is("#/components/schemas/Item2"));
        assertThat(map(discriminator, "mapping").get("second"), is("Item2"));
        assertThat(discriminator.get("defaultMapping"), is("#/components/schemas/Item2"));
        assertThat(dataValue.get("$ref"), is("#/components/schemas/Item"));
        assertThat(map(literalDiscriminator, "mapping").get("literal"), is("Item"));
        assertThat(literalDiscriminator.get("defaultMapping"), is("Item"));
    }

    @Test
    void mergeStaticPreservesEmptyOperationSecurityOverride() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);

        String content = compose(context,
                                 context.openApiVersion(),
                                 STATIC_PUBLIC_OPERATION_DOCUMENT,
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(operationSource()));

        Map<String, Object> operation = map(map(map(parse(content), "paths"), "/public"), "get");
        assertThat(list(operation, "security"), is(List.of()));
    }

    @Test
    void mergeFailsOnDuplicateStaticAndGeneratedOperationId() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> compose(
                                                            context,
                                                            context.openApiVersion(),
                                                            STATIC_DOCUMENT,
                                                            MediaTypes.APPLICATION_OPENAPI_YAML,
                                                            List.of(operationSource("/generated", "staticGet"))));

        assertThat(thrown.getMessage(), containsString("Duplicate OpenAPI operationId staticGet"));
        assertThat(thrown.getMessage(), containsString("paths./static.get"));
        assertThat(thrown.getMessage(), containsString("paths./generated.get"));
    }

    @Test
    void mergeStaticFailsWhenExplicitNullConflictsWithGeneratedValue() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);
        OpenApiDocumentSource conflicting = (_, document) -> document.extension("x-null",
                                                                                              JsonString.create("value"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> compose(
                                                            context,
                                                            context.openApiVersion(),
                                                            STATIC_NULL_EXTENSION_DOCUMENT,
                                                            MediaTypes.APPLICATION_OPENAPI_YAML,
                                                            List.of(conflicting)));

        assertThat(thrown.getMessage(), is("Conflicting OpenAPI document value at x-null"));
    }

    @Test
    void mergeStaticKeepsMatchingExplicitNullGeneratedValue() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);
        OpenApiDocumentSource matching = (_, document) -> document.extension("x-null", JsonNull.instance());

        String content = compose(context,
                                 context.openApiVersion(),
                                 STATIC_NULL_EXTENSION_DOCUMENT,
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(matching));

        Map<String, Object> parsed = parse(content);
        assertThat(parsed.containsKey("x-null"), is(true));
        assertThat(parsed.get("x-null"), is((Object) null));
    }

    @Test
    void buildersRejectJavaNullExtensionValues() {
        assertThrows(NullPointerException.class, () -> OpenApiDocument.builder().extension("x-null", null));
        assertThrows(NullPointerException.class, () -> OpenApiDocument.Info.builder().extension("x-null", null));
        assertThrows(NullPointerException.class, () -> OpenApiDocument.Operation.builder().extension("x-null", null));
    }

    @Test
    void documentBuilderRejectsNullPathItemMaps() {
        assertThrows(NullPointerException.class, () -> OpenApiDocument.builder().paths(null));
        assertThrows(NullPointerException.class, () -> OpenApiDocument.builder().webhooks(null));
    }

    @Test
    void mergeFailsWhenExplicitNullPathItemConflictsWithSourceValue() {
        Map<String, Object> target = documentWithPathValue("/static", null);
        Map<String, Object> source = documentWithPathValue("/static", pathItem("generatedGet"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> OpenApiDocument.merge(target, source, ""));

        assertThat(thrown.getMessage(), is("Conflicting OpenAPI document value at paths./static"));
    }

    @Test
    void mergeKeepsMatchingExplicitNullPathItem() {
        Map<String, Object> target = documentWithPathValue("/static", null);
        Map<String, Object> source = documentWithPathValue("/static", null);

        OpenApiDocument.merge(target, source, "");

        Map<String, Object> paths = map(target, "paths");
        assertThat(paths.containsKey("/static"), is(true));
        assertThat(paths.get("/static"), is((Object) null));
    }

    @Test
    void mergeFailsWhenExplicitNullAdditionalOperationsConflictsWithSourceValue() {
        Map<String, Object> targetPath = new LinkedHashMap<>();
        targetPath.put("additionalOperations", null);
        Map<String, Object> sourcePath = new LinkedHashMap<>();
        sourcePath.put("additionalOperations", Map.of("COPY", operation("copyStatic")));
        Map<String, Object> target = documentWithPathValue("/static", targetPath);
        Map<String, Object> source = documentWithPathValue("/static", sourcePath);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> OpenApiDocument.merge(target, source, ""));

        assertThat(thrown.getMessage(),
                   is("Conflicting OpenAPI document value at paths./static.additionalOperations"));
    }

    @Test
    void mergeKeepsMatchingExplicitNullAdditionalOperations() {
        Map<String, Object> targetPath = new LinkedHashMap<>();
        targetPath.put("additionalOperations", null);
        Map<String, Object> sourcePath = new LinkedHashMap<>();
        sourcePath.put("additionalOperations", null);
        Map<String, Object> target = documentWithPathValue("/static", targetPath);
        Map<String, Object> source = documentWithPathValue("/static", sourcePath);

        OpenApiDocument.merge(target, source, "");

        Map<String, Object> path = map(map(target, "paths"), "/static");
        assertThat(path.containsKey("additionalOperations"), is(true));
        assertThat(path.get("additionalOperations"), is((Object) null));
    }

    @Test
    void generatedSourcesCanContributeOperationsToSamePath() {
        OpenApiDocumentSource first = (context, document) -> document.info("Generated API", "1.0.0")
                .path("/generated",
                      path -> path.operation("GET",
                                             operation -> operation.operationId("generatedGet")
                                                     .response("200", "Generated response.")));
        OpenApiDocumentSource second = (context, document) -> document.path("/generated",
                                                                            path -> path.operation(
                                                                                    "POST",
                                                                                    responseOperation("generatedPost")));
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);

        String content = compose(context,
                                 context.openApiVersion(),
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(first, second));

        Map<String, Object> path = map(map(parse(content), "paths"), "/generated");
        assertThat(path.containsKey("get"), is(true));
        assertThat(path.containsKey("post"), is(true));
    }

    @Test
    void mergeStaticFailsOnConflictingOperation() {
        OpenApiDocumentSource conflicting = (context, document) -> document.path(
                "/static",
                path -> path.operation("GET",
                                       operation -> operation
                        .operationId("other")
                                               .response("200", "Other response.")));

        assertThrows(IllegalStateException.class,
                     () -> {
                         OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);
                         compose(context,
                                 context.openApiVersion(),
                                 STATIC_DOCUMENT,
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(conflicting));
                     });
    }

    @Test
    void mergeStaticFailsOnConflictingNormalizedPathTemplate() {
        OpenApiDocumentSource conflicting = (context, document) -> document.path(
                "/static/{name}",
                path -> path.parameter(parameter -> parameter
                                .name("name")
                                .in("path")
                                .required(true)
                                .schema(JsonObject.builder().set("type", "string").build()))
                        .operation("GET",
                                   operation -> operation
                                           .operationId("other")
                                           .response("200", "Other response.")));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> {
                                                        OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);
                                                        compose(context,
                                                                context.openApiVersion(),
                                                                STATIC_TEMPLATE_DOCUMENT,
                                                                MediaTypes.APPLICATION_OPENAPI_YAML,
                                                                List.of(conflicting));
                                                    });

        assertThat(thrown.getMessage(),
                   is("Conflicting OpenAPI path template at paths./static/{id} and paths./static/{name}"));
    }

    @Test
    void mergeStaticUsesStaticDocumentVersionParser() {
        OpenApiVersion renderVersion = new TestOpenApiVersion("3.0", "3.0.3", true);
        OpenApiVersion staticVersion = new TestOpenApiVersion("3.1", "3.1.0", false);
        OpenApiDocumentContext context = new OpenApiDocumentContextImpl("openapi",
                                                                        "/openapi",
                                                                        "default",
                                                                        OpenApiGeneratedMode.MERGE,
                                                                        renderVersion);

        String content = compose(context,
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
        OpenApiDocumentContext context = new OpenApiDocumentContextImpl("openapi",
                                                                        "/openapi",
                                                                        "default",
                                                                        OpenApiGeneratedMode.MERGE,
                                                                        RawOpenApiVersion.INSTANCE);
        OpenApiDocumentSource generated = (ignored, document) -> document
                .path("/static",
                      path -> path.operation("COPY", responseOperation("copyStatic"))
                              .operation("POST", responseOperation("createStatic")));

        String content = compose(context,
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
        OpenApiDocumentContext context = new OpenApiDocumentContextImpl("openapi",
                                                                        "/openapi",
                                                                        "default",
                                                                        OpenApiGeneratedMode.MERGE,
                                                                        RawOpenApiVersion.INSTANCE);
        OpenApiDocumentSource generated = (ignored, document) -> document.path("/static",
                                                                               path -> path.operation(
                                                                                       "COPY",
                                                                                       responseOperation("copyOther")));

        assertThrows(IllegalStateException.class,
                     () -> compose(context,
                                   RawOpenApiVersion.INSTANCE,
                                   STATIC_DOCUMENT_WITH_ADDITIONAL_OPERATION,
                                   MediaTypes.APPLICATION_OPENAPI_YAML,
                                   List.of(generated)));
    }

    @Test
    void webhooksUseLiteralNamesInsteadOfPathTemplateNormalization() {
        OpenApiDocumentContext context = new OpenApiDocumentContextImpl("openapi",
                                                                        "/openapi",
                                                                        "default",
                                                                        OpenApiGeneratedMode.GENERATED_ONLY,
                                                                        RawOpenApiVersion.INSTANCE);
        OpenApiDocumentSource generated = (ignored, document) -> document
                .info("Generated API", "1.0.0")
                .webhook("order.{created}", path -> path.operation("POST", responseOperation("orderCreated")))
                .webhook("order.{deleted}", path -> path.operation("POST", responseOperation("orderDeleted")));

        String content = compose(context,
                                 RawOpenApiVersion.INSTANCE,
                                 "",
                                 MediaTypes.APPLICATION_OPENAPI_YAML,
                                 List.of(generated));

        Map<String, Object> webhooks = map(parse(content), "webhooks");
        assertThat(webhooks.containsKey("order.{created}"), is(true));
        assertThat(webhooks.containsKey("order.{deleted}"), is(true));
    }

    @Test
    void mergeKeepsWebhookLiteralNamesInsteadOfPathTemplateNormalization() {
        OpenApiDocument existing = OpenApiDocument.builder()
                .info("Static API", "1.0.0")
                .webhook("order.{created}", path -> path.operation("POST", responseOperation("orderCreated")))
                .build();
        OpenApiDocument merged = OpenApiDocument.builder()
                .merge(existing)
                .webhook("order.{deleted}", path -> path.operation("POST", responseOperation("orderDeleted")))
                .build();

        Map<String, Object> webhooks = map(parse(merged.toJsonObject().toString()), "webhooks");
        assertThat(webhooks.containsKey("order.{created}"), is(true));
        assertThat(webhooks.containsKey("order.{deleted}"), is(true));
    }

    @Test
    void componentSchemaUsesJsonSchemaModelWithoutRootKeywords() {
        Schema schema = Schema.builder()
                .id(URI.create("https://example.com/schemas/item"))
                .rootObject(builder -> builder.addStringProperty("name", name -> name.description("Item name")))
                .build();
        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .paths(Map.of())
                .components(components -> components.schema("Item", schema.generateObjectNoKeywords()))
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
                .path("/generated",
                      path -> path.operation("GET",
                                             operation -> operation.operationId("generatedGet")
                                                     .response("200", "Generated response.")));
    }

    private static OpenApiDocumentSource operationSource() {
        return (context, document) -> document.path("/generated",
                                                    path -> path.operation("GET",
                                                                           operation -> operation
                                                                                   .operationId("generatedGet")
                                                                                   .response("200",
                                                                                           "Generated response.")));
    }

    private static OpenApiDocumentSource operationSource(String path, String operationId) {
        return (context, document) -> document.path(path,
                                                    pathBuilder -> pathBuilder.operation(
                                                            "GET",
                                                            operation -> operation.operationId(operationId)
                                                                    .response("200", "OK")));
    }

    private static OpenApiDocumentSource mergeSource() {
        return (context, document) -> document
                .tag(tag -> tag.name("generated")
                        .description("Generated resources"))
                .components(components -> components
                        .schema("GeneratedItem",
                                JsonObject.builder()
                                        .set("type", "object")
                                        .build())
                        .securityScheme("generatedAuth", security -> security
                                .type("oauth2")
                                .flows(JsonObject.builder()
                                               .set("clientCredentials",
                                                    JsonObject.builder()
                                                            .set("tokenUrl", "https://id.example.test/token")
                                                            .set("scopes",
                                                                 JsonObject.builder()
                                                                         .set("generated:read", "Read generated")
                                                                         .build())
                                                            .build())
                                               .build())))
                .securityRequirement("generatedAuth", List.of("generated:read"))
                .path("/generated",
                      path -> path.operation("GET",
                                             operation -> operation.operationId("generatedGet")
                                                     .response("200", "Generated response.")));
    }

    private static OpenApiDocument.Operation responseOperation(String operationId) {
        return OpenApiDocument.Operation.builder()
                .operationId(operationId)
                .response("200", "OK")
                .build();
    }

    private static String pathItemReference(int level) {
        return "#/components/pathItems/" + pathItemName(level);
    }

    private static String pathItemName(int level) {
        return "Path" + level;
    }

    private static String callbackReference(int level) {
        return "#/components/callbacks/" + callbackName(level);
    }

    private static String callbackName(int level) {
        return "Callback" + level;
    }

    private static Map<String, Object> collidingSchemas(OpenApiDocumentContext context,
                                                        String self,
                                                        Map<String, String> references) {
        OpenApiDocumentSource first = (_, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (_, document) -> {
            document.self(self)
                    .components(components -> {
                        components.schema("Item", JsonObject.builder().set("type", "integer").build());
                        references.forEach((name, reference) -> components.schema(
                                name,
                                JsonObject.builder().set("$ref", reference).build()));
                    });
        };
        Map<String, Object> document = parse(compose(context,
                                                     context.openApiVersion(),
                                                     "",
                                                     MediaTypes.APPLICATION_OPENAPI_YAML,
                                                     List.of(first, second)));
        return map(map(document, "components"), "schemas");
    }

    private static String compose(OpenApiDocumentContext context,
                                  OpenApiVersion staticOpenApiVersion,
                                  String staticContent,
                                  MediaType staticContentMediaType,
                                  List<OpenApiDocumentSource> sources) {
        return OpenApiDocumentComposer.compose(
                context,
                Optional.of(() -> {
                    OpenApiDocumentContext staticContext = new OpenApiDocumentContextImpl(context.featureName(),
                                                                                          context.webContext(),
                                                                                          context.listener(),
                                                                                          context.generatedMode(),
                                                                                          staticOpenApiVersion);
                    return staticOpenApiVersion.parse(staticContext, staticContent, staticContentMediaType);
                }),
                staticContent,
                sources);
    }

    private static void assertDuplicateOperationId(OpenApiDocumentSource source, String expectedMessage) {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        assertDuplicateOperationId(context, source, expectedMessage);
    }

    private static void assertDuplicateOperationId(OpenApiDocumentSource source,
                                                   OpenApiVersion version,
                                                   String expectedMessage) {
        assertDuplicateOperationId(rawContext(OpenApiGeneratedMode.GENERATED_ONLY, version), source, expectedMessage);
    }

    private static void assertDuplicateOperationId(OpenApiDocumentContext context,
                                                   OpenApiDocumentSource source,
                                                   String expectedMessage) {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> compose(
                                                            context,
                                                            context.openApiVersion(),
                                                            "",
                                                            MediaTypes.APPLICATION_OPENAPI_YAML,
                                                            List.of(source)));

        assertThat(thrown.getMessage(), is(expectedMessage));
    }

    private static Map<String, Object> documentWithPathValue(String path, Object value) {
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put(path, value);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("paths", paths);
        return document;
    }

    private static Map<String, Object> pathItem(String operationId) {
        Map<String, Object> pathItem = new LinkedHashMap<>();
        pathItem.put("get", operation(operationId));
        return pathItem;
    }

    private static Map<String, Object> operation(String operationId) {
        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("200", Map.of("description", "OK"));
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", operationId);
        operation.put("responses", responses);
        return operation;
    }

    private static OpenApiDocumentContext context(OpenApiGeneratedMode mode) {
        return context(mode, Map.of());
    }

    private static OpenApiDocumentContext context(OpenApiGeneratedMode mode, Map<String, String> operationIds) {
        return new OpenApiDocumentContextImpl("openapi",
                                              "/openapi",
                                              "default",
                                              mode,
                                              OpenApi30Version.create(),
                                              operationIds);
    }

    private static OpenApiDocumentContext rawContext(OpenApiGeneratedMode mode) {
        return rawContext(mode, RawOpenApiVersion.INSTANCE);
    }

    private static OpenApiDocumentContext rawContext(OpenApiGeneratedMode mode, OpenApiVersion version) {
        return new OpenApiDocumentContextImpl("openapi",
                                              "/openapi",
                                              "default",
                                              mode,
                                              version);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String content) {
        return new Yaml().load(content);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<?, ?> map, String name) {
        return (Map<String, Object>) map.get(name);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<?, ?> map, String name) {
        return (List<Object>) map.get(name);
    }

    private record TestOpenApiVersion(String type, String version, boolean failParse) implements OpenApiVersion {
        @Override
        public OpenApiDocument parse(OpenApiDocumentContext context,
                                     String content,
                                     MediaType mediaType) {
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
        private static final RawOpenApiVersion INSTANCE = new RawOpenApiVersion("raw", "raw");
        private static final RawOpenApiVersion OPEN_API_30 = new RawOpenApiVersion("3.0", "3.0.4");
        private static final RawOpenApiVersion OPEN_API_31 = new RawOpenApiVersion("3.1", "3.1.2");
        private static final RawOpenApiVersion OPEN_API_32 = new RawOpenApiVersion("3.2", "3.2.0");

        private final String type;
        private final String version;

        private RawOpenApiVersion(String type, String version) {
            this.type = type;
            this.version = version;
        }

        @Override
        public OpenApiDocument parse(OpenApiDocumentContext context,
                                     String content,
                                     MediaType mediaType) {
            OpenApiDocument document = OpenApi30Version.create().parse(context, content, mediaType);
            if (content.contains("operationId: staticCopy")) {
                return OpenApiDocument.builder()
                        .merge(document)
                        .path("/static", path -> path.operation("COPY", responseOperation("staticCopy")))
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
            return version;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public String name() {
            return type;
        }
    }

}
