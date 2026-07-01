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
    void handlesVersionSpecificResponseRequirements() {
        IllegalStateException missingResponses = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.parse(documentWithOperation("3.1.1", Map.of("summary", "Items"))));
        assertThat(missingResponses.getMessage(), containsString("responses"));

        IllegalStateException missingDescription = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.parse(documentWithOperation(
                        "3.1.1",
                        Map.of("responses", Map.of("200", Map.of("headers", Map.of()))))));
        assertThat(missingDescription.getMessage(), containsString("description"));

        OpenApiDocument operationWithoutResponses = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.operation("GET", operation -> operation.summary("Items")))
                .build();
        IllegalStateException renderedWithoutResponses = assertThrows(
                IllegalStateException.class,
                () -> OpenApi31DocumentMapper.render(operationWithoutResponses, "3.1.1"));
        assertThat(renderedWithoutResponses.getMessage(), containsString("responses"));

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

    private static Map<String, Object> documentWithReferenceObjects(String version) {
        return Map.of("openapi", version,
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "components", Map.of(
                              "parameters", Map.of("testParameter", reference("#/components/parameters/real")),
                              "headers", Map.of("testHeader", reference("#/components/headers/real")),
                              "requestBodies", Map.of("testRequestBody", reference("#/components/requestBodies/real")),
                              "responses", Map.of("testResponse", reference("#/components/responses/real")),
                              "examples", Map.of("testExample", reference("#/components/examples/real")),
                              "links", Map.of("testLink", reference("#/components/links/real")),
                              "securitySchemes", Map.of("testSecurity", reference("#/components/securitySchemes/real"))));
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
        return result;
    }

    private static void assertReference(Map<String, Object> reference) {
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
