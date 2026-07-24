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

package io.helidon.declarative.codegen.openapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Api;
import io.helidon.common.Default;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.common.mapper.Mappers;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.types.Annotation;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.http.Http;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.openapi.OpenApi;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpEntryPoint;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.RestServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class OpenApiResponseCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
            Api.class,
            Config.class,
            Default.class,
            Dependency.class,
            Generated.class,
            GenericType.class,
            Handler.class,
            Http.class,
            HttpEntryPoint.class,
            HttpFeature.class,
            HttpRoute.class,
            HttpRouting.class,
            HttpRules.class,
            JsonObject.class,
            JsonString.class,
            LazyValue.class,
            Mappers.class,
            OpenApi.class,
            Parameters.class,
            RestServer.class,
            ServerRequest.class,
            ServerResponse.class,
            Service.class,
            ServiceDescriptor.class,
            UriQuery.class,
            WebServer.class
    );

    @Test
    void optionalResponseWithExplicitSuccessKeepsNotFoundResponse() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-optional-explicit-success"))
                .addSource("OptionalOpenApiEndpoint.java", """
                        package com.example;

                        import java.util.Optional;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/optional")
                        class OptionalOpenApiEndpoint {
                            @Http.GET
                            @Http.Path("/{name}")
                            @OpenApi.Response(status = 200,
                                              description = "Greeting found",
                                              content = @OpenApi.Content)
                            Optional<String> get(@Http.PathParam("name") String name) {
                                return Optional.of(name);
                            }
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = generatedSource(result);
        assertThat(generated, containsString(".response(\"200\", response -> response.description("
                                                     + "io.helidon.openapi.OpenApiDocumentContextSupport"
                                                     + ".resolveExpression(context, \"Greeting found\"))"));
        assertThat(generated, containsString(".response(\"404\", response -> response.description(\"Not Found\"))"));
    }

    @Test
    void responseLinksAreGenerated() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-response-links"))
                .addSource("LinkedOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/linked")
                        class LinkedOpenApiEndpoint {
                            @Http.GET
                            @OpenApi.Response(
                                    status = 200,
                                    description = "Linked",
                                    links = {
                                            @OpenApi.Link(
                                                    name = "follow",
                                                    operationId = "getGreeting",
                                                    parameters = @OpenApi.LinkParameter(
                                                            name = "id",
                                                            value = "$response.body#/id"),
                                                    requestBody = "$response.body",
                                                    description = "Follow the greeting"),
                                            @OpenApi.Link(
                                                    name = "relative",
                                                    operationRef = "#/paths/~1greetings~1{id}/get")
                                    })
                            String get() {
                                return "ok";
                            }
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = generatedSource(result);
        assertThat(generated, containsString(".link(\"follow\", link -> link"));
        assertThat(generated, containsString(".operationId("));
        assertThat(generated, containsString("\"getGreeting\""));
        assertThat(generated, containsString(".parameters(JsonObject.builder().set(\"id\","));
        assertThat(generated, containsString("\"$response.body#/id\""));
        assertThat(generated, containsString(".requestBody(JsonString.create("));
        assertThat(generated, containsString("\"$response.body\""));
        assertThat(generated, containsString("\"Follow the greeting\""));
        assertThat(generated, containsString(".link(\"relative\", link -> link"));
        assertThat(generated, containsString(".operationRef("));
        assertThat(generated, containsString("\"#/paths/~1greetings~1{id}/get\""));
    }

    @Test
    void responseCannotDeclareDuplicateLinkNames() {
        var result = compileInvalidResponseLinks(
                "duplicate-names",
                """
                        @OpenApi.Link(name = "duplicate", operationId = "first"),
                        @OpenApi.Link(name = "duplicate", operationId = "second")
                        """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get for status 200",
                               "cannot define link duplicate more than once");
    }

    @Test
    void responseLinkRequiresOperationTarget() {
        var result = compileInvalidResponseLinks(
                "missing-target",
                """
                        @OpenApi.Link(name = "invalid")
                        """);

        assertCompilationFails(result,
                               "link invalid must define exactly one of operationRef or operationId");
    }

    @Test
    void responseLinkRejectsMultipleOperationTargets() {
        var result = compileInvalidResponseLinks(
                "multiple-targets",
                """
                        @OpenApi.Link(name = "invalid",
                                      operationRef = "${link.ref:}",
                                      operationId = "getGreeting")
                        """);

        assertCompilationFails(result,
                               "link invalid must define exactly one of operationRef or operationId");
    }

    @Test
    void responseLinkRejectsDuplicateParameterNames() {
        var result = compileInvalidResponseLinks(
                "duplicate-parameter-names",
                """
                        @OpenApi.Link(
                                name = "next",
                                operationId = "getGreeting",
                                parameters = {
                                        @OpenApi.LinkParameter(name = "id", value = "$response.body#/id"),
                                        @OpenApi.LinkParameter(name = "id", value = "$request.path.id")
                                })
                        """);

        assertCompilationFails(result,
                               "link next cannot define parameter id more than once");
    }

    @Test
    void responseLinkRejectsInvalidName() {
        var result = compileInvalidResponseLinks(
                "invalid-name",
                """
                        @OpenApi.Link(name = "invalid/name", operationId = "getGreeting")
                        """);

        assertCompilationFails(result,
                               "has invalid link name invalid/name");
    }

    @Test
    void responseLinkRequiresParameterName() {
        var result = compileInvalidResponseLinks(
                "missing-parameter-name",
                """
                        @OpenApi.Link(
                                name = "next",
                                operationId = "getGreeting",
                                parameters = @OpenApi.LinkParameter(name = "", value = "$response.body#/id"))
                        """);

        assertCompilationFails(result,
                               "link next requires a parameter name");
    }

    @Test
    void explicitResponseWithoutContentDoesNotInferMethodReturnContent() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-explicit-bodyless-response"))
                .addSource("BodylessOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/bodyless")
                        class BodylessOpenApiEndpoint {
                            @Http.GET
                            @OpenApi.Response(status = 204, description = "Deleted")
                            String delete() {
                                return "deleted";
                            }
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = generatedSource(result);
        assertThat(generated, containsString(".response(\"204\", response -> response.description("
                                                     + "io.helidon.openapi.OpenApiDocumentContextSupport"
                                                     + ".resolveExpression(context, \"Deleted\"))"));
        assertThat(generated, is(not(containsString(".content("))));
        assertThat(generated, is(not(containsString("JsonSchemaProvider"))));
    }

    @Test
    void responseCannotDeclareDuplicateStatus() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-duplicate-response-status"))
                .addSource("InvalidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidOpenApiEndpoint {
                            @Http.GET
                            @OpenApi.Response(status = 200, description = "First")
                            @OpenApi.Response(status = 200, description = "Second")
                            String get() {
                                return "ok";
                            }
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define response status 200 more than once");
    }

    @Test
    void responseCannotDeclareLowStatus() {
        var result = compileInvalidResponseStatus(99);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "must define an HTTP response status from 100 to 599: 99");
    }

    @Test
    void responseCannotDeclareHighStatus() {
        var result = compileInvalidResponseStatus(600);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "must define an HTTP response status from 100 to 599: 600");
    }

    private static TestCompiler.Result compileInvalidResponseStatus(int status) {
        return TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-invalid-response-status-" + status))
                .addSource("InvalidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidOpenApiEndpoint {
                            @Http.GET
                            @OpenApi.Response(status = %d, description = "Invalid")
                            String get() {
                                return "ok";
                            }
                        }
                        """.formatted(status))
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();
    }

    private static TestCompiler.Result compileInvalidResponseLinks(String testName, String links) {
        return TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-invalid-response-link-" + testName))
                .addSource("InvalidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidOpenApiEndpoint {
                            @Http.GET
                            @OpenApi.Response(
                                    status = 200,
                                    description = "Invalid",
                                    links = {
                                        %s
                                    })
                            String get() {
                                return "ok";
                            }
                        }
                        """.formatted(links))
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();
    }

    private static String generatedSource(TestCompiler.Result result) throws IOException {
        StringBuilder generatedContent = new StringBuilder();
        var generatedSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith(".java"))
                .toList();
        for (Path generatedSource : generatedSources) {
            generatedContent.append(Files.readString(generatedSource, StandardCharsets.UTF_8));
            generatedContent.append('\n');
        }
        return generatedContent.toString();
    }

    private static void assertCompilationFails(TestCompiler.Result result, String... diagnosticParts) {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        for (String diagnosticPart : diagnosticParts) {
            assertThat(diagnostics, containsString(diagnosticPart));
        }
    }
}
