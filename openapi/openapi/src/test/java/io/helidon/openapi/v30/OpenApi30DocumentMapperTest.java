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
