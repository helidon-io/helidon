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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    void ignoreGeneratedKeepsStaticDocument() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.STATIC_ONLY);
        String content = OpenApiDocumentComposer.compose(context,
                                                        context.openApiVersion(),
                                                        STATIC_DOCUMENT,
                                                        MediaTypes.APPLICATION_OPENAPI_YAML,
                                                        List.of(source()));

        assertThat(content, is(STATIC_DOCUMENT));
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
    void generatedDocumentRequiresInfo() {
        for (OpenApiGeneratedMode mode : List.of(OpenApiGeneratedMode.STATIC_FIRST,
                                                 OpenApiGeneratedMode.MERGE,
                                                 OpenApiGeneratedMode.GENERATED_ONLY)) {
            OpenApiDocumentContext context = context(mode);
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApiDocumentComposer.compose(context,
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

        String content = OpenApiDocumentComposer.compose(context,
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
                                                    () -> OpenApiDocumentComposer.compose(
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

        String content = OpenApiDocumentComposer.compose(context,
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

        String content = OpenApiDocumentComposer.compose(context,
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
        assertThat(map(map(staticHeader, "examples"), "named").get("value"), is("named-static-value"));

        assertThat(((Map<?, ?>) list(parsed, "tags").get(0)).get("name"), is("static"));
        assertThat(((Map<?, ?>) list(parsed, "tags").get(1)).get("name"), is("generated"));
        assertThat(((Map<?, ?>) list(parsed, "security").get(0)).get("staticAuth"), is(List.of()));
        assertThat(((Map<?, ?>) list(parsed, "security").get(1)).get("generatedAuth"), is(List.of("generated:read")));
    }

    @Test
    void generatedDocumentReusesEquivalentSchemasAcrossSources() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource first = (documentContext, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "First",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (documentContext, document) -> document
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

        Map<String, Object> document = parse(OpenApiDocumentComposer.compose(
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
    void generatedDocumentReusesEquivalentSchemasAfterReferenceRewriting() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource first = (documentContext, document) -> document
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
        OpenApiDocumentSource second = (documentContext, document) -> document
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

        Map<String, Object> document = parse(OpenApiDocumentComposer.compose(
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
    void generatedDocumentRenamesCollidingSchemasAndReferences() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        OpenApiDocumentSource first = (documentContext, document) -> document
                .info("Generated API", "1.0.0")
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "string").build()));
        OpenApiDocumentSource second = (documentContext, document) -> document
                .components(components -> components.schema(
                        "Item",
                        JsonObject.builder().set("type", "integer").build()))
                .path("/second",
                      path -> path.operation(
                              "GET",
                              operation -> operation.operationId("secondGet")
                                      .response("200",
                                                response -> response.description("OK")
                                                        .content(MediaTypes.APPLICATION_JSON_VALUE,
                                                                 media -> media.schema(JsonObject.builder()
                                                                                                .set("$ref",
                                                                                                     "#/components/schemas/Item")
                                                                                                .build())))));

        Map<String, Object> document = parse(OpenApiDocumentComposer.compose(
                context,
                context.openApiVersion(),
                "",
                MediaTypes.APPLICATION_OPENAPI_YAML,
                List.of(first, second)));
        Map<String, Object> schemas = map(map(document, "components"), "schemas");
        Map<String, Object> operation = map(map(map(document, "paths"), "/second"), "get");
        Map<String, Object> response = map(map(operation, "responses"), "200");
        Map<String, Object> content = map(map(response, "content"), MediaTypes.APPLICATION_JSON_VALUE);

        assertThat(map(schemas, "Item").get("type"), is("string"));
        assertThat(map(schemas, "Item2").get("type"), is("integer"));
        assertThat(map(content, "schema").get("$ref"), is("#/components/schemas/Item2"));
    }

    @Test
    void mergeStaticPreservesEmptyOperationSecurityOverride() {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);

        String content = OpenApiDocumentComposer.compose(context,
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
                                                    () -> OpenApiDocumentComposer.compose(
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
        OpenApiDocumentSource conflicting = (documentContext, document) -> document.extension("x-null",
                                                                                              JsonString.create("value"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> OpenApiDocumentComposer.compose(
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
        OpenApiDocumentSource matching = (documentContext, document) -> document.extension("x-null", JsonNull.instance());

        String content = OpenApiDocumentComposer.compose(context,
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

        String content = OpenApiDocumentComposer.compose(context,
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
                         OpenApiDocumentComposer.compose(context,
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
                path -> path.operation("GET",
                                       operation -> operation
                                               .operationId("other")
                                               .response("200", "Other response.")));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> {
                                                        OpenApiDocumentContext context = context(OpenApiGeneratedMode.MERGE);
                                                        OpenApiDocumentComposer.compose(context,
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
        OpenApiDocumentContext context = new OpenApiDocumentContextImpl("openapi",
                                                                        "/openapi",
                                                                        "default",
                                                                        OpenApiGeneratedMode.MERGE,
                                                                        RawOpenApiVersion.INSTANCE);
        OpenApiDocumentSource generated = (ignored, document) -> document
                .path("/static",
                      path -> path.operation("COPY", responseOperation("copyStatic"))
                              .operation("POST", responseOperation("createStatic")));

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
                     () -> OpenApiDocumentComposer.compose(context,
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

        String content = OpenApiDocumentComposer.compose(context,
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

    private static void assertDuplicateOperationId(OpenApiDocumentSource source, String expectedMessage) {
        OpenApiDocumentContext context = context(OpenApiGeneratedMode.GENERATED_ONLY);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> OpenApiDocumentComposer.compose(
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
                get:
                  operationId: staticGet
                  responses:
                    "200":
                      description: Static response.
            """;
}
