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
import java.util.Optional;
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

class OpenApi32DocumentMapperTest {
    private static final long LARGE_INTEGRAL_VALUE = 9_007_199_254_740_993L;

    @Test
    void validatesOpenApiVersion() {
        OpenApi32DocumentMapper.parse(document("3.2.0-beta"));

        for (String invalidVersion : List.of("3.2", "3.2.", "3.2.not-a-version", "3.2.1-", "3.2.1.0")) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi32DocumentMapper.parse(document(invalidVersion)),
                                                    invalidVersion);
            assertThat(invalidVersion, ex.getMessage(), containsString(invalidVersion));
        }
    }

    @Test
    void handlesVersionSpecificResponseRequirements() {
        for (Map<String, Object> responses : List.<Map<String, Object>>of(
                Map.of(),
                Map.of("x-note", "No response code"))) {
            IllegalStateException missingResponseCode = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi32DocumentMapper.parse(Map.of(
                            "openapi", "3.2.0",
                            "info", Map.of(
                                    "title", "Static API",
                                    "version", "1.0.0"),
                            "paths", Map.of(
                                    "/items", Map.of(
                                            "get", Map.of("responses", responses))))));
            assertThat(missingResponseCode.getMessage(), containsString("response code"));
        }

        OpenApiDocument document = OpenApi32DocumentMapper.parse(Map.of(
                "openapi", "3.2.0",
                "info", Map.of(
                        "title", "Static API",
                        "version", "1.0.0"),
                "paths", Map.of(
                        "/without-responses", Map.of(
                                "get", Map.of("summary", "Items")),
                        "/without-description", Map.of(
                                "get", Map.of(
                                        "responses", Map.of(
                                                "200", Map.of("summary", "Items")))),
                        "/empty-description", Map.of(
                                "get", Map.of(
                                        "responses", Map.of(
                                                "200", Map.of("description", "")))))));

        OpenApiDocument.Response omittedDescription = document.paths()
                .get("/without-description")
                .operations()
                .get("get")
                .responses()
                .get("200");
        OpenApiDocument.Response emptyDescription = document.paths()
                .get("/empty-description")
                .operations()
                .get("get")
                .responses()
                .get("200");

        Map<String, Object> rendered = OpenApi32DocumentMapper.render(document, "3.2.0");
        Map<String, Object> paths = map(rendered, "paths");
        Map<String, Object> withoutResponses = map(map(paths, "/without-responses"), "get");
        Map<String, Object> response = map(map(map(paths, "/without-description"), "get"), "responses");

        assertThat(withoutResponses.containsKey("responses"), is(false));
        assertThat(map(response, "200").get("summary"), is("Items"));
        assertThat(map(response, "200").containsKey("description"), is(false));
        assertThat(omittedDescription.description(), is(Optional.empty()));
        assertThat(emptyDescription.description(), is(Optional.of("")));

        OpenApiDocument responsesWithoutCode = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.operation(
                        "GET",
                        operation -> operation.responseExtension("x-note", JsonString.create("No response code"))))
                .build();
        IllegalStateException renderedWithoutResponseCode = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(responsesWithoutCode, "3.2.0"));
        assertThat(renderedWithoutResponseCode.getMessage(), containsString("response code"));
    }

    @Test
    void preservesLargeIntegralNumbers() {
        OpenApiDocument document = OpenApi32DocumentMapper.parse(document("3.2.0"));
        Map<String, Object> rendered = OpenApi32DocumentMapper.render(document, "3.2.0");

        assertThat(String.valueOf(schemaProperty(rendered, "large").get("default")), is(String.valueOf(LARGE_INTEGRAL_VALUE)));
    }

    @Test
    void filtersReferenceObjectFields() {
        Map<String, Object> reference = Map.of(
                "$ref", "#/components/responses/real",
                "summary", "Reference summary",
                "description", "Reference description",
                "x-reference", "Reference extension",
                "additional", "Additional property");
        OpenApiDocument document = OpenApi32DocumentMapper.parse(Map.of(
                "openapi", "3.2.0",
                "info", Map.of(
                        "title", "Static API",
                        "version", "1.0.0"),
                "components", Map.of(
                        "schemas", Map.of("testSchema", reference),
                        "responses", Map.of("testResponse", reference))));

        Map<String, Object> rendered = OpenApi32DocumentMapper.render(document, "3.2.0");
        Map<String, Object> components = map(rendered, "components");
        Map<String, Object> responseReference = map(map(components, "responses"), "testResponse");

        assertThat(responseReference.keySet(), is(Set.of("$ref", "summary", "description")));
        assertThat(map(map(components, "schemas"), "testSchema").get("x-reference"), is("Reference extension"));
    }

    @Test
    void preservesNullExtensionValues() {
        OpenApiDocument document = OpenApi32DocumentMapper.parse(documentWithNullExtension("3.2.0"));
        Map<String, Object> rendered = OpenApi32DocumentMapper.render(document, "3.2.0");

        assertThat(rendered.containsKey("x-null"), is(true));
        assertThat(rendered.get("x-null"), is((Object) null));
    }

    @Test
    void filtersUnsupportedHeaderFields() {
        OpenApiDocument document = OpenApi32DocumentMapper.parse(Map.of(
                "openapi", "3.2.0",
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

        Map<String, Object> rendered = OpenApi32DocumentMapper.render(document, "3.2.0");
        Map<String, Object> responses = map(map(map(map(rendered, "paths"), "/items"), "get"), "responses");
        Map<String, Object> header = map(map(map(responses, "200"), "headers"), "X-Test");

        assertThat(header.containsKey("allowEmptyValue"), is(false));
        assertThat(header.get("allowReserved"), is(true));
    }

    @Test
    void openApi32AllowsDeviceAuthorizationFlow() {
        OpenApiDocument document = openApiDocument(documentWithSecurityScheme(deviceAuthorizationSecurityScheme()));
        Map<String, Object> rendered = OpenApi32DocumentMapper.render(document, "3.2.0");
        Map<String, Object> flow = map(map(securityScheme(rendered), "flows"), "deviceAuthorization");

        assertThat(flow.get("deviceAuthorizationUrl"), is("https://idp.example.com/device"));
        assertThat(flow.get("tokenUrl"), is("https://idp.example.com/token"));
    }

    @Test
    void openApi32PreservesMediaTypeEncodingMap() {
        OpenApiDocument document = OpenApi32DocumentMapper.parse(documentWithEncoding("3.2.0"));
        Map<String, Object> rendered = OpenApi32DocumentMapper.render(document, "3.2.0");
        Map<String, Object> encoding = encoding(rendered);

        assertThat(map(encoding, "profileImage").get("contentType"), is("image/png"));
        assertThat(map(map(encoding, "profileImage"), "headers").containsKey("X-Image-Name"), is(true));
    }

    @Test
    void openApi32PreservesMediaTypeReferences() {
        OpenApiDocument document = OpenApi32DocumentMapper.parse(documentWithMediaTypeReference());
        Map<String, Object> rendered = OpenApi32DocumentMapper.render(document, "3.2.0");
        Map<String, Object> content = map(map(map(map(map(map(rendered, "paths"), "/items"), "get"),
                                             "responses"), "200"), "content");
        Map<String, Object> mediaTypes = map(map(rendered, "components"), "mediaTypes");

        assertMediaTypeReference(map(content, "application/json"), "#/components/mediaTypes/Json");
        assertMediaTypeReference(map(mediaTypes, "JsonReference"), "#/components/mediaTypes/Json");
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

    private static Map<String, Object> documentWithMediaTypeReference() {
        return Map.of("openapi", "3.2.0",
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "paths", Map.of("/items", Map.of("get", Map.of(
                              "responses", Map.of("200", Map.of(
                                      "description", "Items.",
                                      "content", Map.of("application/json",
                                                        mediaTypeReference("#/components/mediaTypes/Json"))))))),
                      "components", Map.of("mediaTypes", Map.of(
                              "Json", Map.of("schema", Map.of("type", "object")),
                              "JsonReference", mediaTypeReference("#/components/mediaTypes/Json"))));
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

    private static Map<String, Object> securityScheme(Map<String, Object> document) {
        return map(map(map(document, "components"), "securitySchemes"), "test");
    }

    private static Map<String, Object> mediaTypeReference(String ref) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("$ref", ref);
        result.put("summary", "Media type summary");
        result.put("description", "Media type description");
        return result;
    }

    private static void assertMediaTypeReference(Map<String, Object> reference, String ref) {
        assertThat(reference.get("$ref"), is(ref));
        assertThat(reference.get("summary"), is("Media type summary"));
        assertThat(reference.get("description"), is("Media type description"));
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
