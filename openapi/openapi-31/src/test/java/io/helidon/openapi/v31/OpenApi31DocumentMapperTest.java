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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.json.JsonString;
import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.v30.OpenApiDocumentMapperSupport;
import io.helidon.openapi.v30.OpenApiDocumentReader;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApi31DocumentMapperTest {
    private static final long LARGE_INTEGRAL_VALUE = 9_007_199_254_740_993L;

    @Test
    void validatesOpenApiVersion() {
        OpenApi31DocumentMapper.parse(document("3.1.2-rc1"));

        for (String invalidVersion : List.of("3.1", "3.1.", "3.1.not-a-version", "3.1.1-", "3.1.1.0")) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi31DocumentMapper.parse(document(invalidVersion)),
                                                    invalidVersion);
            assertThat(invalidVersion, ex.getMessage(), containsString(invalidVersion));
        }
    }

    @Test
    void requiresPathParametersForTemplateExpressions() {
        String path = "/items/{id}";
        Map<String, Object> missing = documentWithPathItem(path, Map.of(
                "get", Map.of("responses", Map.of("200", Map.of("description", "OK")))));

        IllegalStateException parsed = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi31DocumentMapper.parse(missing));
        assertThat(parsed.getMessage(), containsString(path));
        assertThat(parsed.getMessage(), containsString("template expression {id}"));

        IllegalStateException rendered = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.render(openApiDocument(missing), "3.1.1"));
        assertThat(rendered.getMessage(), containsString(path));
        assertThat(rendered.getMessage(), containsString("template expression {id}"));

        Map<String, Object> pathLevel = documentWithPathItem(path, Map.of(
                "parameters", List.of(pathParameter("id")),
                "get", Map.of("responses", Map.of("200", Map.of("description", "OK")))));
        OpenApi31DocumentMapper.render(OpenApi31DocumentMapper.parse(pathLevel), "3.1.1");

        Map<String, Object> operationLevel = documentWithPathItem(path, Map.of(
                "get", Map.of(
                        "parameters", List.of(pathParameter("id")),
                        "responses", Map.of("200", Map.of("description", "OK")))));
        OpenApi31DocumentMapper.render(OpenApi31DocumentMapper.parse(operationLevel), "3.1.1");
    }

    @Test
    void validatesParameterListUniqueness() {
        String path = "/items/{id}";
        Map<String, Object> parameter = pathParameter("id");
        Map<String, Object> duplicate = documentWithPathItem(path, Map.of(
                "get", Map.of(
                        "parameters", List.of(parameter, parameter),
                        "responses", Map.of("200", Map.of("description", "OK")))));

        IllegalStateException parsed = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi31DocumentMapper.parse(duplicate));
        assertThat(parsed.getMessage(), containsString("duplicate path parameter id"));

        IllegalStateException rendered = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.render(openApiDocument(duplicate), "3.1.1"));
        assertThat(rendered.getMessage(), containsString("duplicate path parameter id"));

        Map<String, Object> override = documentWithPathItem(path, Map.of(
                "parameters", List.of(parameter),
                "get", Map.of(
                        "parameters", List.of(parameter),
                        "responses", Map.of("200", Map.of("description", "OK")))));
        OpenApi31DocumentMapper.render(OpenApi31DocumentMapper.parse(override), "3.1.1");
    }

    @Test
    void validatesSchemaReferenceSiblings() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                     () -> OpenApi31DocumentMapper.parse(Map.of(
                                                             "openapi", "3.1.1",
                                                             "info", Map.of("title", "Static API",
                                                                            "version", "1.0.0"),
                                                             "paths", Map.of(),
                                                             "components", Map.of("schemas", Map.of(
                                                                     "Referenced", Map.of(
                                                                             "$ref", "#/components/schemas/Base",
                                                                             "properties", Map.of(
                                                                                     "invalid", "not a schema")))))));

        assertThat(thrown.getMessage(), containsString("components.schemas.Referenced.properties.invalid"));
    }

    @Test
    void handlesVersionSpecificResponseRequirements() {
        IllegalStateException missingResponses = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.parse(documentWithOperation("3.1.1", Map.of("summary", "Items"))));
        assertThat(missingResponses.getMessage(), containsString("responses"));

        for (Map<String, Object> responses : List.<Map<String, Object>>of(
                Map.of(),
                Map.of("x-note", "No response code"))) {
            IllegalStateException missingResponseCode = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi31DocumentMapper.parse(documentWithOperation(
                            "3.1.1",
                            Map.of("responses", responses))));
            assertThat(missingResponseCode.getMessage(), containsString("response code"));
        }

        IllegalStateException missingDescription = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.parse(documentWithOperation(
                        "3.1.1",
                        Map.of("responses", Map.of("200", Map.of("headers", Map.of()))))));
        assertThat(missingDescription.getMessage(), containsString("description"));

        IllegalStateException invalidResponseKey = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.parse(documentWithOperation(
                        "3.1.1",
                        Map.of("responses", Map.of(
                                "200", Map.of("description", "OK"),
                                "bogus", Map.of("description", "Invalid"))))));
        assertThat(invalidResponseKey.getMessage(), containsString("3.1"));
        assertThat(invalidResponseKey.getMessage(), containsString("bogus"));

        OpenApiDocument operationWithoutResponses = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.operation("GET", operation -> operation.summary("Items")))
                .build();
        IllegalStateException renderedWithoutResponses = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.render(operationWithoutResponses, "3.1.1"));
        assertThat(renderedWithoutResponses.getMessage(), containsString("responses"));

        OpenApiDocument responsesWithoutCode = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.operation(
                        "GET",
                        operation -> operation.responseExtension("x-note", JsonString.create("No response code"))))
                .build();
        IllegalStateException renderedWithoutResponseCode = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.render(responsesWithoutCode, "3.1.1"));
        assertThat(renderedWithoutResponseCode.getMessage(), containsString("response code"));

        OpenApiDocument responseWithoutDescription = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.operation(
                        "GET",
                        operation -> operation.response("200", response -> response.summary("Items"))))
                .build();
        IllegalStateException renderedWithoutDescription = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.render(responseWithoutDescription, "3.1.1"));
        assertThat(renderedWithoutDescription.getMessage(), containsString("description"));

        OpenApiDocument responseWithInvalidKey = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.operation(
                        "GET",
                        operation -> operation.response("200", "OK").response("bogus", "Invalid")))
                .build();
        IllegalStateException renderedWithInvalidResponseKey = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.render(responseWithInvalidKey, "3.1.1"));
        assertThat(renderedWithInvalidResponseKey.getMessage(), containsString("3.1"));
        assertThat(renderedWithInvalidResponseKey.getMessage(), containsString("bogus"));

        for (String description : List.of("", " ")) {
            OpenApiDocument document = OpenApi31DocumentMapper.parse(documentWithOperation(
                    "3.1.1",
                    Map.of("responses", Map.of("200", Map.of("description", description)))));
            Map<String, Object> rendered = OpenApi31DocumentMapper.render(document, "3.1.1");
            Map<String, Object> response = map(map(map(map(rendered, "paths"), "/items"), "get"), "responses");

            assertThat(map(response, "200").get("description"), is(description));
        }
    }

    @Test
    void rejectsMalformedSecurityRequirements() {
        Map<String, Object> invalidSecurityValues = new LinkedHashMap<>();
        invalidSecurityValues.put("security value is not an array", Map.of("OAuth", List.of()));
        invalidSecurityValues.put("security requirement is not an object", List.of("OAuth"));
        invalidSecurityValues.put("scheme scopes are not an array", List.of(Map.of("OAuth", "read")));
        invalidSecurityValues.put("scheme scope is not a string", List.of(Map.of("OAuth", List.of("read", 42))));

        invalidSecurityValues.forEach((description, invalidSecurity) -> {
            Map<String, Object> topLevelDocument = new LinkedHashMap<>(document("3.1.1"));
            topLevelDocument.put("security", invalidSecurity);
            IllegalStateException topLevel = assertThrows(IllegalStateException.class,
                                                          () -> OpenApi31DocumentMapper.parse(topLevelDocument),
                                                          description + " at document level");
            assertThat(description, topLevel.getMessage(), containsString("security"));

            Map<String, Object> operationDocument = new LinkedHashMap<>(document("3.1.1"));
            operationDocument.put("paths", Map.of(
                    "/items", Map.of(
                            "get", Map.of(
                                    "responses", Map.of("200", Map.of("description", "OK")),
                                    "security", invalidSecurity))));
            IllegalStateException operation = assertThrows(IllegalStateException.class,
                                                           () -> OpenApi31DocumentMapper.parse(operationDocument),
                                                           description + " at operation level");
            assertThat(description, operation.getMessage(), containsString("security"));
        });
    }

    @Test
    void rejectsUndeclaredSecurityRequirementSchemes() {
        Map<String, Map<String, Object>> invalidDocuments = new LinkedHashMap<>();
        Map<String, Object> documentSecurity = new LinkedHashMap<>(document("3.1.1"));
        documentSecurity.put("components", Map.of("securitySchemes", Map.of()));
        documentSecurity.put("security", List.of(Map.of("missingAuth", List.of())));
        invalidDocuments.put("document", documentSecurity);

        Map<String, Object> operationSecurity = new LinkedHashMap<>(document("3.1.1"));
        operationSecurity.put("components", Map.of("securitySchemes", Map.of()));
        operationSecurity.put("paths", Map.of(
                "/items", Map.of(
                        "get", Map.of(
                                "responses", Map.of("200", Map.of("description", "OK")),
                                "security", List.of(Map.of("missingAuth", List.of()))))));
        invalidDocuments.put("operation", operationSecurity);

        invalidDocuments.forEach((location, source) -> {
            IllegalStateException parsed = assertThrows(IllegalStateException.class,
                                                        () -> OpenApi31DocumentMapper.parse(source),
                                                        location + " parsing");
            assertThat(parsed.getMessage(), containsString("undeclared security scheme missingAuth"));

            IllegalStateException rendered = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi31DocumentMapper.render(openApiDocument(source), "3.1.1"),
                    location + " rendering");
            assertThat(rendered.getMessage(), containsString("undeclared security scheme missingAuth"));
        });
    }

    @Test
    void preservesLargeIntegralNumbers() {
        OpenApiDocument document = OpenApi31DocumentMapper.parse(document("3.1.0"));
        Map<String, Object> rendered = OpenApi31DocumentMapper.render(document, "3.1.1");

        assertThat(String.valueOf(schemaProperty(rendered, "large").get("default")), is(String.valueOf(LARGE_INTEGRAL_VALUE)));
    }

    @Test
    void preservesNullExtensionValues() {
        OpenApiDocument document = OpenApi31DocumentMapper.parse(documentWithNullExtension("3.1.0"));
        Map<String, Object> rendered = OpenApi31DocumentMapper.render(document, "3.1.1");

        assertThat(rendered.containsKey("x-null"), is(true));
        assertThat(rendered.get("x-null"), is((Object) null));
    }

    @Test
    void filtersUnsupportedHeaderFields() {
        OpenApiDocument document = OpenApi31DocumentMapper.parse(Map.of(
                "openapi", "3.1.1",
                "info", Map.of(
                        "title", "Static API",
                        "version", "1.0.0"),
                "paths", Map.of(
                        "/items", Map.of(
                                "get", Map.of(
                                        "responses", Map.of(
                                                "200", Map.of(
                                                        "description", "OK",
                                                        "headers", Map.of(
                                                                "X-Test", Map.of(
                                                                        "allowEmptyValue", true,
                                                                        "allowReserved", true,
                                                                        "schema", Map.of("type", "string"))))))))));

        Map<String, Object> rendered = OpenApi31DocumentMapper.render(document, "3.1.1");
        Map<String, Object> responses = map(map(map(map(rendered, "paths"), "/items"), "get"), "responses");
        Map<String, Object> header = map(map(map(responses, "200"), "headers"), "X-Test");

        assertThat(header.containsKey("allowEmptyValue"), is(false));
        assertThat(header.containsKey("allowReserved"), is(false));
    }

    @Test
    void openApi31PreservesResponseAndComponentPathItemExtensions() {
        OpenApiDocument document = OpenApi31DocumentMapper.parse(Map.of(
                "openapi", "3.1.0",
                "info", Map.of(
                        "title", "Static API",
                        "version", "1.0.0"),
                "paths", Map.of(
                        "x-gateway-root", true,
                        "x-gateway-object", Map.of("stage", "prod"),
                        "/pets", Map.of(
                                "x-path-meta", "keep",
                                "get", Map.of(
                                        "responses", Map.of(
                                                "x-provider-meta", true,
                                                "x-provider-object", Map.of("enabled", true),
                                                "200", Map.of("description", "OK")),
                                        "callbacks", Map.of(
                                                "onEvent", Map.of(
                                                        "x-callback-scalar", "keep",
                                                        "x-callback-object", Map.of("enabled", true),
                                                        "{$request.body#/callbackUrl}", Map.of(
                                                                "post", Map.of(
                                                                        "responses", Map.of(
                                                                                "200", Map.of("description", "OK"))))),
                                                "x-named-callback", Map.of(
                                                        "{$request.body#/fallbackUrl}", Map.of(
                                                                "post", Map.of(
                                                                        "responses", Map.of(
                                                                                "204", Map.of("description", "Done"))))),
                                                "referencedCallback", Map.of(
                                                        "$ref", "#/components/callbacks/ReusableCallback",
                                                        "summary", "Reusable callback"))))),
                "components", Map.of(
                        "callbacks", Map.of(
                                "ReusableCallback", Map.of(
                                        "{$request.body#/componentUrl}", Map.of(
                                                "post", Map.of(
                                                        "responses", Map.of(
                                                                "200", Map.of("description", "OK")))))),
                        "responses", Map.of(
                                "x-Problem", Map.of(
                                        "description", "Problem details",
                                        "summary", "OpenAPI 3.2 summary",
                                        "x-response", "preserved")),
                        "securitySchemes", Map.of(
                                "OAuth", Map.of(
                                        "type", "oauth2",
                                        "flows", Map.of(
                                                "x-flow-scalar", "keep",
                                                "x-flow-object", Map.of("enabled", true),
                                                "clientCredentials", Map.of(
                                                        "tokenUrl", "https://idp.example.com/token",
                                                        "scopes", Map.of())))),
                        "pathItems", Map.of(
                                "ReusablePath", Map.of(
                                        "get", Map.of(
                                                "responses", Map.of(
                                                        "200", Map.of("description", "OK"))),
                                        "x-component-path-item", "keep")))));
        Map<String, Object> rendered = OpenApi31DocumentMapper.render(document, "3.1.1");
        Map<String, Object> path = map(map(rendered, "paths"), "/pets");
        Map<String, Object> responses = map(map(path, "get"), "responses");
        Map<String, Object> callbacks = map(map(path, "get"), "callbacks");
        Map<String, Object> callback = map(callbacks, "onEvent");
        Map<String, Object> componentCallback = map(map(map(rendered, "components"), "callbacks"), "ReusableCallback");
        Map<String, Object> componentResponse = map(map(map(rendered, "components"), "responses"), "x-Problem");
        Map<String, Object> reusablePath = map(map(map(rendered, "components"), "pathItems"), "ReusablePath");
        Map<String, Object> flows = map(map(map(map(rendered, "components"), "securitySchemes"), "OAuth"), "flows");

        assertThat(document.paths().containsKey("x-gateway-object"), is(false));
        assertThat(document.paths().get("/pets").operations().get("get").callbacks().get("onEvent")
                           .expressions().containsKey("{$request.body#/callbackUrl}"), is(true));
        assertThat(document.paths().get("/pets").operations().get("get").callbacks()
                           .containsKey("x-named-callback"), is(true));
        assertThat(map(rendered, "paths").get("x-gateway-root"), is(true));
        assertThat(map(map(rendered, "paths"), "x-gateway-object").get("stage"), is("prod"));
        assertThat(document.paths().get("/pets").operations().get("get").responses().containsKey("x-provider-object"),
                   is(false));
        assertThat(path.get("x-path-meta"), is("keep"));
        assertThat(responses.get("x-provider-meta"), is(true));
        assertThat(map(responses, "x-provider-object").get("enabled"), is(true));
        assertThat(callback.get("x-callback-scalar"), is("keep"));
        assertThat(map(callback, "x-callback-object").get("enabled"), is(true));
        assertThat(map(callback, "{$request.body#/callbackUrl}").containsKey("post"), is(true));
        assertThat(map(callbacks, "x-named-callback").containsKey("{$request.body#/fallbackUrl}"), is(true));
        assertThat(map(callbacks, "referencedCallback").get("$ref"),
                   is("#/components/callbacks/ReusableCallback"));
        assertThat(map(callbacks, "referencedCallback").get("summary"), is("Reusable callback"));
        assertThat(componentCallback.containsKey("{$request.body#/componentUrl}"), is(true));
        assertThat(componentResponse.get("description"), is("Problem details"));
        assertThat(componentResponse.containsKey("summary"), is(false));
        assertThat(componentResponse.get("x-response"), is("preserved"));
        assertThat(flows.get("x-flow-scalar"), is("keep"));
        assertThat(map(flows, "x-flow-object").get("enabled"), is(true));
        assertThat(reusablePath.get("x-component-path-item"), is("keep"));
    }

    @Test
    void openApi31AllowsMutualTlsSecurityScheme() {
        OpenApiDocument document = openApiDocument(documentWithSecurityScheme(mutualTlsSecurityScheme()));
        Map<String, Object> rendered = OpenApi31DocumentMapper.render(document, "3.1.1");

        assertThat(map(securitySchemes(rendered), "test").get("type"), is("mutualTLS"));
    }

    @Test
    void openApi31RejectsDeviceAuthorizationFlow() {
        OpenApiDocument document = openApiDocument(documentWithSecurityScheme(deviceAuthorizationSecurityScheme()));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                   () -> OpenApi31DocumentMapper.render(document, "3.1.1"));

        assertThat(thrown.getMessage(), containsString("deviceAuthorization"));
    }

    @Test
    void openApi31PreservesMediaTypeEncodingMap() {
        OpenApiDocument document = OpenApi31DocumentMapper.parse(documentWithEncoding("3.1.0"));
        Map<String, Object> rendered = OpenApi31DocumentMapper.render(document, "3.1.1");
        Map<String, Object> encoding = encoding(rendered);

        assertThat(map(encoding, "profileImage").get("contentType"), is("image/png"));
        assertThat(map(map(encoding, "profileImage"), "headers").containsKey("X-Image-Name"), is(true));
    }

    @Test
    void openApi31PreservesReferenceSummaryAndDescription() {
        OpenApiDocument document = OpenApi31DocumentMapper.parse(documentWithReferenceObjects("3.1.0"));
        Map<String, Object> rendered = OpenApi31DocumentMapper.render(document, "3.1.1");
        Map<String, Object> components = map(rendered, "components");

        assertReference(map(map(components, "parameters"), "testParameter"));
        assertReference(map(map(components, "headers"), "testHeader"));
        assertReference(map(map(components, "requestBodies"), "testRequestBody"));
        assertReference(map(map(components, "responses"), "testResponse"));
        assertReference(map(map(components, "examples"), "testExample"));
        assertReference(map(map(components, "links"), "testLink"));
        assertReference(map(map(components, "securitySchemes"), "testSecurity"));
        assertThat(map(map(components, "schemas"), "testSchema").get("x-reference"), is("Reference extension"));
    }

    @Test
    void validatesReferenceUris() {
        String malformed = "http://[bad";
        assertInvalidReferenceUri(documentWithExampleReference(malformed), "must be a URI");
        assertInvalidReferenceUri(documentWithSchemaReference(malformed), "must be a URI");

        String ipvFuture = "http://[v1.fe]/description.yaml#/components/examples/Example";
        assertInvalidReferenceUri(documentWithExampleReference(ipvFuture), "IPvFuture host literal");
        assertInvalidReferenceUri(documentWithSchemaReference(ipvFuture), "IPvFuture host literal");

        for (String valid : List.of("https://example.test/openapi.yaml#/components/examples/Example",
                                    "../openapi.yaml#/components/examples/Example",
                                    "#/components/examples/Example",
                                    "other.yaml#anchor")) {
            OpenApiDocument document = OpenApi31DocumentMapper.parse(documentWithExampleReference(valid));
            Map<String, Object> validRendered = OpenApi31DocumentMapper.render(document, "3.1.1");
            assertThat(map(map(map(validRendered, "components"), "examples"), "test").get("$ref"), is(valid));
        }
    }

    private static Map<String, ?> document(String version) {
        return Map.of("openapi", version,
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "components", Map.of("schemas", Map.of("StaticItem", Map.of(
                              "type", "object",
                              "properties", Map.of("large", Map.of(
                                      "type", "integer",
                                      "format", "int64",
                                      "default", LARGE_INTEGRAL_VALUE))))));
    }

    private static Map<String, Object> documentWithOperation(String version, Map<String, Object> operation) {
        return Map.of("openapi", version,
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "paths", Map.of("/items", Map.of("get", operation)));
    }

    private static Map<String, Object> documentWithPathItem(String path, Map<String, Object> pathItem) {
        return Map.of("openapi", "3.1.1",
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "paths", Map.of(path, pathItem));
    }

    private static Map<String, Object> pathParameter(String name) {
        return Map.of("name", name,
                      "in", "path",
                      "required", true,
                      "schema", Map.of("type", "string"));
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
                                              "headers", Map.of("X-Image-Name", Map.of(
                                                      "description", "Image name",
                                                      "schema", Map.of("type", "string")))))))),
                              "responses", Map.of("204", Map.of("description", "Done."))))));
    }

    private static Map<String, Object> documentWithReferenceObjects(String version) {
        return Map.of("openapi", version,
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "components", Map.of(
                              "schemas", Map.of("testSchema", reference("#/components/schemas/real")),
                              "parameters", Map.of("testParameter", reference("#/components/parameters/real")),
                              "headers", Map.of("testHeader", reference("#/components/headers/real")),
                              "requestBodies", Map.of("testRequestBody", reference("#/components/requestBodies/real")),
                              "responses", Map.of("testResponse", reference("#/components/responses/real")),
                              "examples", Map.of("testExample", reference("#/components/examples/real")),
                              "links", Map.of("testLink", reference("#/components/links/real")),
                              "securitySchemes", Map.of("testSecurity", reference("#/components/securitySchemes/real"))));
    }

    private static Map<String, Object> documentWithExampleReference(String reference) {
        return Map.of("openapi", "3.1.1",
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "paths", Map.of(),
                      "components", Map.of("examples", Map.of("test", Map.of("$ref", reference))));
    }

    private static Map<String, Object> documentWithSchemaReference(String reference) {
        return Map.of("openapi", "3.1.1",
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "paths", Map.of(),
                      "components", Map.of("schemas", Map.of("test", Map.of("$ref", reference))));
    }

    private static void assertInvalidReferenceUri(Map<String, Object> source, String expectedMessage) {
        IllegalStateException parsed = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi31DocumentMapper.parse(source));
        assertThat(parsed.getMessage(), containsString(expectedMessage));

        IllegalStateException rendered = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.render(openApiDocument(source), "3.1.1"));
        assertThat(rendered.getMessage(), containsString(expectedMessage));
    }

    private static OpenApiDocument openApiDocument(Map<String, Object> document) {
        return OpenApiDocumentReader.read(OpenApiDocumentMapperSupport.jsonObject(document));
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

    private static Map<String, Object> securitySchemes(Map<String, Object> document) {
        return map(map(document, "components"), "securitySchemes");
    }

    private static Map<String, Object> reference(String ref) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("$ref", ref);
        result.put("summary", "Reference summary");
        result.put("description", "Reference description");
        result.put("x-reference", "Reference extension");
        result.put("additional", "Additional property");
        return result;
    }

    private static void assertReference(Map<String, Object> reference) {
        assertThat(reference.keySet(), is(Set.of("$ref", "summary", "description")));
        assertThat(reference.get("summary"), is("Reference summary"));
        assertThat(reference.get("description"), is("Reference description"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperty(Map<String, Object> document, String propertyName) {
        return (Map<String, Object>) map(map(map(map(document, "components"), "schemas"), "StaticItem"), "properties")
                .get(propertyName);
    }

    private static Map<String, Object> encoding(Map<String, Object> document) {
        return map(map(map(map(map(map(map(document, "paths"), "/upload"), "post"),
                           "requestBody"), "content"), "multipart/form-data"), "encoding");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<?, ?> map, String name) {
        return (Map<String, Object>) map.get(name);
    }
}
