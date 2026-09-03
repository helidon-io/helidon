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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.json.JsonObject;
import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.OpenApiDocumentContext;
import io.helidon.openapi.OpenApiGeneratedMode;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.spi.OpenApiVersionProvider;
import io.helidon.openapi.v30.OpenApi30Version;
import io.helidon.openapi.v30.OpenApiDocumentMapperSupport;
import io.helidon.openapi.v30.OpenApiDocumentReader;
import io.helidon.openapi.v31.OpenApi31Version;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApi32VersionTest {
    @Test
    void rejectsDuplicateOperationIdsAcrossVersions() {
        String staticDocument = """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /first:
                    get:
                      operationId: duplicate
                      responses:
                        "200": {description: OK}
                  /second:
                    post:
                      operationId: duplicate
                      responses:
                        "200": {description: OK}
                """;
        OpenApiDocument generatedDocument = OpenApiDocument.builder()
                .info("API", "1")
                .path("/first", path -> path.operation(
                        "GET",
                        operation -> operation.operationId("duplicate").response("200", "OK")))
                .path("/second", path -> path.operation(
                        "POST",
                        operation -> operation.operationId("duplicate").response("200", "OK")))
                .build();

        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            IllegalStateException parsed = assertThrows(
                    IllegalStateException.class,
                    () -> version.parse(context(version),
                                        staticDocument.formatted(version.version()),
                                        MediaTypes.APPLICATION_OPENAPI_YAML));
            assertThat(version.version(),
                       parsed.getMessage(),
                       containsString("Duplicate OpenAPI operationId duplicate"));

            IllegalStateException rendered = assertThrows(
                    IllegalStateException.class,
                    () -> version.render(context(version), generatedDocument));
            assertThat(version.version(),
                       rendered.getMessage(),
                       containsString("Duplicate OpenAPI operationId duplicate"));
        }
    }

    @Test
    void validatesEmptyOperationsAcrossVersions() {
        String staticDocument = """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get: {}
                """;
        OpenApiDocument generatedDocument = OpenApiDocument.builder()
                .info("API", "1")
                .path("/items", path -> path.operation("GET", _ -> { }))
                .build();

        for (OpenApiVersion version : List.of(OpenApi30Version.create(), OpenApi31Version.create())) {
            IllegalStateException parsed = assertThrows(
                    IllegalStateException.class,
                    () -> version.parse(context(version),
                                        staticDocument.formatted(version.version()),
                                        MediaTypes.APPLICATION_OPENAPI_YAML));
            assertThat(parsed.getMessage(), containsString("requires responses"));

            IllegalStateException rendered = assertThrows(
                    IllegalStateException.class,
                    () -> version.render(context(version), generatedDocument));
            assertThat(rendered.getMessage(), containsString("requires responses"));
        }

        OpenApiVersion version32 = OpenApi32Version.create();
        OpenApiDocument parsed = version32.parse(context(version32),
                                                 staticDocument.formatted(version32.version()),
                                                 MediaTypes.APPLICATION_OPENAPI_YAML);
        version32.render(context(version32), parsed);
        version32.render(context(version32), generatedDocument);
    }

    @Test
    void preservesEmptyRequiredUriReferencesAcrossVersions() {
        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            OpenApiDocument document = version.parse(
                    context(version),
                    """
                    openapi: %s
                    info: {title: API, version: "1"}
                    servers:
                      - url: ""
                    paths: {}
                    externalDocs:
                      url: ""
                    components:
                      securitySchemes:
                        oauth:
                          type: oauth2
                          flows:
                            authorizationCode:
                              authorizationUrl: ""
                              tokenUrl: ""
                              scopes: {}
                        openId:
                          type: openIdConnect
                          openIdConnectUrl: ""
                    """.formatted(version.version()),
                    MediaTypes.APPLICATION_OPENAPI_YAML);
            Map<String, Object> rendered = parse(version.render(context(version), document));

            assertThat(((Map<?, ?>) ((List<?>) rendered.get("servers")).getFirst()).get("url"), is(""));
            assertThat(map(rendered, "externalDocs").get("url"), is(""));
            Map<String, Object> securitySchemes = map(map(rendered, "components"), "securitySchemes");
            Map<String, Object> authorizationCode = map(map(map(securitySchemes, "oauth"), "flows"),
                                                        "authorizationCode");
            assertThat(authorizationCode.get("authorizationUrl"), is(""));
            assertThat(authorizationCode.get("tokenUrl"), is(""));
            assertThat(map(securitySchemes, "openId").get("openIdConnectUrl"), is(""));
        }

        OpenApiVersion version32 = OpenApi32Version.create();
        OpenApiDocument deviceDocument = version32.parse(
                context(version32),
                """
                openapi: 3.2.0
                info: {title: API, version: "1"}
                paths: {}
                components:
                  securitySchemes:
                    oauth:
                      type: oauth2
                      flows:
                        deviceAuthorization:
                          deviceAuthorizationUrl: ""
                          tokenUrl: ""
                          scopes: {}
                """,
                MediaTypes.APPLICATION_OPENAPI_YAML);
        Map<String, Object> renderedDevice = parse(version32.render(context(version32), deviceDocument));
        Map<String, Object> deviceAuthorization = map(
                map(map(map(map(renderedDevice, "components"), "securitySchemes"), "oauth"), "flows"),
                "deviceAuthorization");
        assertThat(deviceAuthorization.get("deviceAuthorizationUrl"), is(""));
        assertThat(deviceAuthorization.get("tokenUrl"), is(""));
    }

    @Test
    void validatesReferenceObjectFieldsAcrossVersions() {
        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            List<String> invalidDocuments = List.of(
                    """
                    openapi: %s
                    info: {title: API, version: "1"}
                    paths: {}
                    components:
                      examples:
                        Invalid: {$ref: 42}
                    """,
                    """
                    openapi: %s
                    info: {title: API, version: "1"}
                    paths:
                      /items:
                        get:
                          callbacks:
                            invalid: {$ref: 42}
                          responses:
                            "200": {description: OK}
                    """);
            for (String invalidDocument : invalidDocuments) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.parse(context(version),
                                            invalidDocument.formatted(version.version()),
                                            MediaTypes.APPLICATION_OPENAPI_YAML));
                assertThat(thrown.getMessage(), containsString("field $ref must be a string"));
            }
        }

        String invalidSummary = """
                openapi: %s
                info: {title: API, version: "1"}
                paths: {}
                components:
                  examples:
                    Invalid: {$ref: '#/components/examples/Other', summary: 42}
                """;
        OpenApiVersion version30 = OpenApi30Version.create();
        OpenApiDocument filtered = version30.parse(context(version30),
                                                   invalidSummary.formatted(version30.version()),
                                                   MediaTypes.APPLICATION_OPENAPI_YAML);
        version30.render(context(version30), filtered);

        for (OpenApiVersion version : List.of(OpenApi31Version.create(), OpenApi32Version.create())) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> version.parse(context(version),
                                        invalidSummary.formatted(version.version()),
                                        MediaTypes.APPLICATION_OPENAPI_YAML));
            assertThat(thrown.getMessage(), containsString("field summary must be a string"));
        }
    }

    @Test
    void ignoresReferenceObjectSchemaSiblingsAcrossVersions() {
        String parameterAndHeaderDocument = """
                openapi: %s
                info: {title: API, version: "1"}
                paths: {}
                components:
                  parameters:
                    Actual: {name: query, in: query, schema: {type: string}}
                    Alias:
                      $ref: '#/components/parameters/Actual'
                      schema: not-an-object
                  headers:
                    Actual: {schema: {type: string}}
                    Alias:
                      $ref: '#/components/headers/Actual'
                      schema: not-an-object
                """;

        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            OpenApiDocument parsed = version.parse(
                    context(version),
                    parameterAndHeaderDocument.formatted(version.version()),
                    MediaTypes.APPLICATION_OPENAPI_YAML);
            Map<String, Object> rendered = parse(version.render(context(version), parsed));
            Map<String, Object> components = map(rendered, "components");
            Map<String, Object> parameterAlias = map(map(components, "parameters"), "Alias");
            Map<String, Object> headerAlias = map(map(components, "headers"), "Alias");

            assertThat(parameterAlias.get("$ref"), is("#/components/parameters/Actual"));
            assertThat(parameterAlias.containsKey("schema"), is(false));
            assertThat(headerAlias.get("$ref"), is("#/components/headers/Actual"));
            assertThat(headerAlias.containsKey("schema"), is(false));
        }

        OpenApiVersion version32 = OpenApi32Version.create();
        OpenApiDocument parsed = version32.parse(
                context(version32),
                """
                openapi: 3.2.0
                info: {title: API, version: "1"}
                paths: {}
                components:
                  mediaTypes:
                    Actual: {schema: {type: string}}
                    Alias:
                      $ref: '#/components/mediaTypes/Actual'
                      schema: not-an-object
                      itemSchema: not-an-object
                """,
                MediaTypes.APPLICATION_OPENAPI_YAML);
        Map<String, Object> rendered = parse(version32.render(context(version32), parsed));
        Map<String, Object> alias = map(map(map(rendered, "components"), "mediaTypes"), "Alias");

        assertThat(alias.get("$ref"), is("#/components/mediaTypes/Actual"));
        assertThat(alias.containsKey("schema"), is(false));
        assertThat(alias.containsKey("itemSchema"), is(false));
    }

    @Test
    void validatesSecuritySchemeRequirementsAcrossVersions() {
        List<String> invalidSecuritySchemes = List.of(
                "{type: apiKey, in: header}",
                "{type: http}",
                "{type: oauth2}",
                "{type: openIdConnect}",
                """
                type: oauth2
                flows:
                  implicit:
                    scopes: {}
                """,
                """
                type: oauth2
                flows:
                  clientCredentials:
                    tokenUrl: https://example.com/token
                """);

        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            for (String securityScheme : invalidSecuritySchemes) {
                String invalidDocument = """
                        openapi: %s
                        info: {title: API, version: "1"}
                        paths: {}
                        components:
                          securitySchemes:
                            test:
                        %s
                        """.formatted(version.version(), securityScheme.indent(6));
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.parse(context(version), invalidDocument, MediaTypes.APPLICATION_OPENAPI_YAML));
                assertThat(thrown.getMessage(), containsString("requires"));
            }

            OpenApiDocument invalidGenerated = OpenApiDocument.builder()
                    .info("API", "1")
                    .paths(Map.of())
                    .components(components -> components.securityScheme("test", scheme -> scheme.type("http")))
                    .build();
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> version.render(context(version), invalidGenerated));
            assertThat(thrown.getMessage(), containsString("requires scheme"));
        }

        OpenApiVersion version32 = OpenApi32Version.create();
        for (String deviceFlow : List.of(
                "{tokenUrl: https://example.com/token, scopes: {}}",
                "{deviceAuthorizationUrl: https://example.com/device, scopes: {}}",
                "{deviceAuthorizationUrl: https://example.com/device, tokenUrl: https://example.com/token}")) {
            String invalidDocument = """
                    openapi: %s
                    info: {title: API, version: "1"}
                    paths: {}
                    components:
                      securitySchemes:
                        test:
                          type: oauth2
                          flows:
                            deviceAuthorization: %s
                    """.formatted(version32.version(), deviceFlow);
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> version32.parse(context(version32), invalidDocument, MediaTypes.APPLICATION_OPENAPI_YAML));
            assertThat(thrown.getMessage(), containsString("requires"));
        }
    }

    @Test
    void requiresTrueForPathParametersAcrossVersions() {
        List<String> invalidStaticDocuments = List.of(
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items/{id}:
                    parameters:
                      - name: id
                        in: path
                        schema: {type: string}
                    get:
                      responses:
                        "200": {description: OK}
                """,
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items/{id}:
                    parameters:
                      - name: id
                        in: path
                        required: false
                        schema: {type: string}
                    get:
                      responses:
                        "200": {description: OK}
                """);
        String validStaticDocument = """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items/{id}:
                    parameters:
                      - name: id
                        in: path
                        required: true
                        schema: {type: string}
                    get:
                      responses:
                        "200": {description: OK}
                """;

        JsonObject stringSchema = JsonObject.builder().set("type", "string").build();
        List<OpenApiDocument> invalidGeneratedDocuments = List.of(
                OpenApiDocument.builder()
                        .info("API", "1")
                        .path("/items/{id}", path -> path
                                .parameter(parameter -> parameter.name("id")
                                        .in("path")
                                        .schema(stringSchema))
                                .operation("GET", operation -> operation.response("200", "OK")))
                        .build(),
                OpenApiDocument.builder()
                        .info("API", "1")
                        .path("/items/{id}", path -> path
                                .parameter(parameter -> parameter.name("id")
                                        .in("path")
                                        .required(false)
                                        .schema(stringSchema))
                                .operation("GET", operation -> operation.response("200", "OK")))
                        .build());
        OpenApiDocument validGeneratedDocument = OpenApiDocument.builder()
                .info("API", "1")
                .path("/items/{id}", path -> path
                        .parameter(parameter -> parameter.name("id")
                                .in("path")
                                .required(true)
                                .schema(stringSchema))
                        .operation("GET", operation -> operation.response("200", "OK")))
                .build();

        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            for (String invalidStaticDocument : invalidStaticDocuments) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.parse(context(version),
                                            invalidStaticDocument.formatted(version.version()),
                                            MediaTypes.APPLICATION_OPENAPI_YAML));
                assertThat(thrown.getMessage(), containsString("path parameter requires required: true"));
            }
            for (OpenApiDocument invalidGeneratedDocument : invalidGeneratedDocuments) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.render(context(version), invalidGeneratedDocument));
                assertThat(thrown.getMessage(), containsString("path parameter requires required: true"));
            }
            version.parse(context(version),
                          validStaticDocument.formatted(version.version()),
                          MediaTypes.APPLICATION_OPENAPI_YAML);
            version.render(context(version), validGeneratedDocument);
        }
    }

    @Test
    void validatesParameterAndHeaderSchemaContentChoiceAcrossVersions() {
        List<String> invalidDocuments = List.of(
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      parameters: [{name: query, in: query}]
                      responses:
                        "200": {description: OK}
                """,
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      parameters:
                        - name: query
                          in: query
                          schema: {type: string}
                          content: {application/json: {}}
                      responses:
                        "200": {description: OK}
                """,
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      responses:
                        "200":
                          description: OK
                          headers: {X-Test: {}}
                """,
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      responses:
                        "200":
                          description: OK
                          headers:
                            X-Test:
                              schema: {type: string}
                              content: {application/json: {}}
                """);

        JsonObject stringSchema = JsonObject.builder().set("type", "string").build();
        List<OpenApiDocument> invalidGenerated = List.of(
                OpenApiDocument.builder()
                        .info("API", "1")
                        .path("/items", path -> path.operation("GET", operation -> operation
                                .parameter(parameter -> parameter.name("query").in("query"))
                                .response("200", "OK")))
                        .build(),
                OpenApiDocument.builder()
                        .info("API", "1")
                        .path("/items", path -> path.operation("GET", operation -> operation
                                .parameter(parameter -> parameter.name("query")
                                        .in("query")
                                        .schema(stringSchema)
                                        .content("application/json", _ -> { }))
                                .response("200", "OK")))
                        .build(),
                OpenApiDocument.builder()
                        .info("API", "1")
                        .path("/items", path -> path.operation("GET", operation -> operation
                                .response("200", response -> response.description("OK")
                                        .header("X-Test", _ -> { }))))
                        .build(),
                OpenApiDocument.builder()
                        .info("API", "1")
                        .path("/items", path -> path.operation("GET", operation -> operation
                                .response("200", response -> response.description("OK")
                                        .header("X-Test", header -> header.schema(stringSchema)
                                                .content("application/json", _ -> { })))))
                        .build());

        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            for (String invalidDocument : invalidDocuments) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.parse(context(version),
                                            invalidDocument.formatted(version.version()),
                                            MediaTypes.APPLICATION_OPENAPI_YAML));
                assertThat(thrown.getMessage(), containsString("exactly one of schema or content"));
            }
            for (OpenApiDocument invalidDocument : invalidGenerated) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.render(context(version), invalidDocument));
                assertThat(thrown.getMessage(), containsString("exactly one of schema or content"));
            }
        }
    }

    @Test
    void rejectsExampleAndExamplesAcrossVersions() {
        List<String> invalidStaticDocuments = List.of(
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      parameters:
                        - name: query
                          in: query
                          schema: {type: string}
                          example: short
                          examples: {named: {value: named}}
                      responses:
                        "200": {description: OK}
                """,
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      responses:
                        "200":
                          description: OK
                          headers:
                            X-Test:
                              schema: {type: string}
                              example: short
                              examples: {named: {value: named}}
                """,
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      responses:
                        "200":
                          description: OK
                          content:
                            text/plain:
                              schema: {type: string}
                              example: short
                              examples: {named: {value: named}}
                """);

        JsonObject stringSchema = JsonObject.builder().set("type", "string").build();
        JsonObject exampleValue = JsonObject.builder().set("value", "short").build();
        OpenApiDocument.Example namedExample = OpenApiDocument.Example.builder()
                .value(JsonObject.builder().set("value", "named").build())
                .build();
        List<OpenApiDocument> invalidGeneratedDocuments = List.of(
                OpenApiDocument.builder()
                        .info("API", "1")
                        .path("/items", path -> path.operation("GET", operation -> operation
                                .parameter(parameter -> parameter.name("query")
                                        .in("query")
                                        .schema(stringSchema)
                                        .example(exampleValue)
                                        .example("named", namedExample))
                                .response("200", "OK")))
                        .build(),
                OpenApiDocument.builder()
                        .info("API", "1")
                        .path("/items", path -> path.operation("GET", operation -> operation
                                .response("200", response -> response.description("OK")
                                        .header("X-Test", header -> header.schema(stringSchema)
                                                .example(exampleValue)
                                                .example("named", namedExample)))))
                        .build(),
                OpenApiDocument.builder()
                        .info("API", "1")
                        .path("/items", path -> path.operation("GET", operation -> operation
                                .response("200", response -> response.description("OK")
                                        .content("text/plain", mediaType -> mediaType.schema(stringSchema)
                                                .example(exampleValue)
                                                .example("named", namedExample)))))
                        .build());

        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            for (String invalidDocument : invalidStaticDocuments) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.parse(context(version),
                                            invalidDocument.formatted(version.version()),
                                            MediaTypes.APPLICATION_OPENAPI_YAML));
                assertThat(thrown.getMessage(), containsString("cannot combine example with examples"));
            }
            for (OpenApiDocument invalidDocument : invalidGeneratedDocuments) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.render(context(version), invalidDocument));
                assertThat(thrown.getMessage(), containsString("cannot combine example with examples"));
            }
        }
    }

    @Test
    void validatesParameterAndHeaderContentCardinalityAcrossVersions() {
        List<String> invalidStaticDocuments = List.of(
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      parameters:
                        - name: query
                          in: query
                          content: {}
                      responses:
                        "200": {description: OK}
                """,
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      parameters:
                        - name: query
                          in: query
                          content:
                            application/json: {}
                            text/plain: {}
                      responses:
                        "200": {description: OK}
                """,
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      responses:
                        "200":
                          description: OK
                          headers:
                            X-Test: {content: {}}
                """,
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      responses:
                        "200":
                          description: OK
                          headers:
                            X-Test:
                              content:
                                application/json: {}
                                text/plain: {}
                """);
        String validStaticDocument = """
                openapi: %s
                info: {title: API, version: "1"}
                paths:
                  /items:
                    get:
                      parameters:
                        - name: query
                          in: query
                          content: {application/json: {}}
                      responses:
                        "200":
                          description: OK
                          headers:
                            X-Test: {content: {text/plain: {}}}
                """;

        Map<String, Object> multipleContent = Map.of("application/json", Map.of(),
                                                     "text/plain", Map.of());
        List<OpenApiDocument> invalidGeneratedDocuments = List.of(
                contentDocument(false, multipleContent),
                contentDocument(true, multipleContent));
        OpenApiDocument validGeneratedDocument = OpenApiDocument.builder()
                .info("API", "1")
                .path("/items", path -> path.operation("GET", operation -> operation
                        .parameter(parameter -> parameter.name("query")
                                .in("query")
                                .content("application/json", _ -> { }))
                        .response("200", response -> response.description("OK")
                                .header("X-Test", header -> header.content("text/plain", _ -> { })))))
                .build();

        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            for (String invalidStaticDocument : invalidStaticDocuments) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.parse(context(version),
                                            invalidStaticDocument.formatted(version.version()),
                                            MediaTypes.APPLICATION_OPENAPI_YAML));
                assertThat(thrown.getMessage(), containsString("content must contain exactly one entry"));
            }
            for (OpenApiDocument invalidGeneratedDocument : invalidGeneratedDocuments) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.render(context(version), invalidGeneratedDocument));
                assertThat(thrown.getMessage(), containsString("content must contain exactly one entry"));
            }
            version.parse(context(version),
                          validStaticDocument.formatted(version.version()),
                          MediaTypes.APPLICATION_OPENAPI_YAML);
            version.render(context(version), validGeneratedDocument);
        }
    }

    @Test
    void validatesLinkOperationChoiceAcrossVersions() {
        List<String> invalidLinks = List.of("{}",
                                            "{operationRef: '#/paths/~1items/get', operationId: getItems}");
        List<OpenApiDocument> invalidGenerated = List.of(
                OpenApiDocument.builder()
                        .info("API", "1")
                        .paths(Map.of())
                        .components(components -> components.link("Invalid", _ -> { }))
                        .build(),
                OpenApiDocument.builder()
                        .info("API", "1")
                        .paths(Map.of())
                        .components(components -> components.link("Invalid", link -> link
                                .operationRef("#/paths/~1items/get")
                                .operationId("getItems")))
                        .build());

        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            for (String invalidLink : invalidLinks) {
                String invalidDocument = """
                        openapi: %s
                        info: {title: API, version: "1"}
                        paths: {}
                        components:
                          links:
                            Invalid: %s
                        """.formatted(version.version(), invalidLink);
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.parse(context(version),
                                            invalidDocument,
                                            MediaTypes.APPLICATION_OPENAPI_YAML));
                assertThat(thrown.getMessage(), containsString("requires exactly one of operationRef or operationId"));
            }
            for (OpenApiDocument invalidDocument : invalidGenerated) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.render(context(version), invalidDocument));
                assertThat(thrown.getMessage(), containsString("requires exactly one of operationRef or operationId"));
            }
        }
    }

    @Test
    void validatesExampleValueChoicesAcrossVersions() {
        JsonObject value = JsonObject.builder().set("item", "value").build();
        OpenApiDocument valueAndExternal = exampleDocument(OpenApiDocument.Example.builder()
                                                                   .value(value)
                                                                   .externalValue("https://example.com/value.json")
                                                                   .build());
        String parsedValueAndExternal = "{value: local, externalValue: https://example.com/value.json}";

        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            assertInvalidExample(version, parsedValueAndExternal);
            assertInvalidExample(version, valueAndExternal);
        }

        OpenApiVersion version32 = OpenApi32Version.create();
        for (String invalidExample : List.of("{value: local, dataValue: structured}",
                                             "{value: local, serializedValue: serialized}",
                                             "{serializedValue: serialized, externalValue: https://example.com/value}")) {
            assertInvalidExample(version32, invalidExample);
        }
        for (OpenApiDocument invalidExample : List.of(
                exampleDocument(OpenApiDocument.Example.builder().value(value).dataValue(value).build()),
                exampleDocument(OpenApiDocument.Example.builder().value(value).serializedValue("serialized").build()),
                exampleDocument(OpenApiDocument.Example.builder()
                                        .serializedValue("serialized")
                                        .externalValue("https://example.com/value")
                                        .build()))) {
            assertInvalidExample(version32, invalidExample);
        }

        for (String validExample : List.of("{dataValue: structured, serializedValue: serialized}",
                                           "{dataValue: structured, externalValue: https://example.com/value}")) {
            OpenApiDocument document = parseExample(version32, validExample);
            version32.render(context(version32), document);
        }
        for (OpenApiDocument validExample : List.of(
                exampleDocument(OpenApiDocument.Example.builder().dataValue(value).serializedValue("serialized").build()),
                exampleDocument(OpenApiDocument.Example.builder()
                                        .dataValue(value)
                                        .externalValue("https://example.com/value")
                                        .build()))) {
            version32.render(context(version32), validExample);
        }

        OpenApiDocument newerGenerated = exampleDocument(OpenApiDocument.Example.builder()
                                                                  .value(value)
                                                                  .dataValue(value)
                                                                  .serializedValue("serialized")
                                                                  .build());
        for (OpenApiVersion olderVersion : List.of(OpenApi30Version.create(), OpenApi31Version.create())) {
            OpenApiDocument newerParsed = parseExample(
                    olderVersion,
                    "{value: local, dataValue: structured, serializedValue: serialized}");
            olderVersion.render(context(olderVersion), newerParsed);
            olderVersion.render(context(olderVersion), newerGenerated);
        }
    }

    @Test
    void validatesBooleanSchemasAcrossVersions() {
        String directBooleanSchema = """
                openapi: %s
                info: {title: API, version: "1"}
                paths: {}
                components:
                  schemas:
                    Item: true
                """;
        String nestedBooleanSchema = """
                openapi: %s
                info: {title: API, version: "1"}
                paths: {}
                components:
                  schemas:
                    Item:
                      type: object
                      properties:
                        value: false
                """;

        OpenApiVersion version30 = OpenApi30Version.create();
        for (String invalid : List.of(directBooleanSchema, nestedBooleanSchema)) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> version30.parse(context(version30),
                                          invalid.formatted(version30.version()),
                                          MediaTypes.APPLICATION_OPENAPI_YAML));
            assertThat(thrown.getMessage(), containsString("must be an object"));
        }

        for (OpenApiVersion version : List.of(OpenApi31Version.create(), OpenApi32Version.create())) {
            for (String valid : List.of(directBooleanSchema, nestedBooleanSchema)) {
                OpenApiDocument document = version.parse(context(version),
                                                         valid.formatted(version.version()),
                                                         MediaTypes.APPLICATION_OPENAPI_YAML);
                version.render(context(version), document);
            }
        }

        OpenApiDocument additionalProperties = version30.parse(
                context(version30),
                """
                openapi: %s
                info: {title: API, version: "1"}
                paths: {}
                components:
                  schemas:
                    Item:
                      type: object
                      additionalProperties: false
                """.formatted(version30.version()),
                MediaTypes.APPLICATION_OPENAPI_YAML);
        version30.render(context(version30), additionalProperties);
    }

    @Test
    void treatsAdditionalItemsAccordingToSchemaDialect() {
        String document = """
                openapi: %s
                info: {title: API, version: "1"}
                paths: {}
                components:
                  schemas:
                    Item:
                      additionalItems: annotation
                """;

        OpenApiVersion version30 = OpenApi30Version.create();
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> version30.parse(context(version30),
                                      document.formatted(version30.version()),
                                      MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThat(thrown.getMessage(), containsString("components.schemas.Item.additionalItems"));
        assertThat(thrown.getMessage(), containsString("must be an object or boolean"));

        for (OpenApiVersion version : List.of(OpenApi31Version.create(), OpenApi32Version.create())) {
            OpenApiDocument parsed = version.parse(context(version),
                                                   document.formatted(version.version()),
                                                   MediaTypes.APPLICATION_OPENAPI_YAML);
            Map<String, Object> rendered = parse(version.render(context(version), parsed));
            Map<String, Object> schema = map(map(rendered, "components"), "schemas");
            assertThat(map(schema, "Item").get("additionalItems"), is("annotation"));
        }
    }

    @Test
    void rejectsWrongRecognizedFieldTypesAcrossVersions() {
        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            List<String> invalidDocuments = List.of(
                    """
                    openapi: %s
                    info:
                      title: API
                      version: "1"
                      description: 42
                    paths: {}
                    """.formatted(version.version()),
                    """
                    openapi: %s
                    info: {title: API, version: "1"}
                    paths:
                      /items:
                        get:
                          parameters:
                            - name: query
                              in: query
                              required: "true"
                              schema: {type: string}
                          responses:
                            "200": {description: OK}
                    """.formatted(version.version()),
                    """
                    openapi: %s
                    info: {title: API, version: "1"}
                    paths:
                      /items:
                        $ref: https://example.com/path-item
                        servers: not-an-array
                    """.formatted(version.version()));

            for (String invalidDocument : invalidDocuments) {
                IllegalStateException thrown = assertThrows(
                        IllegalStateException.class,
                        () -> version.parse(context(version), invalidDocument, MediaTypes.APPLICATION_OPENAPI_YAML));
                assertThat(version.version(), thrown.getMessage(), containsString("must be"));
            }
        }
    }

    @Test
    void rejectsNonObjectComponentEntriesAcrossVersions() {
        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            List<String> componentFields = switch (version.version().substring(0, 3)) {
            case "3.0" -> List.of("responses",
                                  "parameters",
                                  "examples",
                                  "requestBodies",
                                  "headers",
                                  "securitySchemes",
                                  "links",
                                  "callbacks");
            case "3.1" -> List.of("responses",
                                  "parameters",
                                  "examples",
                                  "requestBodies",
                                  "headers",
                                  "securitySchemes",
                                  "links",
                                  "callbacks",
                                  "pathItems");
            default -> List.of("responses",
                               "parameters",
                               "examples",
                               "requestBodies",
                               "headers",
                               "securitySchemes",
                               "links",
                               "callbacks",
                               "pathItems",
                               "mediaTypes");
            };
            for (String componentField : componentFields) {
                for (String invalidValue : List.of("not-an-object", "null")) {
                    String invalidDocument = """
                            openapi: %s
                            info: {title: API, version: "1"}
                            paths: {}
                            components:
                              %s:
                                Invalid: %s
                            """.formatted(version.version(), componentField, invalidValue);
                    IllegalStateException thrown = assertThrows(
                            IllegalStateException.class,
                            () -> version.parse(context(version),
                                                invalidDocument,
                                                MediaTypes.APPLICATION_OPENAPI_YAML));
                    assertThat(componentField,
                               thrown.getMessage(),
                               containsString("components." + componentField + ".Invalid"));
                    assertThat(componentField, thrown.getMessage(), containsString("must be an object"));
                }
            }
        }

        OpenApiVersion version32 = OpenApi32Version.create();
        version32.parse(context(version32),
                        """
                        openapi: 3.2.0
                        info: {title: API, version: "1"}
                        paths: {}
                        components:
                          responses: {Empty: {}}
                          examples: {Empty: {}}
                          links:
                            Empty:
                              operationRef: https://example.com/openapi.yaml#/paths/~1target/get
                          callbacks: {Empty: {}}
                          pathItems: {Empty: {}}
                          mediaTypes: {Empty: {}}
                        """,
                        MediaTypes.APPLICATION_OPENAPI_YAML);
    }

    @Test
    void rejectsNonObjectWalkedNodesAcrossVersions() {
        for (OpenApiVersion version : List.of(OpenApi30Version.create(),
                                              OpenApi31Version.create(),
                                              OpenApi32Version.create())) {
            Map<String, String> invalidDocuments = Map.ofEntries(
                    Map.entry("paths./items", """
                            openapi: %s
                            info: {title: API, version: "1"}
                            paths: {/items: not-an-object}
                            """),
                    Map.entry("servers[0]", """
                            openapi: %s
                            info: {title: API, version: "1"}
                            servers: [not-an-object]
                            paths: {}
                            """),
                    Map.entry("paths./items.get.servers[0]", """
                            openapi: %s
                            info: {title: API, version: "1"}
                            paths:
                              /items:
                                get:
                                  servers: [not-an-object]
                                  responses: {"200": {description: OK}}
                            """),
                    Map.entry("paths./items.get.parameters[0].examples.Invalid", """
                            openapi: %s
                            info: {title: API, version: "1"}
                            paths:
                              /items:
                                get:
                                  parameters:
                                    - name: query
                                      in: query
                                      schema: {type: string}
                                      examples: {Invalid: not-an-object}
                                  responses: {"200": {description: OK}}
                            """),
                    Map.entry("paths./items.get.responses.200.content.application/json", """
                            openapi: %s
                            info: {title: API, version: "1"}
                            paths:
                              /items:
                                get:
                                  responses:
                                    "200":
                                      description: OK
                                      content: {application/json: not-an-object}
                            """),
                    Map.entry("paths./items.get.responses.200.content.multipart/form-data.encoding.value", """
                            openapi: %s
                            info: {title: API, version: "1"}
                            paths:
                              /items:
                                get:
                                  responses:
                                    "200":
                                      description: OK
                                      content:
                                        multipart/form-data:
                                          schema: {type: object}
                                          encoding: {value: not-an-object}
                            """),
                    Map.entry("paths./items.get.responses.200.links.Invalid", """
                            openapi: %s
                            info: {title: API, version: "1"}
                            paths:
                              /items:
                                get:
                                  responses:
                                    "200":
                                      description: OK
                                      links: {Invalid: not-an-object}
                            """),
                    Map.entry("paths./items.get.callbacks.Invalid", """
                            openapi: %s
                            info: {title: API, version: "1"}
                            paths:
                              /items:
                                get:
                                  callbacks:
                                    Invalid:
                                      '{$request.body#/callback}': not-an-object
                                  responses: {"200": {description: OK}}
                            """));
            invalidDocuments.forEach((location, document) -> assertNonObjectRejected(
                    version,
                    location,
                    document.formatted(version.version())));
        }

        OpenApiVersion version32 = OpenApi32Version.create();
        assertNonObjectRejected(version32,
                                "paths./items.additionalOperations.COPY",
                                """
                                openapi: 3.2.0
                                info: {title: API, version: "1"}
                                paths:
                                  /items:
                                    additionalOperations: {COPY: not-an-object}
                                """);
        assertNonObjectRejected(version32,
                                "prefixEncoding[0]",
                                """
                                openapi: 3.2.0
                                info: {title: API, version: "1"}
                                paths:
                                  /items:
                                    get:
                                      responses:
                                        "200":
                                          content:
                                            multipart/form-data:
                                              itemSchema: {type: string}
                                              prefixEncoding: [not-an-object]
                                """);
    }

    @Test
    void filtersUnsupportedObjectShapesBeforeWalking() {
        OpenApiVersion version30 = OpenApi30Version.create();
        OpenApiDocument document30 = version30.parse(context(version30),
                                                     """
                                                     openapi: 3.0.3
                                                     info: {title: API, version: "1"}
                                                     paths:
                                                       /items:
                                                         additionalOperations: {COPY: not-an-object}
                                                     webhooks: {Invalid: not-an-object}
                                                     components:
                                                       pathItems: {Invalid: not-an-object}
                                                       mediaTypes: {Invalid: not-an-object}
                                                     """,
                                                     MediaTypes.APPLICATION_OPENAPI_YAML);
        version30.render(context(version30), document30);

        OpenApiVersion version31 = OpenApi31Version.create();
        OpenApiDocument document31 = version31.parse(context(version31),
                                                     """
                                                     openapi: 3.1.1
                                                     info: {title: API, version: "1"}
                                                     paths:
                                                       /items:
                                                         additionalOperations: {COPY: not-an-object}
                                                     components:
                                                       mediaTypes: {Invalid: not-an-object}
                                                     """,
                                                     MediaTypes.APPLICATION_OPENAPI_YAML);
        version31.render(context(version31), document31);
    }

    @Test
    void preservesEmptyRequiredNames() {
        OpenApi32Version version = OpenApi32Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = version.parse(context,
                                                 """
                                                 openapi: 3.2.0
                                                 info:
                                                   title: API
                                                   version: "1"
                                                   license:
                                                     name: ""
                                                 tags:
                                                   - name: " "
                                                 paths:
                                                   /items:
                                                     get:
                                                       parameters:
                                                         - name: ""
                                                           in: query
                                                           schema: {type: string}
                                                       responses:
                                                         "200": {description: OK}
                                                 """,
                                                 MediaTypes.APPLICATION_OPENAPI_YAML);

        Map<String, Object> rendered = parse(version.render(context, document));
        assertThat(map(map(rendered, "info"), "license").get("name"), is(""));
        assertThat(((Map<?, ?>) ((List<?>) rendered.get("tags")).getFirst()).get("name"), is(" "));
        Map<?, ?> operation = map(map(map(rendered, "paths"), "/items"), "get");
        assertThat(((Map<?, ?>) ((List<?>) operation.get("parameters")).getFirst()).get("name"), is(""));
    }

    @Test
    void preservesEmptyInfoStrings() {
        OpenApi32Version version = OpenApi32Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = version.parse(context,
                                                 """
                                                 openapi: 3.2.0
                                                 info:
                                                   title: ""
                                                   version: " "
                                                 webhooks: {}
                                                 """,
                                                 MediaTypes.APPLICATION_OPENAPI_YAML);

        assertThat(document.info().orElseThrow().title(), is(""));
        assertThat(document.info().orElseThrow().version(), is(" "));

        Map<?, ?> renderedInfo = map(parse(version.render(context, document)), "info");
        assertThat(renderedInfo.get("title"), is(""));
        assertThat(renderedInfo.get("version"), is(" "));
    }

    @Test
    void requiresInfoWhenRendering() {
        OpenApi32Version version = OpenApi32Version.create();
        OpenApiDocument withoutInfo = OpenApiDocument.builder()
                .paths(Map.of())
                .build();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> version.render(context(version), withoutInfo));
        assertThat(thrown.getMessage(), containsString("requires Info metadata"));
    }

    @Test
    void requiresPathsComponentsOrWebhooksWhenRendering() {
        OpenApi32Version version = OpenApi32Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument infoOnly = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> version.render(context, infoOnly));
        assertThat(thrown.getMessage(), containsString("requires at least one of paths, components, or webhooks"));

        OpenApiDocument emptyWebhooks = version.parse(context,
                                                      """
                                                      openapi: 3.2.0
                                                      info:
                                                        title: Generated API
                                                        version: 1.0.0
                                                      webhooks: {}
                                                      """,
                                                      MediaTypes.APPLICATION_OPENAPI_YAML);
        assertThat(parse(version.render(context, emptyWebhooks)).containsKey("webhooks"), is(true));
    }

    @Test
    void parsesAndRendersOpenApi32Fields() {
        OpenApi32Version version = OpenApi32Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = version.parse(context, static32(), MediaTypes.APPLICATION_OPENAPI_YAML);

        Map<String, Object> rendered = parse(version.render(context, document));

        assertThat(rendered.get("openapi"), is("3.2.0"));
        assertThat(rendered.get("$self"), is("https://example.com/openapi/static-3.2.yaml"));

        Map<?, ?> server = (Map<?, ?>) ((List<?>) rendered.get("servers")).getFirst();
        assertThat(server.get("name"), is("local"));

        Map<?, ?> secondTag = (Map<?, ?>) ((List<?>) rendered.get("tags")).get(1);
        assertThat(secondTag.get("summary"), is("Internal"));
        assertThat(secondTag.get("parent"), is("static"));
        assertThat(secondTag.get("kind"), is("badge"));

        Map<?, ?> staticPath = map(map(rendered, "paths"), "/static/{id}");
        assertThat(staticPath.containsKey("query"), is(true));
        assertThat(staticPath.containsKey("additionalOperations"), is(true));
        Map<?, ?> staticResponse = map(map(map(staticPath, "get"), "responses"), "200");
        assertThat(staticResponse.get("summary"), is("Static item"));
        assertThat(map(map(staticResponse, "headers"), "X-Request-Id").containsKey("allowReserved"), is(false));

        Map<?, ?> queryResponse = map(map(staticPath, "query"), "responses");
        assertThat(map(queryResponse, "200").get("summary"), is("Static query stream"));
        Map<?, ?> queryContent = map(map(queryResponse, "200"), "content");
        assertThat(map(queryContent, "application/jsonl").containsKey("itemSchema"), is(true));

        Map<?, ?> search = map(map(map(rendered, "paths"), "/search"), "get");
        Map<?, ?> queryString = (Map<?, ?>) ((List<?>) search.get("parameters")).getFirst();
        assertThat(queryString.get("name"), is("query"));
        assertThat(queryString.get("in"), is("querystring"));
        Map<?, ?> formExample = map(map(map(map(queryString, "content"), "application/x-www-form-urlencoded"), "examples"),
                                    "form");
        assertThat(formExample.containsKey("dataValue"), is(true));
        assertThat(formExample.get("serializedValue"), is("q=static+item&active=true"));

        Map<?, ?> securityScheme = map(map(map(rendered, "components"), "securitySchemes"), "bearerAuth");
        assertThat(securityScheme.get("deprecated"), is(true));
        Map<?, ?> oauthFlows = map(map(map(map(rendered, "components"), "securitySchemes"), "oauthDevice"), "flows");
        assertThat(oauthFlows.containsKey("deviceAuthorization"), is(true));
    }

    @Test
    void rendersOpenApi32StaticDocumentAsOpenApi31() {
        OpenApi32Version parseVersion = OpenApi32Version.create();
        OpenApiDocument document = parseVersion.parse(context(parseVersion),
                                                      static32WithoutDeviceAuthorization(),
                                                      MediaTypes.APPLICATION_OPENAPI_YAML);

        OpenApi31Version renderVersion = OpenApi31Version.create();
        Map<String, Object> rendered = parse(renderVersion.render(context(renderVersion), document));

        assertThat(rendered.get("openapi"), is("3.1.1"));
        assertThat(rendered.containsKey("$self"), is(false));
        assertThat(rendered.get("jsonSchemaDialect"), is("https://spec.openapis.org/oas/3.1/dialect/base"));

        Map<?, ?> server = (Map<?, ?>) ((List<?>) rendered.get("servers")).getFirst();
        assertThat(server.containsKey("name"), is(false));

        Map<?, ?> secondTag = (Map<?, ?>) ((List<?>) rendered.get("tags")).get(1);
        assertThat(secondTag.containsKey("summary"), is(false));
        assertThat(secondTag.containsKey("parent"), is(false));
        assertThat(secondTag.containsKey("kind"), is(false));

        Map<?, ?> staticPath = map(map(rendered, "paths"), "/static/{id}");
        assertThat(staticPath.containsKey("query"), is(false));
        assertThat(staticPath.containsKey("additionalOperations"), is(false));
        Map<?, ?> staticResponse = map(map(map(staticPath, "get"), "responses"), "200");
        assertThat(staticResponse.containsKey("summary"), is(false));
        assertThat(map(map(staticResponse, "headers"), "X-Request-Id").containsKey("allowReserved"), is(false));

        Map<?, ?> search = map(map(map(rendered, "paths"), "/search"), "get");
        assertThat(search.get("parameters"), is(List.of()));

        Map<?, ?> securityScheme = map(map(map(rendered, "components"), "securitySchemes"), "bearerAuth");
        assertThat(securityScheme.containsKey("deprecated"), is(false));
        Map<?, ?> oauthFlows = map(map(map(map(rendered, "components"), "securitySchemes"), "oauthDevice"), "flows");
        assertThat(oauthFlows.containsKey("deviceAuthorization"), is(false));
        assertThat(oauthFlows.containsKey("authorizationCode"), is(true));
    }

    @Test
    void rendersOpenApi32StaticDocumentAsOpenApi30() {
        OpenApi32Version parseVersion = OpenApi32Version.create();
        OpenApiDocument document = parseVersion.parse(context(parseVersion),
                                                      static32WithoutDeviceAuthorization(),
                                                      MediaTypes.APPLICATION_OPENAPI_YAML);

        OpenApi30Version renderVersion = OpenApi30Version.create();
        Map<String, Object> rendered = parse(renderVersion.render(context(renderVersion), document));

        assertThat(rendered.get("openapi"), is("3.0.3"));
        assertThat(rendered.containsKey("$self"), is(false));
        assertThat(rendered.containsKey("jsonSchemaDialect"), is(false));

        Map<?, ?> staticPath = map(map(rendered, "paths"), "/static/{id}");
        assertThat(staticPath.containsKey("query"), is(false));
        assertThat(staticPath.containsKey("additionalOperations"), is(false));
        Map<?, ?> staticResponse = map(map(map(staticPath, "get"), "responses"), "200");
        assertThat(staticResponse.containsKey("summary"), is(false));
        assertThat(map(map(staticResponse, "headers"), "X-Request-Id").containsKey("allowReserved"), is(false));

        Map<?, ?> search = map(map(map(rendered, "paths"), "/search"), "get");
        assertThat(search.get("parameters"), is(List.of()));

        Map<String, Object> status = schemaProperty(rendered, "StaticItem", "status");
        assertThat(status.get("type"), is("string"));
        assertThat(status.get("nullable"), is(true));
        assertThat(((List<?>) status.get("enum")).contains(null), is(true));

        Map<String, Object> mode = schemaProperty(rendered, "StaticItem", "mode");
        assertThat(mode.containsKey("const"), is(false));
        assertThat(mode.get("enum"), is(List.of("modern")));

        assertThat(schemaPropertyValue(rendered, "StaticItem", "payload"), is(Map.of()));

        Map<?, ?> securitySchemes = map(map(rendered, "components"), "securitySchemes");
        Map<?, ?> oauthFlows = map(map(securitySchemes, "oauthDevice"), "flows");
        assertThat(oauthFlows.containsKey("deviceAuthorization"), is(false));
        assertThat(oauthFlows.containsKey("authorizationCode"), is(true));
    }

    @Test
    void rejectsOpenApi32DeviceAuthorizationWhenRenderingOpenApi31() {
        OpenApi32Version parseVersion = OpenApi32Version.create();
        OpenApiDocument document = parseVersion.parse(context(parseVersion), static32(), MediaTypes.APPLICATION_OPENAPI_YAML);

        OpenApi31Version renderVersion = OpenApi31Version.create();
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                   () -> renderVersion.render(context(renderVersion), document));

        assertThat(thrown.getMessage(), containsString("deviceAuthorization"));
    }

    @Test
    void rejectsOpenApi32DeviceAuthorizationWhenRenderingOpenApi30() {
        OpenApi32Version parseVersion = OpenApi32Version.create();
        OpenApiDocument document = parseVersion.parse(context(parseVersion), static32(), MediaTypes.APPLICATION_OPENAPI_YAML);

        OpenApi30Version renderVersion = OpenApi30Version.create();
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                   () -> renderVersion.render(context(renderVersion), document));

        assertThat(thrown.getMessage(), containsString("deviceAuthorization"));
    }

    @Test
    void arbitraryHttpMethodUsesAdditionalOperations() {
        OpenApiDocument document = OpenApiDocument.builder()
                .info("Generated API", "1.0.0")
                .path("/static/{id}",
                      path -> path.parameter(parameter -> parameter
                                      .name("id")
                                      .in("path")
                                      .required(true)
                                      .schema(JsonObject.builder().set("type", "string").build()))
                              .operation("COPY",
                                         operation -> operation.operationId("copyStatic")
                                                 .response("200", "Copied.")))
                .build();

        OpenApi32Version version32 = OpenApi32Version.create();
        Map<String, Object> rendered32 = parse(version32.render(context(version32), document));
        Map<?, ?> path32 = map(map(rendered32, "paths"), "/static/{id}");
        assertThat(path32.containsKey("copy"), is(false));
        assertThat(map(path32, "additionalOperations").containsKey("COPY"), is(true));

        OpenApi30Version version30 = OpenApi30Version.create();
        Map<String, Object> rendered30 = parse(version30.render(context(version30), document));
        Map<?, ?> path30 = map(map(rendered30, "paths"), "/static/{id}");
        assertThat(path30.containsKey("copy"), is(false));
        assertThat(path30.containsKey("additionalOperations"), is(false));
    }

    @Test
    void rejectsFixedMethodInParsedAdditionalOperations() {
        OpenApi32Version version = OpenApi32Version.create();

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> version.parse(context(version),
                                    """
                                    openapi: 3.2.0
                                    info:
                                      title: Static API
                                      version: 1.0.0
                                    paths:
                                      /static:
                                        additionalOperations:
                                          POST:
                                            responses:
                                              "200":
                                                description: Static response.
                                    """,
                                    MediaTypes.APPLICATION_OPENAPI_YAML));

        assertThat(thrown.getMessage(), containsString("fixed-field HTTP method: POST"));
    }

    @Test
    void preservesCaseSensitiveCustomMethodsInParsedAdditionalOperations() {
        OpenApi32Version version = OpenApi32Version.create();
        OpenApiDocument document = version.parse(context(version),
                                                 """
                                                 openapi: 3.2.0
                                                 info:
                                                   title: Static API
                                                   version: 1.0.0
                                                 paths:
                                                   /static:
                                                     post:
                                                       responses:
                                                         default:
                                                           description: Fixed POST response
                                                     additionalOperations:
                                                       post:
                                                         responses:
                                                           default:
                                                             description: Lowercase post response
                                                       PoSt:
                                                         responses:
                                                           default:
                                                             description: Mixed-case PoSt response
                                                 """,
                                                 MediaTypes.APPLICATION_OPENAPI_YAML);

        Map<String, Object> rendered = parse(version.render(context(version), document));
        Map<?, ?> path = map(map(rendered, "paths"), "/static");
        assertThat(path.containsKey("post"), is(true));
        Map<?, ?> additionalOperations = map(path, "additionalOperations");
        assertThat(additionalOperations.keySet(), is(Set.of("post", "PoSt")));
    }

    @Test
    void parsesOnlyOpenApi32Documents() {
        OpenApi32Version version = OpenApi32Version.create();

        assertThrows(IllegalStateException.class,
                     () -> version.parse(context(version),
                                         """
                                         openapi: 3.1.0
                                         info:
                                           title: Static API
                                           version: 1.0.0
                                         """,
                                         MediaTypes.APPLICATION_OPENAPI_YAML));
    }

    @Test
    void rejectsNullArguments() {
        OpenApi32Version version = OpenApi32Version.create();
        OpenApiDocumentContext context = context(version);
        OpenApiDocument document = OpenApiDocument.builder().build();

        assertThrows(NullPointerException.class, () -> OpenApi32Version.create((OpenApi32VersionConfig) null));
        assertThrows(NullPointerException.class, () -> version.parse(null, "", MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThrows(NullPointerException.class, () -> version.parse(context, null, MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThrows(NullPointerException.class, () -> version.parse(context, "", null));
        assertThrows(NullPointerException.class, () -> version.render(null, document));
        assertThrows(NullPointerException.class, () -> version.render(context, null));
    }

    @Test
    void validatesConfiguredVersion() {
        assertThat(OpenApi32Version.builder().version("3.2.99").build().version(), is("3.2.99"));
        assertThat(OpenApi32Version.builder().version("3.2.0-beta").build().version(), is("3.2.0-beta"));

        for (String invalidVersion : List.of("3.2", "3.2.", "3.2.not-a-version", "3.2.1-", "3.2.1.0", "3.1.0")) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                       () -> OpenApi32Version.builder()
                                                               .version(invalidVersion)
                                                               .build(),
                                                       invalidVersion);
            assertThat(invalidVersion, ex.getMessage(), containsString("3.2"));
            assertThat(invalidVersion, ex.getMessage(), containsString(invalidVersion));
        }
    }

    @Test
    void serviceLoaderDiscoversProvider() {
        boolean found = ServiceLoader.load(OpenApiVersionProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(provider -> "3.2".equals(provider.configKey()));

        assertThat(found, is(true));
    }

    private static void assertNonObjectRejected(OpenApiVersion version, String location, String document) {
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> version.parse(context(version), document, MediaTypes.APPLICATION_OPENAPI_YAML));
        assertThat(location, thrown.getMessage(), containsString(location));
        assertThat(location, thrown.getMessage(), containsString("must be an object"));
    }

    private static OpenApiDocument exampleDocument(OpenApiDocument.Example example) {
        return OpenApiDocument.builder()
                .info("API", "1")
                .paths(Map.of())
                .components(components -> components.example("Example", example))
                .build();
    }

    private static OpenApiDocument parseExample(OpenApiVersion version, String example) {
        return version.parse(context(version),
                             """
                             openapi: %s
                             info: {title: API, version: "1"}
                             paths: {}
                             components:
                               examples:
                                 Example: %s
                             """.formatted(version.version(), example),
                             MediaTypes.APPLICATION_OPENAPI_YAML);
    }

    private static void assertInvalidExample(OpenApiVersion version, String example) {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> parseExample(version, example));
        assertThat(thrown.getMessage(), containsString("cannot combine"));
    }

    private static void assertInvalidExample(OpenApiVersion version, OpenApiDocument document) {
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> version.render(context(version), document));
        assertThat(thrown.getMessage(), containsString("cannot combine"));
    }

    private static OpenApiDocument contentDocument(boolean header, Map<String, Object> content) {
        Map<String, Object> response = header
                ? Map.of("description", "OK", "headers", Map.of("X-Test", Map.of("content", content)))
                : Map.of("description", "OK");
        Map<String, Object> operation = header
                ? Map.of("responses", Map.of("200", response))
                : Map.of("parameters", List.of(Map.of("name", "query", "in", "query", "content", content)),
                         "responses", Map.of("200", response));
        return OpenApiDocumentReader.read(OpenApiDocumentMapperSupport.jsonObject(Map.of(
                "info", Map.of("title", "API", "version", "1"),
                "paths", Map.of("/items", Map.of("get", operation)))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String content) {
        return new Yaml().load(content);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperty(Map<String, Object> document, String schemaName, String propertyName) {
        return (Map<String, Object>) schemaPropertyValue(document, schemaName, propertyName);
    }

    private static Object schemaPropertyValue(Map<String, Object> document, String schemaName, String propertyName) {
        return map(map(map(map(document, "components"), "schemas"), schemaName), "properties")
                .get(propertyName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<?, ?> map, String name) {
        return (Map<String, Object>) map.get(name);
    }

    private static OpenApiDocumentContext context(OpenApiVersion version) {
        return new TestOpenApiDocumentContext(version);
    }

    private static String static32() {
        try (InputStream is = OpenApi32VersionTest.class.getResourceAsStream("/static-3.2.yaml")) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: static-3.2.yaml");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String static32WithoutDeviceAuthorization() {
        Map<String, Object> document = parse(static32());
        Map<String, Object> securityScheme = map(map(map(document, "components"), "securitySchemes"), "oauthDevice");
        map(securityScheme, "flows").remove("deviceAuthorization");
        return new Yaml().dump(document);
    }

    private record TestOpenApiDocumentContext(OpenApiVersion openApiVersion) implements OpenApiDocumentContext {
        @Override
        public String featureName() {
            return "openapi";
        }

        @Override
        public String webContext() {
            return "/openapi";
        }

        @Override
        public String listener() {
            return "default";
        }

        @Override
        public OpenApiGeneratedMode generatedMode() {
            return OpenApiGeneratedMode.STATIC_ONLY;
        }
    }
}
