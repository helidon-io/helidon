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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
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
    void rejectsRepeatedPathTemplateExpressions() {
        String path = "/items/{itemId}/{itemId}";
        IllegalStateException parsed = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.parse(documentWithSection("paths", Map.of(path, Map.of()))));
        assertThat(parsed.getMessage(), containsString(path));
        assertThat(parsed.getMessage(), containsString("must not repeat path template expression {itemId}"));

        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path(path, _ -> { })
                .build();
        IllegalStateException rendered = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(document, "3.2.0"));
        assertThat(rendered.getMessage(), containsString(path));
        assertThat(rendered.getMessage(), containsString("must not repeat path template expression {itemId}"));
    }

    @Test
    void rejectsEmptyPathTemplateExpressions() {
        String path = "/items/{}";
        IllegalStateException parsed = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.parse(documentWithSection("paths", Map.of(path, Map.of()))));
        assertThat(parsed.getMessage(), containsString(path));
        assertThat(parsed.getMessage(), containsString("must not contain an empty path template expression"));

        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path(path, _ -> { })
                .build();
        IllegalStateException rendered = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(document, "3.2.0"));
        assertThat(rendered.getMessage(), containsString(path));
        assertThat(rendered.getMessage(), containsString("must not contain an empty path template expression"));
    }

    @Test
    void validatesPathTemplateGrammar() {
        Map<String, String> invalidPaths = Map.of(
                "/items/[id]", "contains invalid path literal character at index",
                "/items/item id", "contains invalid path literal character at index",
                "/items/\u017E", "contains invalid path literal character at index",
                "/items/%2", "contains an invalid percent-encoded path literal",
                "/items/%GG", "contains an invalid percent-encoded path literal",
                "/items/%\uFF26\uFF26", "contains an invalid percent-encoded path literal",
                "/items/%\uFF11\uFF12", "contains an invalid percent-encoded path literal",
                "/items//details", "must not contain an empty path segment");
        invalidPaths.forEach((path, expectedMessage) -> {
            IllegalStateException parsed = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi32DocumentMapper.parse(documentWithSection("paths", Map.of(path, Map.of()))));
            assertThat(parsed.getMessage(), containsString(path));
            assertThat(parsed.getMessage(), containsString(expectedMessage));

            OpenApiDocument document = OpenApiDocument.builder()
                    .info("Generated API", "1.0.0")
                    .path(path, _ -> { })
                    .build();
            IllegalStateException rendered = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi32DocumentMapper.render(document, "3.2.0"));
            assertThat(rendered.getMessage(), containsString(path));
            assertThat(rendered.getMessage(), containsString(expectedMessage));
        });

        for (String validPath : List.of("/",
                                        "/items/",
                                        "/items/%5Bid%5D",
                                        "/items/segment-._~!$&'()*+,;=:@",
                                        "/items/{item/name?mode#fragment}",
                                        "/items/{item-\u017E}")) {
            OpenApi32DocumentMapper.parse(documentWithSection("paths", Map.of(validPath, Map.of())));
            OpenApi32DocumentMapper.render(OpenApiDocument.builder()
                                                   .info("Generated API", "1.0.0")
                                                   .path(validPath, _ -> { })
                                                   .build(),
                                           "3.2.0");
        }
    }

    @Test
    void validatesPathTemplateParameters() {
        String path = "/items/{id}";
        assertMissingPathParameter(documentWithPathItem(path, Map.of("get", Map.of())), "get");

        assertValidPathTemplateDocument(documentWithPathItem(path, Map.of(
                "parameters", List.of(pathParameter("id")),
                "get", Map.of())));
        assertValidPathTemplateDocument(documentWithPathItem(path, Map.of(
                "get", Map.of("parameters", List.of(pathParameter("id"))))));

        assertMissingPathParameter(documentWithPathItem(path, Map.of(
                "get", Map.of("parameters", List.of(pathParameter("id"))),
                "post", Map.of())), "post");

        Map<String, Object> localReference = documentWithPathItem(path, Map.of(
                "get", Map.of("parameters", List.of(Map.of(
                        "$ref", "#/components/parameters/Id")))));
        localReference.put("components", Map.of("parameters", Map.of("Id", pathParameter("id"))));
        assertValidPathTemplateDocument(localReference);

        Map<String, Object> sameDocumentReferences = documentWithSection("paths", Map.of(
                "/shared/{id}", Map.of("get", Map.of("parameters", List.of(pathParameter("id")))),
                "/alias/{id}", Map.of("get", Map.of("parameters", List.of(Map.of(
                        "$ref", "#/paths/~1shared~1%7Bid%7D/get/parameters/0")))),
                path, Map.of("get", Map.of("parameters", List.of(Map.of(
                        "$ref", "#/paths/~1alias~1%7Bid%7D/get/parameters/0"))))));
        assertValidPathTemplateDocument(sameDocumentReferences);

        assertValidPathTemplateDocument(documentWithPathItem(path, Map.of(
                "get", Map.of("parameters", List.of(Map.of(
                        "$ref", "parameters.yaml#/components/parameters/Id"))))));

        assertValidPathTemplateDocument(documentWithPathItem(path, Map.of(
                "get", Map.of("parameters", List.of(Map.of(
                        "$ref", "#/components/parameters/Missing"))))));

        Map<String, Object> cyclicReference = documentWithPathItem(path, Map.of(
                "get", Map.of("parameters", List.of(Map.of(
                        "$ref", "#/components/parameters/First")))));
        cyclicReference.put("components", Map.of("parameters", Map.of(
                "First", Map.of("$ref", "#/components/parameters/Second"),
                "Second", Map.of("$ref", "#/components/parameters/First"))));
        assertValidPathTemplateDocument(cyclicReference);

        assertValidPathTemplateDocument(documentWithPathItem(path, Map.of(
                "$ref", "paths.yaml#/components/pathItems/Items",
                "get", Map.of())));

        assertValidPathTemplateDocument(documentWithPathItem(path, Map.of(
                "query", Map.of("parameters", List.of(pathParameter("id"))))));
        assertMissingPathParameter(documentWithPathItem(path, Map.of(
                "additionalOperations", Map.of("COPY", Map.of()))), "COPY");
        assertValidPathTemplateDocument(documentWithPathItem(path, Map.of(
                "parameters", List.of(pathParameter("id")),
                "additionalOperations", Map.of("COPY", Map.of()))));
    }

    @Test
    void resolvesLongPathItemReferenceChains() {
        int referenceCount = 10_000;
        int pathCount = 100;
        CountingMap pathItems = new CountingMap();
        for (int i = 0; i < referenceCount - 1; i++) {
            pathItems.put("Item" + i, Map.of("$ref", "#/components/pathItems/Item" + (i + 1)));
        }
        pathItems.put("Item" + (referenceCount - 1), Map.of(
                "get", Map.of("parameters", List.of(pathParameter("id")))));

        Map<String, Object> paths = new LinkedHashMap<>();
        for (int i = 0; i < pathCount; i++) {
            paths.put("/items" + i + "/{id}", Map.of("$ref", "#/components/pathItems/Item0"));
        }
        Map<String, Object> document = documentWithSection("paths", paths);
        document.put("components", Map.of("pathItems", pathItems));
        OpenApiDocument parsed = OpenApi32DocumentMapper.parse(document);
        assertThat(pathItems.lookups() <= referenceCount + pathCount, is(true));
        OpenApi32DocumentMapper.render(parsed, "3.2.0");
    }

    @Test
    void validatesParameterListUniqueness() {
        Map<String, Object> pathParameter = pathParameter("id");
        assertDuplicateParameters(documentWithPathItem("/items/{id}", Map.of(
                "get", Map.of("parameters", List.of(pathParameter, pathParameter)))));

        Map<String, Object> query = queryParameter("id");
        assertDuplicateParameters(documentWithParameters(
                List.of(query, query),
                List.of()));

        Map<String, Object> localReference = documentWithParameters(
                List.of(),
                List.of(query, Map.of("$ref", "#/components/parameters/Alias")));
        localReference.put("components", Map.of("parameters", Map.of("Alias", query)));
        assertDuplicateParameters(localReference);

        Map<String, Object> referenceChain = documentWithParameters(
                List.of(),
                List.of(Map.of("$ref", "#/components/parameters/First"),
                        Map.of("$ref", "#/components/parameters/Second")));
        referenceChain.put("components", Map.of("parameters", Map.of(
                "First", Map.of("$ref", "#/components/parameters/Target"),
                "Second", Map.of("$ref", "#/components/parameters/Target"),
                "Target", query)));
        assertDuplicateParameters(referenceChain);

        Map<String, Object> sameDocumentReference = documentWithSection("paths", Map.of(
                "/shared", Map.of("get", Map.of("parameters", List.of(query))),
                "/alias", Map.of("get", Map.of("parameters", List.of(Map.of(
                        "$ref", "#/paths/~1shared/get/parameters/0")))),
                "/items", Map.of("get", Map.of("parameters", List.of(
                        query,
                        Map.of("$ref", "#/paths/~1alias/get/parameters/0"))))));
        assertDuplicateParameters(sameDocumentReference);

        assertDuplicateParameters(documentWithParameters(
                List.of(),
                List.of(headerParameter("X-Request-Id"), headerParameter("x-request-id"))));

        assertValidParameterDocument(documentWithParameters(
                List.of(query),
                List.of(query)));
        assertValidParameterDocument(documentWithParameters(
                List.of(),
                List.of(queryParameter("id"), queryParameter("ID"), headerParameter("id"))));
        assertValidParameterDocument(documentWithParameters(
                List.of(),
                List.of(Map.of("$ref", "parameters.yaml#/components/parameters/Id"),
                        Map.of("$ref", "parameters.yaml#/components/parameters/Id"))));
    }

    @Test
    void validatesSchemaReferenceSiblings() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                     () -> OpenApi32DocumentMapper.parse(Map.of(
                                                             "openapi", "3.2.0",
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
    void validatesReferenceUris() {
        String malformed = "http://[bad";
        assertInvalidReferenceUri(documentWithExampleReference(malformed), "must be a URI");
        assertInvalidReferenceUri(documentWithSchemaReference(malformed), "must be a URI");
        assertInvalidReferenceUri(documentWithPathItem("/items", Map.of("$ref", malformed)), "must be a URI");

        String ipvFuture = "http://[v1.fe]/description.yaml#/components/examples/Example";
        assertInvalidReferenceUri(documentWithExampleReference(ipvFuture), "IPvFuture host literal");
        assertInvalidReferenceUri(documentWithSchemaReference(ipvFuture), "IPvFuture host literal");
        assertInvalidReferenceUri(documentWithPathItem("/items", Map.of("$ref", ipvFuture)),
                                  "IPvFuture host literal");

        for (String valid : List.of("https://example.test/openapi.yaml#/components/examples/Example",
                                    "../openapi.yaml#/components/examples/Example",
                                    "#/components/examples/Example",
                                    "other.yaml#anchor")) {
            OpenApiDocument document = OpenApi32DocumentMapper.parse(documentWithExampleReference(valid));
            Map<String, Object> validRendered = OpenApi32DocumentMapper.render(document, "3.2.0");
            assertThat(map(map(map(validRendered, "components"), "examples"), "test").get("$ref"), is(valid));
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

        IllegalStateException invalidResponseKey = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.parse(Map.of(
                        "openapi", "3.2.0",
                        "info", Map.of(
                                "title", "Static API",
                                "version", "1.0.0"),
                        "paths", Map.of(
                                "/items", Map.of(
                                        "get", Map.of(
                                                "responses", Map.of(
                                                        "200", Map.of("description", "OK"),
                                                        "bogus", Map.of("description", "Invalid"))))))));
        assertThat(invalidResponseKey.getMessage(), containsString("3.2"));
        assertThat(invalidResponseKey.getMessage(), containsString("bogus"));

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

        OpenApiDocument responseWithInvalidKey = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.operation(
                        "GET",
                        operation -> operation.response("200", "OK").response("bogus", "Invalid")))
                .build();
        IllegalStateException renderedWithInvalidResponseKey = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(responseWithInvalidKey, "3.2.0"));
        assertThat(renderedWithInvalidResponseKey.getMessage(), containsString("3.2"));
        assertThat(renderedWithInvalidResponseKey.getMessage(), containsString("bogus"));
    }

    @Test
    void rejectsMalformedSecurityRequirements() {
        Map<String, Object> invalidSecurityValues = new LinkedHashMap<>();
        invalidSecurityValues.put("security value is not an array", Map.of("OAuth", List.of()));
        invalidSecurityValues.put("security requirement is not an object", List.of("OAuth"));
        invalidSecurityValues.put("scheme scopes are not an array", List.of(Map.of("OAuth", "read")));
        invalidSecurityValues.put("scheme scope is not a string", List.of(Map.of("OAuth", List.of("read", 42))));

        invalidSecurityValues.forEach((description, invalidSecurity) -> {
            Map<String, Object> topLevelDocument = new LinkedHashMap<>(document("3.2.0"));
            topLevelDocument.put("security", invalidSecurity);
            IllegalStateException topLevel = assertThrows(IllegalStateException.class,
                                                          () -> OpenApi32DocumentMapper.parse(topLevelDocument),
                                                          description + " at document level");
            assertThat(description, topLevel.getMessage(), containsString("security"));

            Map<String, Object> operationDocument = new LinkedHashMap<>(document("3.2.0"));
            operationDocument.put("paths", Map.of(
                    "/items", Map.of(
                            "get", Map.of(
                                    "responses", Map.of("200", Map.of("description", "OK")),
                                    "security", invalidSecurity))));
            IllegalStateException operation = assertThrows(IllegalStateException.class,
                                                           () -> OpenApi32DocumentMapper.parse(operationDocument),
                                                           description + " at operation level");
            assertThat(description, operation.getMessage(), containsString("security"));
        });
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
        assertThat(header.containsKey("allowReserved"), is(false));
    }

    @Test
    void validatesQueryStringParameterShape() {
        Map<String, Object> validExample = new LinkedHashMap<>(queryStringParameter("example"));
        validExample.put("example", "q=one");
        Map<String, Object> validExamples = new LinkedHashMap<>(queryStringParameter("examples"));
        validExamples.put("examples", Map.of("one", Map.of("value", "q=one")));
        OpenApi32DocumentMapper.parse(documentWithParameters(List.of(validExample), List.of()));
        OpenApi32DocumentMapper.parse(documentWithParameters(List.of(validExamples), List.of()));

        OpenApiDocument valid = OpenApi32DocumentMapper.parse(documentWithParameters(
                List.of(), List.of(queryStringParameter("query"))));
        Map<String, Object> rendered = OpenApi32DocumentMapper.render(valid, "3.2.0");
        Map<String, Object> operation = map(map(map(rendered, "paths"), "/items"), "get");
        Map<String, Object> parameter = map((Map<?, ?>) ((List<?>) operation.get("parameters")).getFirst(), "content");
        assertThat(parameter.keySet(), is(Set.of("application/x-www-form-urlencoded")));

        List<Map<String, Object>> invalidParameters = new ArrayList<>();
        invalidParameters.add(Map.of("name", "missing", "in", "querystring"));
        invalidParameters.add(Map.of("name", "empty", "in", "querystring", "content", Map.of()));
        Map<String, Object> multipleContent = new LinkedHashMap<>(queryStringParameter("multiple"));
        multipleContent.put("content", Map.of(
                "application/x-www-form-urlencoded", Map.of(),
                "application/json", Map.of()));
        invalidParameters.add(multipleContent);
        Map<String, Object> schemaFields = Map.of(
                "allowEmptyValue", true,
                "style", "form",
                "explode", true,
                "allowReserved", true,
                "schema", Map.of("type", "object"));
        schemaFields.forEach((field, value) -> {
            Map<String, Object> invalid = new LinkedHashMap<>(queryStringParameter(field));
            invalid.put(field, value);
            invalidParameters.add(invalid);
        });

        for (Map<String, Object> invalid : invalidParameters) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi32DocumentMapper.parse(documentWithParameters(List.of(invalid), List.of())));
            assertThat(thrown.getMessage(), containsString("querystring parameter"));
        }

        OpenApiDocument invalidGenerated = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/items", path -> path.parameter(parameterBuilder -> parameterBuilder
                        .name("query")
                        .in("querystring")
                        .schema(JsonObject.builder().set("type", "string").build()))
                        .operation("GET", _ -> { }))
                .build();
        IllegalStateException renderedInvalid = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(invalidGenerated, "3.2.0"));
        assertThat(renderedInvalid.getMessage(), containsString("querystring parameter"));
    }

    @Test
    void validatesEffectiveQueryStringParameterLocations() {
        IllegalStateException repeated = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.parse(documentWithParameters(
                        List.of(queryStringParameter("first"), queryStringParameter("second")), List.of())));
        assertThat(repeated.getMessage(), containsString("more than one querystring"));

        IllegalStateException mixed = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.parse(documentWithParameters(
                        List.of(queryParameter("named")), List.of(queryStringParameter("query")))));
        assertThat(mixed.getMessage(), containsString("cannot combine query and querystring"));

        IllegalStateException reverseMixed = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.parse(documentWithParameters(
                        List.of(queryStringParameter("query")), List.of(queryParameter("named")))));
        assertThat(reverseMixed.getMessage(), containsString("cannot combine query and querystring"));

        IllegalStateException inheritedRepeated = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.parse(documentWithParameters(
                        List.of(queryStringParameter("first")), List.of(queryStringParameter("second")))));
        assertThat(inheritedRepeated.getMessage(), containsString("more than one querystring"));

        OpenApi32DocumentMapper.parse(documentWithParameters(
                List.of(queryStringParameter("query")), List.of(queryStringParameter("query"))));
    }

    @Test
    void ignoresQueryStringFieldsOnComponentReferenceObjects() {
        Map<String, Object> document = documentWithSection("components", Map.of(
                "parameters", Map.of(
                        "Actual", queryStringParameter("actual"),
                        "Alias", Map.of(
                                "$ref", "#/components/parameters/Actual",
                                "in", "querystring",
                                "style", "form"))));

        OpenApiDocument parsed = OpenApi32DocumentMapper.parse(document);
        Map<String, Object> rendered = OpenApi32DocumentMapper.render(parsed, "3.2.0");
        Map<String, Object> alias = map(map(map(rendered, "components"), "parameters"), "Alias");

        assertThat(alias.get("$ref"), is("#/components/parameters/Actual"));
        assertThat(alias.containsKey("in"), is(false));
        assertThat(alias.containsKey("style"), is(false));
    }

    @Test
    void validatesEffectiveQueryStringLocationsThroughParameterReferenceChains() {
        Map<String, Object> document = documentWithParameters(
                List.of(queryParameter("named")),
                List.of(Map.of("$ref", "#/components/parameters/Alias")));
        document.put("components", Map.of("parameters", Map.of(
                "Alias", Map.of(
                        "$ref", "#/components/parameters/Query%2EString",
                        "in", "querystring",
                        "style", "form"),
                "Query.String", queryStringParameter("query"))));

        IllegalStateException parsed = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi32DocumentMapper.parse(document));
        assertThat(parsed.getMessage(), containsString("cannot combine query and querystring"));

        IllegalStateException rendered = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(openApiDocument(document), "3.2.0"));
        assertThat(rendered.getMessage(), containsString("cannot combine query and querystring"));

        Map<String, Object> cyclic = documentWithParameters(
                List.of(),
                List.of(Map.of("$ref", "#/components/parameters/First")));
        cyclic.put("components", Map.of("parameters", Map.of(
                "First", Map.of("$ref", "#/components/parameters/Second"),
                "Second", Map.of("$ref", "#/components/parameters/First"))));
        OpenApi32DocumentMapper.parse(cyclic);
        OpenApi32DocumentMapper.render(openApiDocument(cyclic), "3.2.0");
    }

    @Test
    void validatesQueryStringLocationsThroughSelfReferences() {
        String self = "https://example.test/api";
        String ref = self + "#/components/parameters/QueryString";
        Map<String, Object> valid = documentWithParameters(List.of(), List.of(Map.of("$ref", ref)));
        valid.put("$self", self);
        valid.put("components", Map.of("parameters", Map.of(
                "QueryString", queryStringParameter("query"))));

        OpenApiDocument parsed = OpenApi32DocumentMapper.parse(valid);
        Map<String, Object> rendered = OpenApi32DocumentMapper.render(parsed, "3.2.0");
        Map<String, Object> operation = map(map(map(rendered, "paths"), "/items"), "get");
        Map<?, ?> renderedReference = (Map<?, ?>) ((List<?>) operation.get("parameters")).getFirst();

        assertThat(rendered.get("$self"), is(self));
        assertThat(renderedReference.get("$ref"), is(ref));

        Map<String, Object> invalid = documentWithParameters(
                List.of(queryParameter("named")),
                List.of(Map.of("$ref", ref)));
        invalid.put("$self", self);
        invalid.put("components", valid.get("components"));

        IllegalStateException parsedInvalid = assertThrows(IllegalStateException.class,
                                                           () -> OpenApi32DocumentMapper.parse(invalid));
        assertThat(parsedInvalid.getMessage(), containsString("cannot combine query and querystring"));

        IllegalStateException renderedInvalid = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(openApiDocument(invalid), "3.2.0"));
        assertThat(renderedInvalid.getMessage(), containsString("cannot combine query and querystring"));
    }

    @Test
    void validatesQueryStringParametersInReusableAndCallbackPaths() {
        Map<String, Object> invalidPathItem = Map.of(
                "parameters", List.of(queryParameter("named"), queryStringParameter("query")),
                "get", Map.of());
        Map<String, Object> invalidComponentParameter = new LinkedHashMap<>(queryStringParameter("component"));
        invalidComponentParameter.remove("content");
        invalidComponentParameter.put("schema", Map.of("type", "object"));
        Map<String, Object> callbackPathItem = Map.of(
                "get", Map.of("callbacks", Map.of(
                        "Nested", Map.of("{$request.body#/callbackUrl}", invalidPathItem))));
        Map<String, Object> additionalOperationPathItem = Map.of(
                "additionalOperations", Map.of("SUBSCRIBE", Map.of(
                        "parameters", List.of(queryParameter("named"), queryStringParameter("query")))));

        List<Map<String, Object>> invalidDocuments = List.of(
                documentWithSection("webhooks", Map.of("event", invalidPathItem)),
                documentWithSection("components", Map.of(
                        "pathItems", Map.of("Reusable", invalidPathItem))),
                documentWithSection("components", Map.of(
                        "callbacks", Map.of("Reusable", Map.of("{$request.body#/callbackUrl}", invalidPathItem)))),
                documentWithSection("paths", Map.of("/callback", callbackPathItem)),
                documentWithSection("paths", Map.of("/additional", additionalOperationPathItem)),
                documentWithSection("components", Map.of(
                        "parameters", Map.of("Query", invalidComponentParameter))));

        for (Map<String, Object> invalidDocument : invalidDocuments) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi32DocumentMapper.parse(invalidDocument));
            assertThat(thrown.getMessage(), containsString("querystring"));
        }
    }

    @Test
    void ignoresQueryStringLikeExtensionData() {
        Map<String, Object> extensionValue = Map.of(
                "parameters", List.of(queryParameter("named"), queryStringParameter("query")),
                "get", Map.of());

        OpenApi32DocumentMapper.parse(documentWithSection("paths", Map.of(
                "x-validation-data", extensionValue,
                "/items", Map.of())));
        OpenApi32DocumentMapper.parse(documentWithSection("components", Map.of(
                "callbacks", Map.of("Reusable", Map.of("x-validation-data", extensionValue)))));
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
    void validatesMediaTypeEncodingCombinations() {
        OpenApi32DocumentMapper.parse(documentWithMediaType(
                "Multipart/Mixed; boundary=test",
                Map.of(
                        "itemSchema", Map.of("type", "string"),
                        "prefixEncoding", List.of(Map.of()),
                        "itemEncoding", Map.of())));
        OpenApi32DocumentMapper.parse(documentWithMediaType(
                "multipart/form-data",
                Map.of(
                        "schema", Map.of("type", List.of("array", "null")),
                        "prefixEncoding", List.of(Map.of()))));
        OpenApiDocument ignoredPositional = OpenApi32DocumentMapper.parse(documentWithMediaType(
                "application/json",
                Map.of(
                        "prefixEncoding", List.of(Map.of()),
                        "itemEncoding", Map.of())));
        Map<String, Object> ignoredRendered = OpenApi32DocumentMapper.render(ignoredPositional, "3.2.0");
        Map<String, Object> ignoredMediaType = map(map(map(map(map(map(ignoredRendered, "paths"), "/upload"), "post"),
                                                           "requestBody"), "content"), "application/json");
        assertThat(ignoredMediaType.containsKey("prefixEncoding"), is(true));
        assertThat(ignoredMediaType.containsKey("itemEncoding"), is(true));

        List<Map<String, Object>> invalidDocuments = List.of(
                documentWithMediaType("multipart/form-data", Map.of(
                        "schema", Map.of("type", "array"),
                        "encoding", Map.of("item", Map.of()),
                        "prefixEncoding", List.of(Map.of()))),
                documentWithMediaType("multipart/form-data", Map.of(
                        "itemSchema", Map.of("type", "string"),
                        "encoding", Map.of("item", Map.of()),
                        "itemEncoding", Map.of())),
                documentWithMediaType("multipart/mixed", Map.of(
                        "prefixEncoding", List.of(Map.of()))),
                documentWithMediaType("multipart/mixed", Map.of(
                        "schema", Map.of("type", "object"),
                        "itemEncoding", Map.of())));

        for (Map<String, Object> invalidDocument : invalidDocuments) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi32DocumentMapper.parse(invalidDocument));
            assertThat(thrown.getMessage(), containsString("OpenAPI 3.2 media type"));
        }

        OpenApiDocument invalidGenerated = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/upload", path -> path.operation("POST", operation -> operation
                        .requestBody(body -> body.content("multipart/form-data", mediaType -> mediaType
                                .schema(JsonObject.builder().set("type", "array").build())
                                .encoding("item", OpenApiDocument.Encoding.builder().build())
                                .prefixEncoding(JsonArray.empty())))))
                .build();
        IllegalStateException renderedInvalid = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(invalidGenerated, "3.2.0"));
        assertThat(renderedInvalid.getMessage(), containsString("cannot combine encoding"));
    }

    @Test
    void validatesNestedEncodingCombinations() {
        List<Map<String, Object>> invalidEncodings = List.of(
                Map.of(
                        "encoding", Map.of("nested", Map.of()),
                        "prefixEncoding", List.of(Map.of())),
                Map.of(
                        "encoding", Map.of("nested", Map.of()),
                        "itemEncoding", Map.of()));

        for (Map<String, Object> invalidEncoding : invalidEncodings) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi32DocumentMapper.parse(documentWithMediaType(
                            "multipart/form-data",
                            Map.of("encoding", Map.of("item", invalidEncoding)))));
            assertThat(thrown.getMessage(), containsString("OpenAPI 3.2 encoding at"));
            assertThat(thrown.getMessage(), containsString(".encoding.item"));
            assertThat(thrown.getMessage(), containsString("cannot combine encoding with prefixEncoding or itemEncoding"));
        }

        OpenApiDocument.Encoding invalidEncoding = OpenApiDocument.Encoding.builder()
                .encoding("nested", OpenApiDocument.Encoding.builder().build())
                .prefixEncoding(JsonArray.empty())
                .build();
        OpenApiDocument invalidGenerated = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/upload", path -> path.operation("POST", operation -> operation
                        .requestBody(body -> body.content("multipart/form-data", mediaType -> mediaType
                                .encoding("item", invalidEncoding)))))
                .build();

        IllegalStateException renderedInvalid = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(invalidGenerated, "3.2.0"));
        assertThat(renderedInvalid.getMessage(), containsString("OpenAPI 3.2 encoding at"));
        assertThat(renderedInvalid.getMessage(), containsString(".encoding.item"));
        assertThat(renderedInvalid.getMessage(),
                   containsString("cannot combine encoding with prefixEncoding or itemEncoding"));
    }

    @Test
    void validatesReusableMediaTypesInContentContext() {
        Map<String, Object> positional = Map.of(
                "itemSchema", Map.of("type", "string"),
                "prefixEncoding", List.of(Map.of()));
        OpenApi32DocumentMapper.parse(documentWithSection("components", Map.of(
                "mediaTypes", Map.of("Positional", positional))));
        OpenApi32DocumentMapper.parse(documentWithMediaTypeReference(
                "multipart/mixed", "#/components/mediaTypes/Positional", positional));

        OpenApi32DocumentMapper.parse(documentWithMediaTypeReference(
                "application/json", "#/components/mediaTypes/Positional", positional));

        OpenApi32DocumentMapper.parse(documentWithMediaTypeReference(
                "application/json", "https://example.test/media-types/Positional", positional));

        Map<String, Object> localArrayRef = documentWithMediaType(
                "multipart/mixed",
                Map.of(
                        "schema", Map.of("$ref", "#/components/schemas/Items"),
                        "prefixEncoding", List.of(Map.of())));
        localArrayRef.put("components", Map.of("schemas", Map.of("Items", Map.of("type", "array"))));
        OpenApi32DocumentMapper.parse(localArrayRef);

        Map<String, Object> localObjectRef = documentWithMediaType(
                "multipart/mixed",
                Map.of(
                        "schema", Map.of("$ref", "#/components/schemas/Item"),
                        "prefixEncoding", List.of(Map.of())));
        localObjectRef.put("components", Map.of("schemas", Map.of("Item", Map.of("type", "object"))));
        IllegalStateException nonArrayRef = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.parse(localObjectRef));
        assertThat(nonArrayRef.getMessage(), containsString("requires itemSchema or an array schema"));

        OpenApi32DocumentMapper.parse(documentWithMediaType(
                "multipart/mixed",
                Map.of(
                        "schema", Map.of("$ref", "https://example.test/schemas/Items"),
                        "prefixEncoding", List.of(Map.of()))));
    }

    @Test
    void validatesMediaTypesAcrossDocumentLocations() {
        Map<String, Object> invalidMediaType = Map.of(
                "schema", Map.of("type", "array"),
                "encoding", Map.of("item", Map.of()),
                "prefixEncoding", List.of(Map.of()));
        Map<String, Object> invalidContent = Map.of("multipart/form-data", invalidMediaType);
        Map<String, Object> invalidParameter = Map.of(
                "name", "item", "in", "query", "content", invalidContent);
        Map<String, Object> invalidHeader = Map.of("content", invalidContent);
        Map<String, Object> invalidRequestBody = Map.of("content", invalidContent);
        Map<String, Object> invalidResponse = Map.of("content", invalidContent);
        Map<String, Object> invalidPathItem = Map.of("get", Map.of(
                "responses", Map.of("200", Map.of(
                        "headers", Map.of("X-Item", invalidHeader)))));
        Map<String, Object> invalidNestedEncoding = Map.of(
                "schema", Map.of("type", "object"),
                "encoding", Map.of("item", Map.of(
                        "headers", Map.of("X-Item", invalidHeader))));

        List<Map<String, Object>> invalidDocuments = List.of(
                documentWithSection("paths", Map.of("/items", invalidPathItem)),
                documentWithSection("webhooks", Map.of("event", invalidPathItem)),
                documentWithSection("components", Map.of(
                        "pathItems", Map.of("Reusable", invalidPathItem))),
                documentWithSection("components", Map.of(
                        "callbacks", Map.of("Reusable", Map.of("{$request.body#/callbackUrl}", invalidPathItem)))),
                documentWithSection("components", Map.of(
                        "parameters", Map.of("Item", invalidParameter))),
                documentWithSection("components", Map.of(
                        "headers", Map.of("X-Item", invalidHeader))),
                documentWithSection("components", Map.of(
                        "requestBodies", Map.of("Item", invalidRequestBody))),
                documentWithSection("components", Map.of(
                        "responses", Map.of("Item", invalidResponse))),
                documentWithSection("components", Map.of(
                        "mediaTypes", Map.of("Item", invalidMediaType))),
                documentWithMediaType("multipart/form-data", invalidNestedEncoding));

        for (Map<String, Object> invalidDocument : invalidDocuments) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> OpenApi32DocumentMapper.parse(invalidDocument));
            assertThat(thrown.getMessage(), containsString("cannot combine encoding"));
        }

        OpenApi32DocumentMapper.parse(documentWithMediaType("application/json", Map.of(
                "schema", Map.of(
                        "type", "object",
                        "properties", Map.of("content", Map.of("multipart/form-data", invalidMediaType))))));

        Map<String, Object> ignoredPositional = Map.of(
                "prefixEncoding", List.of(Map.of(
                        "headers", Map.of("X-Item", invalidHeader))));
        OpenApiDocument ignored = OpenApi32DocumentMapper.parse(documentWithMediaType(
                "application/json", ignoredPositional));
        OpenApi32DocumentMapper.render(ignored, "3.2.0");
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

    private static Map<String, Object> documentWithParameters(List<Map<String, Object>> pathParameters,
                                                              List<Map<String, Object>> operationParameters) {
        Map<String, Object> operation = new LinkedHashMap<>();
        if (!operationParameters.isEmpty()) {
            operation.put("parameters", operationParameters);
        }
        Map<String, Object> pathItem = new LinkedHashMap<>();
        if (!pathParameters.isEmpty()) {
            pathItem.put("parameters", pathParameters);
        }
        pathItem.put("get", operation);
        return documentWithSection("paths", Map.of("/items", pathItem));
    }

    private static Map<String, Object> documentWithPathItem(String path, Map<String, Object> pathItem) {
        return documentWithSection("paths", Map.of(path, pathItem));
    }

    private static Map<String, Object> documentWithSection(String section, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openapi", "3.2.0");
        result.put("info", Map.of("title", "Static API", "version", "1.0.0"));
        result.put(section, value);
        return result;
    }

    private static Map<String, Object> documentWithExampleReference(String reference) {
        return Map.of("openapi", "3.2.0",
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "paths", Map.of(),
                      "components", Map.of("examples", Map.of("test", Map.of("$ref", reference))));
    }

    private static Map<String, Object> documentWithSchemaReference(String reference) {
        return Map.of("openapi", "3.2.0",
                      "info", Map.of("title", "Static API",
                                     "version", "1.0.0"),
                      "paths", Map.of(),
                      "components", Map.of("schemas", Map.of("test", Map.of("$ref", reference))));
    }

    private static Map<String, Object> pathParameter(String name) {
        return Map.of("name", name,
                      "in", "path",
                      "required", true,
                      "schema", Map.of("type", "string"));
    }

    private static Map<String, Object> queryStringParameter(String name) {
        return Map.of(
                "name", name,
                "in", "querystring",
                "content", Map.of("application/x-www-form-urlencoded", Map.of("schema", Map.of("type", "object"))));
    }

    private static Map<String, Object> queryParameter(String name) {
        return Map.of("name", name, "in", "query", "schema", Map.of("type", "string"));
    }

    private static Map<String, Object> headerParameter(String name) {
        return Map.of("name", name, "in", "header", "schema", Map.of("type", "string"));
    }

    private static void assertDuplicateParameters(Map<String, Object> source) {
        IllegalStateException parsed = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi32DocumentMapper.parse(source));
        assertThat(parsed.getMessage(), containsString("parameters contain duplicate"));

        IllegalStateException rendered = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(openApiDocument(source), "3.2.0"));
        assertThat(rendered.getMessage(), containsString("parameters contain duplicate"));
    }

    private static void assertValidParameterDocument(Map<String, Object> source) {
        OpenApi32DocumentMapper.render(OpenApi32DocumentMapper.parse(source), "3.2.0");
    }

    private static void assertMissingPathParameter(Map<String, Object> source, String operationName) {
        IllegalStateException parsed = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi32DocumentMapper.parse(source));
        assertThat(parsed.getMessage(), containsString("operation " + operationName));
        assertThat(parsed.getMessage(), containsString("template expression {id}"));

        IllegalStateException rendered = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(openApiDocument(source), "3.2.0"));
        assertThat(rendered.getMessage(), containsString("operation " + operationName));
        assertThat(rendered.getMessage(), containsString("template expression {id}"));
    }

    private static void assertValidPathTemplateDocument(Map<String, Object> source) {
        OpenApi32DocumentMapper.render(OpenApi32DocumentMapper.parse(source), "3.2.0");
    }

    private static void assertInvalidReferenceUri(Map<String, Object> source, String expectedMessage) {
        IllegalStateException parsed = assertThrows(IllegalStateException.class,
                                                    () -> OpenApi32DocumentMapper.parse(source));
        assertThat(parsed.getMessage(), containsString(expectedMessage));

        IllegalStateException rendered = assertThrows(
                IllegalStateException.class,
                () -> OpenApi32DocumentMapper.render(openApiDocument(source), "3.2.0"));
        assertThat(rendered.getMessage(), containsString(expectedMessage));
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

    private static Map<String, Object> documentWithMediaType(String mediaType,
                                                             Map<String, Object> mediaTypeObject) {
        return documentWithSection("paths", Map.of("/upload", Map.of("post", Map.of(
                "requestBody", Map.of("content", Map.of(mediaType, mediaTypeObject))))));
    }

    private static Map<String, Object> documentWithMediaTypeReference(String mediaType,
                                                                      String ref,
                                                                      Map<String, Object> component) {
        Map<String, Object> document = documentWithMediaType(mediaType, Map.of("$ref", ref));
        document.put("components", Map.of("mediaTypes", Map.of("Positional", component)));
        return document;
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

    private static final class CountingMap extends LinkedHashMap<String, Object> {
        private int lookups;

        @Override
        public Object get(Object key) {
            lookups++;
            return super.get(key);
        }

        private int lookups() {
            return lookups;
        }
    }
}
