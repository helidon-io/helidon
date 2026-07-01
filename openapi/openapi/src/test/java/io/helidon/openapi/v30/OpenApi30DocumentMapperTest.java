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

package io.helidon.openapi.v30;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.openapi.OpenApiDocument;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApi30DocumentMapperTest {
    private static final long LARGE_INTEGRAL_VALUE = 9_007_199_254_740_993L;

    @Test
    void validatesOpenApiVersion() {
        OpenApi30DocumentMapper.parse(document("3.0.4-rc1"));

        for (String invalidVersion : List.of("3.0", "3.0.", "3.0.not-a-version", "3.0.1-", "3.0.1.0")) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi30DocumentMapper.parse(document(invalidVersion)),
                                                    invalidVersion);
            assertThat(invalidVersion, ex.getMessage(), containsString(invalidVersion));
        }
    }

    @Test
    void handlesVersionSpecificResponseRequirements() {
        IllegalStateException missingResponses = assertThrows(
                IllegalStateException.class,
                () -> OpenApi30DocumentMapper.parse(documentWithOperation("3.0.3", Map.of("summary", "Items"))));
        assertThat(missingResponses.getMessage(), containsString("responses"));

        IllegalStateException missingDescription = assertThrows(
                IllegalStateException.class,
                () -> OpenApi30DocumentMapper.parse(documentWithOperation(
                        "3.0.3",
                        Map.of("responses", Map.of("200", Map.of("headers", Map.of()))))));
        assertThat(missingDescription.getMessage(), containsString("description"));

        OpenApiDocument operationWithoutResponses = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.operation("GET", operation -> operation.summary("Items")))
                .build();
        IllegalStateException renderedWithoutResponses = assertThrows(
                IllegalStateException.class,
                () -> OpenApi30DocumentMapper.render(operationWithoutResponses, "3.0.3"));
        assertThat(renderedWithoutResponses.getMessage(), containsString("responses"));

        OpenApiDocument responseWithoutDescription = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.operation(
                        "GET",
                        operation -> operation.response("200", response -> response.summary("Items"))))
                .build();
        IllegalStateException renderedWithoutDescription = assertThrows(
                IllegalStateException.class,
                () -> OpenApi30DocumentMapper.render(responseWithoutDescription, "3.0.3"));
        assertThat(renderedWithoutDescription.getMessage(), containsString("description"));
    }

    @Test
    void preservesLargeIntegralNumbers() {
        OpenApiDocument document = OpenApi30DocumentMapper.parse(document("3.0.3"));
        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");

        assertThat(String.valueOf(schemaProperty(rendered, "large").get("default")), is(String.valueOf(LARGE_INTEGRAL_VALUE)));
    }

    @Test
    void canonicalizesBooleanExclusiveBounds() {
        Map<String, Object> bounded = new LinkedHashMap<>();
        bounded.put("type", "number");
        bounded.put("maximum", 10);
        bounded.put("exclusiveMaximum", true);
        bounded.put("minimum", 1);
        bounded.put("exclusiveMinimum", true);
        Map<String, Object> inclusive = new LinkedHashMap<>();
        inclusive.put("type", "number");
        inclusive.put("maximum", 100);
        inclusive.put("exclusiveMaximum", false);
        inclusive.put("minimum", 0);
        inclusive.put("exclusiveMinimum", false);

        OpenApiDocument document = OpenApi30DocumentMapper.parse(document("3.0.3", bounded, inclusive));
        Map<String, Object> canonical = parse(document.toJsonObject().toString());
        Map<String, Object> boundedSchema = schemaProperty(canonical, "bounded");
        Map<String, Object> inclusiveSchema = schemaProperty(canonical, "inclusive");

        assertThat(boundedSchema.containsKey("maximum"), is(false));
        assertThat(boundedSchema.get("exclusiveMaximum"), is(10));
        assertThat(boundedSchema.containsKey("minimum"), is(false));
        assertThat(boundedSchema.get("exclusiveMinimum"), is(1));
        assertThat(inclusiveSchema.get("maximum"), is(100));
        assertThat(inclusiveSchema.containsKey("exclusiveMaximum"), is(false));
        assertThat(inclusiveSchema.get("minimum"), is(0));
        assertThat(inclusiveSchema.containsKey("exclusiveMinimum"), is(false));
    }

    @Test
    void openApi30RenderUsesStricterNumericExclusiveOrInclusiveBounds() {
        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .components(components -> components
                        .schema("ExclusiveUpperWins", schema("exclusiveMaximum", 5, "maximum", 10))
                        .schema("InclusiveUpperWins", schema("exclusiveMaximum", 15, "maximum", 10))
                        .schema("ExclusiveLowerWins", schema("exclusiveMinimum", 5, "minimum", 0))
                        .schema("InclusiveLowerWins", schema("exclusiveMinimum", 5, "minimum", 10)))
                .build();

        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");
        Map<String, Object> exclusiveUpper = schema(rendered, "ExclusiveUpperWins");
        Map<String, Object> inclusiveUpper = schema(rendered, "InclusiveUpperWins");
        Map<String, Object> exclusiveLower = schema(rendered, "ExclusiveLowerWins");
        Map<String, Object> inclusiveLower = schema(rendered, "InclusiveLowerWins");

        assertThat(String.valueOf(exclusiveUpper.get("maximum")), is("5"));
        assertThat(exclusiveUpper.get("exclusiveMaximum"), is(true));
        assertThat(String.valueOf(inclusiveUpper.get("maximum")), is("10"));
        assertThat(inclusiveUpper.containsKey("exclusiveMaximum"), is(false));
        assertThat(String.valueOf(exclusiveLower.get("minimum")), is("5"));
        assertThat(exclusiveLower.get("exclusiveMinimum"), is(true));
        assertThat(String.valueOf(inclusiveLower.get("minimum")), is("10"));
        assertThat(inclusiveLower.containsKey("exclusiveMinimum"), is(false));
    }

    @Test
    void preservesNullExtensionValues() {
        OpenApiDocument document = OpenApi30DocumentMapper.parse(documentWithNullExtension("3.0.3"));
        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");

        assertThat(rendered.containsKey("x-null"), is(true));
        assertThat(rendered.get("x-null"), is((Object) null));
    }

    @Test
    void preservesResponseExtensions() {
        OpenApiDocument document = OpenApi30DocumentMapper.parse(Map.of(
                "openapi", "3.0.3",
                "info", Map.of(
                        "title", "Static API",
                        "version", "1.0.0"),
                "paths", Map.of(
                        "/static", Map.of(
                                "x-path-meta", "keep",
                                "get", Map.of(
                                        "responses", Map.of(
                                                "x-provider-meta", true,
                                                "x-provider-object", Map.of("enabled", true),
                                                "200", Map.of(
                                                        "description", "OK",
                                                        "headers", Map.of(
                                                                "X-Trace", Map.of(
                                                                        "description", "Trace header",
                                                                        "x-header", true)),
                                                        "content", Map.of(
                                                                "application/json", Map.of(
                                                                        "schema", Map.of("type", "object"),
                                                                        "examples", Map.of(
                                                                                "StaticExample", Map.of(
                                                                                        "value", Map.of("message", "ok"),
                                                                                        "x-example", "keep")),
                                                                        "encoding", Map.of(
                                                                                "payload", Map.of(
                                                                                        "contentType", "application/json",
                                                                                        "x-encoding", "keep")),
                                                                        "x-media", true)),
                                                        "links", Map.of(
                                                                "StaticLink", Map.of(
                                                                        "operationId", "followUp",
                                                                        "x-link", "keep")),
                                                        "x-static-response", "preserved"))))),
                "components", Map.of(
                        "x-components", true,
                        "responses", Map.of(
                                "x-Problem", Map.of(
                                        "description", "Problem details",
                                        "summary", "OpenAPI 3.2 summary",
                                        "x-response", "preserved")),
                        "parameters", Map.of(
                                "GatewayPolicy", Map.of(
                                        "name", "policy",
                                        "in", "query",
                                        "schema", Map.of("type", "string"),
                                        "x-gateway-policy", "preserved")),
                        "requestBodies", Map.of(
                                "CodegenRequest", Map.of(
                                        "content", Map.of(
                                                "application/json", Map.of(
                                                        "schema", Map.of("type", "object"))),
                                        "x-codegen-request", true)),
                        "securitySchemes", Map.of(
                                "AmazonAuth", Map.of(
                                        "type", "http",
                                        "scheme", "bearer",
                                        "x-amazon-apigateway-authtype", "custom")))));
        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");
        Map<String, Object> staticPath = map(map(rendered, "paths"), "/static");
        Map<String, Object> response = map(map(staticPath, "get"), "responses");
        Map<String, Object> okResponse = map(response, "200");
        Map<String, Object> content = map(map(okResponse, "content"), "application/json");
        Map<String, Object> example = map(map(content, "examples"), "StaticExample");
        Map<String, Object> encoding = map(map(content, "encoding"), "payload");
        Map<String, Object> link = map(map(okResponse, "links"), "StaticLink");
        Map<String, Object> components = map(rendered, "components");
        Map<String, Object> componentResponse = map(map(components, "responses"), "x-Problem");
        Map<String, Object> parameter = map(map(components, "parameters"), "GatewayPolicy");
        Map<String, Object> requestBody = map(map(components, "requestBodies"), "CodegenRequest");
        Map<String, Object> securityScheme = map(map(components, "securitySchemes"), "AmazonAuth");

        assertThat(document.paths().get("/static").operations().get("get").responses().containsKey("x-provider-object"),
                   is(false));
        assertThat(staticPath.get("x-path-meta"), is("keep"));
        assertThat(response.get("x-provider-meta"), is(true));
        assertThat(map(response, "x-provider-object").get("enabled"), is(true));
        assertThat(okResponse.get("x-static-response"), is("preserved"));
        assertThat(map(map(okResponse, "headers"), "X-Trace").get("x-header"), is(true));
        assertThat(content.get("x-media"), is(true));
        assertThat(example.get("x-example"), is("keep"));
        assertThat(encoding.get("x-encoding"), is("keep"));
        assertThat(link.get("x-link"), is("keep"));
        assertThat(components.get("x-components"), is(true));
        assertThat(componentResponse.get("description"), is("Problem details"));
        assertThat(componentResponse.containsKey("summary"), is(false));
        assertThat(componentResponse.get("x-response"), is("preserved"));
        assertThat(parameter.get("x-gateway-policy"), is("preserved"));
        assertThat(requestBody.get("x-codegen-request"), is(true));
        assertThat(securityScheme.get("x-amazon-apigateway-authtype"), is("custom"));
    }

    @Test
    void preservesContainerExtensions() {
        Map<String, Object> callbackPost = new LinkedHashMap<>();
        callbackPost.put("post", Map.of(
                "responses", Map.of(
                        "200", Map.of("description", "OK"))));

        Map<String, Object> callback = new LinkedHashMap<>();
        callback.put("x-callback-scalar", "keep");
        callback.put("x-callback-object", Map.of("enabled", true));
        callback.put("{$request.body#/callbackUrl}", callbackPost);

        Map<String, Object> callbacks = new LinkedHashMap<>();
        callbacks.put("onEvent", callback);
        callbacks.put("x-named-callback", Map.of("{$request.body#/fallbackUrl}", callbackPost));
        callbacks.put("referencedCallback", Map.of("$ref", "#/components/callbacks/ReusableCallback"));

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("responses", Map.of("204", Map.of("description", "Done.")));
        operation.put("callbacks", callbacks);

        Map<String, Object> pathItem = new LinkedHashMap<>();
        pathItem.put("get", operation);

        Map<String, Object> pathsSource = new LinkedHashMap<>();
        pathsSource.put("x-gateway-root", true);
        pathsSource.put("x-gateway-object", Map.of("stage", "prod"));
        pathsSource.put("/callback", pathItem);

        Map<String, Object> flowsSource = new LinkedHashMap<>();
        flowsSource.put("x-flow-scalar", "keep");
        flowsSource.put("x-flow-object", Map.of("enabled", true));
        flowsSource.put("clientCredentials", Map.of(
                "tokenUrl", "https://idp.example.com/token",
                "scopes", Map.of()));

        Map<String, Object> securityScheme = new LinkedHashMap<>();
        securityScheme.put("type", "oauth2");
        securityScheme.put("flows", flowsSource);

        Map<String, Object> documentSource = new LinkedHashMap<>();
        documentSource.put("openapi", "3.0.3");
        documentSource.put("info", Map.of(
                "title", "Static API",
                "version", "1.0.0"));
        documentSource.put("paths", pathsSource);
        documentSource.put("components", Map.of(
                "callbacks", Map.of("ReusableCallback", callback),
                "securitySchemes", Map.of(
                        "OAuth", securityScheme)));

        OpenApiDocument document = OpenApi30DocumentMapper.parse(documentSource);
        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");
        Map<String, Object> paths = map(rendered, "paths");
        Map<String, Object> renderedCallbacks = map(map(map(paths, "/callback"), "get"), "callbacks");
        Map<String, Object> renderedCallback = map(renderedCallbacks, "onEvent");
        Map<String, Object> componentCallback = map(map(map(rendered, "components"), "callbacks"), "ReusableCallback");
        Map<String, Object> flows = map(map(map(map(rendered, "components"), "securitySchemes"), "OAuth"), "flows");

        assertThat(document.paths().containsKey("x-gateway-object"), is(false));
        assertThat(document.paths().get("/callback").operations().get("get").callbacks().get("onEvent")
                           .expressions().containsKey("{$request.body#/callbackUrl}"), is(true));
        assertThat(document.paths().get("/callback").operations().get("get").callbacks()
                           .containsKey("x-named-callback"), is(true));
        assertThat(paths.get("x-gateway-root"), is(true));
        assertThat(map(paths, "x-gateway-object").get("stage"), is("prod"));
        assertThat(renderedCallback.get("x-callback-scalar"), is("keep"));
        assertThat(map(renderedCallback, "x-callback-object").get("enabled"), is(true));
        assertThat(map(renderedCallback, "{$request.body#/callbackUrl}").containsKey("post"), is(true));
        assertThat(map(renderedCallbacks, "x-named-callback").containsKey("{$request.body#/fallbackUrl}"), is(true));
        assertThat(map(renderedCallbacks, "referencedCallback").get("$ref"),
                   is("#/components/callbacks/ReusableCallback"));
        assertThat(componentCallback.containsKey("{$request.body#/callbackUrl}"), is(true));
        assertThat(flows.get("x-flow-scalar"), is("keep"));
        assertThat(map(flows, "x-flow-object").get("enabled"), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesHighLevelStaticDocumentExtensions() {
        OpenApiDocument document = OpenApi30DocumentMapper.parse(Map.of(
                "openapi", "3.0.3",
                "info", Map.of(
                        "title", "Static API",
                        "version", "1.0.0",
                        "contact", Map.of(
                                "name", "API Team",
                                "x-contact", "keep"),
                        "license", Map.of(
                                "name", "Apache-2.0",
                                "x-license", "keep")),
                "externalDocs", Map.of(
                        "url", "https://api.example.com/docs",
                        "x-external-docs", "keep"),
                "servers", List.of(Map.of(
                        "url", "https://api.example.com",
                        "variables", Map.of(
                                "region", Map.of(
                                        "default", "us",
                                        "x-server-variable", "keep")),
                        "x-server", "keep")),
                "tags", List.of(Map.of(
                        "name", "pets",
                        "externalDocs", Map.of(
                                "url", "https://api.example.com/tags/pets",
                                "x-tag-docs", "keep"),
                        "x-tag", "keep")),
                "paths", Map.of()));
        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");
        Map<String, Object> info = map(rendered, "info");
        Map<String, Object> server = ((List<Map<String, Object>>) rendered.get("servers")).getFirst();
        Map<String, Object> serverVariable = map(map(server, "variables"), "region");
        Map<String, Object> tag = ((List<Map<String, Object>>) rendered.get("tags")).getFirst();

        assertThat(map(info, "contact").get("x-contact"), is("keep"));
        assertThat(map(info, "license").get("x-license"), is("keep"));
        assertThat(map(rendered, "externalDocs").get("x-external-docs"), is("keep"));
        assertThat(server.get("x-server"), is("keep"));
        assertThat(serverVariable.get("x-server-variable"), is("keep"));
        assertThat(tag.get("x-tag"), is("keep"));
        assertThat(map(tag, "externalDocs").get("x-tag-docs"), is("keep"));
    }

    @Test
    void openApi30RenderFiltersExampleFields() {
        Map<String, Object> examples = examples(OpenApi30DocumentMapper.render(documentWithExamples(), "3.0.3"));

        assertExampleFields(examples, "valueExample", Set.of("summary", "value"));
        assertExampleFields(examples, "externalExample", Set.of("summary", "externalValue"));
        assertExampleFields(examples, "dataSerializedExample", Set.of("summary"));
        assertExampleFields(examples, "dataExternalExample", Set.of("summary", "externalValue"));
    }

    @Test
    void openApi31RenderFiltersExampleFields() {
        Map<String, Object> examples = examples(render3x(documentWithExamples(),
                                                         exampleRules("3.1.0",
                                                                      Set.of("summary",
                                                                             "description",
                                                                             "value",
                                                                             "externalValue"))));

        assertExampleFields(examples, "valueExample", Set.of("summary", "value"));
        assertExampleFields(examples, "externalExample", Set.of("summary", "externalValue"));
        assertExampleFields(examples, "dataSerializedExample", Set.of("summary"));
        assertExampleFields(examples, "dataExternalExample", Set.of("summary", "externalValue"));
    }

    @Test
    void openApi32RenderPreservesExampleFields() {
        Map<String, Object> examples = examples(render3x(documentWithExamples(),
                                                         exampleRules("3.2.0",
                                                                      Set.of("summary",
                                                                             "description",
                                                                             "value",
                                                                             "dataValue",
                                                                             "serializedValue",
                                                                             "externalValue"))));

        assertExampleFields(examples, "valueExample", Set.of("summary", "value"));
        assertExampleFields(examples, "externalExample", Set.of("summary", "externalValue"));
        assertExampleFields(examples, "dataSerializedExample", Set.of("summary", "dataValue", "serializedValue"));
        assertExampleFields(examples, "dataExternalExample", Set.of("summary", "dataValue", "externalValue"));
    }

    @Test
    void openApi30RenderPreservesNullConstAsEnum() {
        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .components(components -> components.schema("NullConst",
                                                            JsonObject.builder()
                                                                    .setNull("const")
                                                                    .build()))
                .build();

        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");
        Map<String, Object> schema = schema(rendered, "NullConst");
        List<?> values = (List<?>) schema.get("enum");

        assertThat(values.size(), is(1));
        assertThat(values.getFirst(), is((Object) null));
    }

    @Test
    void openApi30RenderPreservesNullOnlyTypeAsNullableSchema() {
        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .components(components -> components.schema("NullOnly",
                                                            JsonObject.builder()
                                                                    .set("type", "null")
                                                                    .build()))
                .build();

        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");
        Map<String, Object> schema = schema(rendered, "NullOnly");
        List<?> values = (List<?>) schema.get("enum");

        assertThat(schema.get("type"), is("object"));
        assertThat(schema.get("nullable"), is(true));
        assertThat(values.size(), is(1));
        assertThat(values.getFirst(), is((Object) null));
    }

    @Test
    void openApi30RenderPreservesNullableNullOnlyEnumAsNullableSchema() {
        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .components(components -> components.schema("NullOnlyEnum",
                                                            JsonObject.builder()
                                                                    .setValues("type", List.of(
                                                                            JsonString.create("string"),
                                                                            JsonString.create("null")))
                                                                    .setValues("enum", List.of(JsonNull.instance()))
                                                                    .build()))
                .build();

        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");
        Map<String, Object> schema = schema(rendered, "NullOnlyEnum");
        List<?> values = (List<?>) schema.get("enum");

        assertThat(schema.get("type"), is("object"));
        assertThat(schema.get("nullable"), is(true));
        assertThat(values.size(), is(1));
        assertThat(values.getFirst(), is((Object) null));
    }

    @Test
    void filtersInfoContactFields() {
        OpenApiDocument document = OpenApi30DocumentMapper.parse(Map.of(
                "openapi", "3.0.3",
                "info", Map.of(
                        "title", "Static API",
                        "version", "1.0.0",
                        "contact", Map.of(
                                "name", "API Team",
                                "url", "https://api.example.com",
                                "email", "api@example.com",
                                "identifier", "unsupported"))));

        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");
        Map<String, Object> contact = map(map(rendered, "info"), "contact");

        assertThat(contact.get("name"), is("API Team"));
        assertThat(contact.get("url"), is("https://api.example.com"));
        assertThat(contact.get("email"), is("api@example.com"));
        assertThat(contact.containsKey("identifier"), is(false));
    }

    @Test
    void openApi30PreservesMediaTypeEncodingMap() {
        OpenApiDocument document = OpenApi30DocumentMapper.parse(documentWithEncoding("3.0.3"));
        Map<String, Object> rendered = OpenApi30DocumentMapper.render(document, "3.0.3");
        Map<String, Object> encoding = encoding(rendered);

        assertThat(map(encoding, "profileImage").get("contentType"), is("image/png"));
        assertThat(map(map(encoding, "profileImage"), "headers").containsKey("X-Image-Name"), is(true));
    }

    @Test
    void openApi30RejectsMutualTlsSecurityScheme() {
        OpenApiDocument document = openApiDocument(documentWithSecurityScheme(mutualTlsSecurityScheme()));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                   () -> OpenApi30DocumentMapper.render(document, "3.0.3"));

        assertThat(thrown.getMessage(), containsString("mutualTLS"));
    }

    @Test
    void openApi30RejectsDeviceAuthorizationFlow() {
        OpenApiDocument document = openApiDocument(documentWithSecurityScheme(deviceAuthorizationSecurityScheme()));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                   () -> OpenApi30DocumentMapper.render(document, "3.0.3"));

        assertThat(thrown.getMessage(), containsString("deviceAuthorization"));
    }

    private static JsonObject schema(String firstBound, int firstValue, String secondBound, int secondValue) {
        return JsonObject.builder()
                .set("type", "number")
                .set(firstBound, firstValue)
                .set(secondBound, secondValue)
                .build();
    }

    private static Map<String, ?> document(String version) {
        return document(version, Map.of("type", "integer",
                                       "format", "int64",
                                       "default", LARGE_INTEGRAL_VALUE),
                        Map.of("type", "string"));
    }

    private static Map<String, Object> documentWithOperation(String version, Map<String, Object> operation) {
        return Map.of("openapi", version,
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "paths", Map.of("/items", Map.of("get", operation)));
    }

    private static Map<String, ?> document(String version, Map<String, Object> bounded, Map<String, Object> inclusive) {
        return Map.of("openapi", version,
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "components", Map.of("schemas", Map.of("StaticItem", Map.of(
                              "type", "object",
                              "properties", Map.of("large", Map.of(
                                      "type", "integer",
                                      "format", "int64",
                                      "default", LARGE_INTEGRAL_VALUE),
                                                   "bounded", bounded,
                                                   "inclusive", inclusive)))));
    }

    private static Map<String, Object> documentWithNullExtension(String version) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openapi", version);
        result.put("info", Map.of("title", "Static API",
                                  "version", "1.0.0"));
        result.put("x-null", null);
        return result;
    }

    private static Map<String, Object> documentWithEncoding(String version) {
        return Map.of("openapi", version,
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "paths", Map.of("/upload", Map.of("post", Map.of(
                              "requestBody", Map.of("content", Map.of("multipart/form-data", Map.of(
                                      "schema", Map.of("type", "object"),
                                      "encoding", Map.of("profileImage", Map.of(
                                              "contentType", "image/png",
                                              "headers", Map.of("X-Image-Name", Map.of("description", "Image name"))))))),
                              "responses", Map.of("204", Map.of("description", "Done."))))));
    }

    private static OpenApiDocument openApiDocument(Map<String, Object> document) {
        return OpenApiDocumentReader.read(OpenApiDocumentMapperSupport.jsonObject(document));
    }

    private static OpenApiDocument documentWithExamples() {
        OpenApiDocument.Example value = OpenApiDocument.Example.builder()
                .summary("Value")
                .value(JsonString.create("one"))
                .build();
        OpenApiDocument.Example external = OpenApiDocument.Example.builder()
                .summary("External")
                .externalValue("examples/external.json")
                .build();
        OpenApiDocument.Example dataSerialized = OpenApiDocument.Example.builder()
                .summary("Data serialized")
                .dataValue(exampleData("serialized"))
                .serializedValue("{\"kind\":\"serialized\"}")
                .build();
        OpenApiDocument.Example dataExternal = OpenApiDocument.Example.builder()
                .summary("Data external")
                .dataValue(exampleData("external"))
                .externalValue("examples/data.json")
                .build();
        OpenApiDocument.Response response = OpenApiDocument.Response.builder()
                .description("OK")
                .content("application/json", content -> content
                        .example("valueExample", value)
                        .example("externalExample", external)
                        .example("dataSerializedExample", dataSerialized)
                        .example("dataExternalExample", dataExternal))
                .build();

        return OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/examples", path -> path.operation("GET",
                                                           operation -> operation.response("200",
                                                                                           response)))
                .build();
    }

    private static JsonObject exampleData(String kind) {
        return JsonObject.builder()
                .set("kind", kind)
                .build();
    }

    private static Map<String, Object> render3x(OpenApiDocument document, OpenApi3xMapperRules rules) {
        return OpenApiDocumentMapperSupport.document3x(OpenApiDocumentMapperSupport.objectMap(document.toJsonObject()),
                                                       rules);
    }

    private static OpenApi3xMapperRules exampleRules(String targetVersion, Set<String> exampleFields) {
        return OpenApi3xMapperRules.builder()
                .targetVersion(targetVersion)
                .addDocumentFields(Set.of("paths"))
                .addPathItemFields(Set.of("get"))
                .addFixedPathOperationFields(Set.of("get"))
                .addOperationFields(Set.of("responses"))
                .addResponseFields(Set.of("description", "content"))
                .addMediaTypeFields(Set.of("examples"))
                .addExampleFields(exampleFields)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> examples(Map<String, Object> document) {
        Object examples = map(map(map(map(map(map(map(document, "paths"), "/examples"), "get"), "responses"), "200"),
                              "content"),
                              "application/json")
                .get("examples");
        if (examples instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static void assertExampleFields(Map<String, Object> examples, String name, Set<String> fields) {
        assertThat(map(examples, name).keySet(), is(fields));
    }

    private static Map<String, Object> documentWithSecurityScheme(Map<String, Object> securityScheme) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openapi", "3.2.0");
        result.put("info", Map.of("title", "Static API",
                                  "version", "1.0.0"));
        result.put("components", Map.of("securitySchemes", Map.of("test", securityScheme)));
        return result;
    }

    private static Map<String, Object> mutualTlsSecurityScheme() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "mutualTLS");
        return result;
    }

    private static Map<String, Object> deviceAuthorizationSecurityScheme() {
        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("deviceAuthorizationUrl", "https://idp.example.com/device");
        flow.put("tokenUrl", "https://idp.example.com/token");
        flow.put("scopes", Map.of());

        Map<String, Object> flows = new LinkedHashMap<>();
        flows.put("deviceAuthorization", flow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "oauth2");
        result.put("flows", flows);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperty(Map<String, Object> document, String propertyName) {
        return (Map<String, Object>) map(map(map(map(document, "components"), "schemas"), "StaticItem"), "properties")
                .get(propertyName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(Map<String, Object> document, String schemaName) {
        return (Map<String, Object>) map(map(document, "components"), "schemas").get(schemaName);
    }

    private static Map<String, Object> encoding(Map<String, Object> document) {
        return map(map(map(map(map(map(map(document, "paths"), "/upload"), "post"),
                           "requestBody"), "content"), "multipart/form-data"), "encoding");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String content) {
        return new Yaml().load(content);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<?, ?> map, String name) {
        return (Map<String, Object>) map.get(name);
    }
}
