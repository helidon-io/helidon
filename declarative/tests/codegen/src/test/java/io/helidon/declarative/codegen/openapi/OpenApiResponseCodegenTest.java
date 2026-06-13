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
