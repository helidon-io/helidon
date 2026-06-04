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
