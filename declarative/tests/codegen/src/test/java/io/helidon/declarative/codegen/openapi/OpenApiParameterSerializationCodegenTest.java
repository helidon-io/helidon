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
import io.helidon.json.schema.spi.JsonSchemaProvider;
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

class OpenApiParameterSerializationCodegenTest {
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
            JsonSchemaProvider.class,
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
    void queryParameterCannotOverrideLocation() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-location-override"))
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
                            String get(@OpenApi.Parameter(in = "header")
                                       @Http.QueryParam("value") String value) {
                                return value;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot document a query parameter as header");
    }

    @Test
    void queryParameterCannotOverrideName() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-name-override"))
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
                            String get(@OpenApi.Parameter(name = "documented")
                                       @Http.QueryParam("actual") String value) {
                                return value;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot document a query parameter named actual as documented");
    }

    @Test
    void queryParameterCannotUseExampleAndExamples() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-example-and-examples"))
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
                            String get(@OpenApi.Parameter(example = "one",
                                                          examples = @OpenApi.Example(name = "two",
                                                                                      value = "two"))
                                       @Http.QueryParam("value") String value) {
                                return value;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define both example and examples for query parameter value");
    }

    @Test
    void queryParameterCannotUseContentAndStyle() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-content-and-style"))
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
                            String get(@OpenApi.Parameter(content = @OpenApi.Content,
                                                          style = OpenApi.Style.FORM)
                                       @Http.QueryParam("value") String value) {
                                return value;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define style when content is defined for query parameter value");
    }

    @Test
    void queryParameterCannotUseContentAndExplode() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-content-and-explode"))
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
                            String get(@OpenApi.Parameter(content = @OpenApi.Content,
                                                          explode = OpenApi.Explode.FALSE)
                                       @Http.QueryParam("value") String value) {
                                return value;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define explode when content is defined for query parameter value");
    }

    @Test
    void queryParameterCannotUseMultipleContentEntries() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-multiple-content"))
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
                            String get(@OpenApi.Parameter(content = {@OpenApi.Content("application/json"),
                                                                     @OpenApi.Content("text/plain")})
                                       @Http.QueryParam("value") String value) {
                                return value;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define more than one content entry for query parameter value");
    }

    @Test
    void headerParameterCannotUseQueryStyle() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-header-query-style"))
                .addSource("InvalidOpenApiEndpoint.java", """
                        package com.example;

                        import java.util.List;

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
                            String get(@OpenApi.Parameter(style = OpenApi.Style.PIPE_DELIMITED)
                                       @Http.HeaderParam("X-Value") List<String> values) {
                                return values.toString();
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot use pipeDelimited style for a header parameter");
    }

    @Test
    void scalarQueryParameterCannotUseArrayStyle() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-scalar-query-array-style"))
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
                            String get(@OpenApi.Parameter(style = OpenApi.Style.PIPE_DELIMITED)
                                       @Http.QueryParam("value") String value) {
                                return value;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot use pipeDelimited style for a scalar query parameter");
    }

    @Test
    void pathParameterCannotUseQueryStyle() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-path-query-style"))
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
                            @Http.Path("/{id}")
                            String get(@OpenApi.Parameter(style = OpenApi.Style.FORM)
                                       @Http.PathParam("id") String id) {
                                return id;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot use form style for a path parameter");
    }

    @Test
    void pathParameterCannotUseAllowReserved() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-path-allow-reserved"))
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
                            @Http.Path("/{id}")
                            String get(@OpenApi.Parameter(allowReserved = true)
                                       @Http.PathParam("id") String id) {
                                return id;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot use allowReserved=true for a path parameter");
    }

    @Test
    void listQueryParameterCannotUseDelimitedStyleWithExplodeTrue() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-list-query-delimited-explode"))
                .addSource("InvalidOpenApiEndpoint.java", """
                        package com.example;

                        import java.util.List;

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
                            String get(@OpenApi.Parameter(style = OpenApi.Style.PIPE_DELIMITED,
                                                          explode = OpenApi.Explode.TRUE)
                                       @Http.QueryParam("value") List<String> values) {
                                return values.toString();
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot use explode=true with pipeDelimited style for a query parameter");
    }

    @Test
    void headerParameterCannotUseExplodeTrue() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-header-explode"))
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
                            String get(@OpenApi.Parameter(explode = OpenApi.Explode.TRUE)
                                       @Http.HeaderParam("X-Value") String value) {
                                return value;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot use explode=true for a header parameter");
    }

    @Test
    void headerParameterCannotUseAllowReserved() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-header-allow-reserved"))
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
                            String get(@OpenApi.Parameter(allowReserved = true)
                                       @Http.HeaderParam("X-Value") String value) {
                                return value;
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
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot use allowReserved=true for a header parameter");
    }

    @Test
    void queryParameterCanUseAllowReserved() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-allow-reserved"))
                .addSource("ValidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/valid")
                        class ValidOpenApiEndpoint {
                            @Http.GET
                            String get(@OpenApi.Parameter(allowReserved = true)
                                       @Http.QueryParam("value") String value) {
                                return value;
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
    }

    @Test
    void queryParameterCanUseMatchingName() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-matching-name"))
                .addSource("ValidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/valid")
                        class ValidOpenApiEndpoint {
                            @Http.GET
                            String get(@OpenApi.Parameter(name = "value")
                                       @Http.QueryParam("value") String value) {
                                return value;
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
    }

    @Test
    void generatedParameterUsesValidatedStaticNameAndLocation() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-static-name-location"))
                .addSource("ValidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/valid")
                        class ValidOpenApiEndpoint {
                            @Http.GET
                            @OpenApi.Parameter(name = "${openapi.param:value}",
                                               in = "${openapi.in:query}",
                                               examples = @OpenApi.Example(
                                                       name = "${openapi.example:sample}",
                                                       value = "one"))
                            String get(@Http.QueryParam("value") String value) {
                                return value;
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
        assertThat(generated, containsString(".parameter(parameter -> parameter.name(\"value\")"));
        assertThat(generated, containsString(".in(\"query\")"));
        assertThat(generated, containsString(".example(\"sample\", "));
    }

    @Test
    void requestParamsRecordComponentsAreGeneratedAsOpenApiParameters() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-request-params-components"))
                .addSource("ValidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/valid")
                        class ValidOpenApiEndpoint {
                            @Http.PUT
                            @Http.Path("/{id}")
                            @OpenApi.Parameter(name = "q", in = "query", value = "Search query")
                            @OpenApi.RequestBody("Body doc")
                            String put(@Http.RequestParams Params params) {
                                return params.id() + params.query() + params.trace() + params.body().value();
                            }
                        }

                        record Params(@Http.PathParam("id") String id,
                                      @Http.QueryParam("q") String query,
                                      @Http.HeaderParam("X-Trace") String trace,
                                      @Http.Entity MessageRequest body) {
                        }

                        record MessageRequest(String value) {
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
        assertThat(generated, containsString("document.path(\"/valid/{id}\""));
        assertThat(generated, containsString(".parameter(parameter -> parameter.name(\"id\")"));
        assertThat(generated, containsString(".in(\"path\")"));
        assertThat(generated, containsString(".parameter(parameter -> parameter.name(\"q\")"));
        assertThat(generated, containsString("Search query"));
        assertThat(generated, containsString(".parameter(parameter -> parameter.name(\"X-Trace\")"));
        assertThat(generated, containsString(".in(\"header\")"));
        assertThat(generated, containsString(".requestBody(requestBody -> requestBody"));
        assertThat(generated, containsString("Body doc"));
        assertThat(generated, containsString("NamedByType(MessageRequest.class)"));
        assertThat(generated, containsString(".content(\"application/json\", content -> content.schema(schemaRef("));
        assertThat(generated, not(containsString("NamedByType(Params.class)")));
    }

    @Test
    void cookieAndFormParametersAreGeneratedAsOpenApiInputs() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-cookie-form-params"))
                .addSource("ValidOpenApiEndpoint.java", """
                        package com.example;

                        import java.util.List;
                        import java.util.Optional;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/valid")
                        class ValidOpenApiEndpoint {
                            @Http.POST
                            @OpenApi.Parameter(name = "session", in = "cookie", value = "Session cookie")
                            @OpenApi.RequestBody(value = "Form doc",
                                                 content = @OpenApi.Content(
                                                         value = "application/x-www-form-urlencoded",
                                                         examples = @OpenApi.Example(name = "form-example",
                                                                                     value = "{\\"first\\":\\"one\\"}")))
                            String post(@Http.CookieParam("session") String session,
                                        @Http.FormParam("first") String first,
                                        @Http.RequestParams Params params) {
                                return session + first + params.second() + params.tags();
                            }
                        }

                        record Params(@Http.CookieParam("tracking") Optional<String> tracking,
                                      @Http.FormParam("second") String second,
                                      @Http.FormParam("tags") Optional<List<String>> tags) {
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
        assertThat(generated, containsString(".parameter(parameter -> parameter.name(\"session\")"));
        assertThat(generated, containsString("Session cookie"));
        assertThat(generated, containsString(".in(\"cookie\")"));
        assertThat(generated, containsString(".parameter(parameter -> parameter.name(\"tracking\")"));
        assertThat(generated, containsString(".content(\"application/x-www-form-urlencoded\", content -> content.schema("));
        assertThat(generated, containsString(".set(\"properties\", properties -> properties"));
        assertThat(generated, containsString(".set(\"first\", schema(\"string\"))"));
        assertThat(generated, containsString(".set(\"second\", schema(\"string\"))"));
        assertThat(generated, containsString(".set(\"tags\", arraySchema(schema(\"string\")))"));
        assertThat(generated, containsString(".setStrings(\"required\", java.util.List.of(\"first\", \"second\"))"));
        assertThat(generated, containsString(".example(\"form-example\", "));
        assertThat(generated, not(containsString("NamedByType(Params.class)")));
    }

    @Test
    void duplicateCookieParametersAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-duplicate-cookie"))
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
                            String get(@Http.CookieParam("session") String session,
                                       @Http.RequestParams Params params) {
                                return session + params.duplicate();
                            }
                        }

                        record Params(@Http.CookieParam("session") String duplicate) {
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
                               "Generated OpenAPI parameters on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define cookie parameter session more than once");
    }

    @Test
    void duplicateFormParametersAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-duplicate-form"))
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
                            @Http.POST
                            @Http.Consumes("application/x-www-form-urlencoded")
                            String post(@Http.FormParam("field") String first,
                                        @Http.RequestParams Params params) {
                                return first + params.second();
                            }
                        }

                        record Params(@Http.FormParam("field") String second) {
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
                               "Generated OpenAPI form request body on com.example.InvalidOpenApiEndpoint.post",
                               "cannot define form field field more than once");
    }

    @Test
    void formParametersRequireCompatibleConsumes() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-form-json-consumes"))
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
                            @Http.POST
                            @Http.Consumes("application/json")
                            String post(@Http.FormParam("field") String field) {
                                return field;
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
                               "Generated OpenAPI form request body on com.example.InvalidOpenApiEndpoint.post",
                               "requires @Http.Consumes(\"application/x-www-form-urlencoded\")");
    }

    @Test
    void queryParameterCanUseContent() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-query-content"))
                .addSource("ValidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/valid")
                        class ValidOpenApiEndpoint {
                            @Http.GET
                            String get(@OpenApi.Parameter(content = @OpenApi.Content)
                                       @Http.QueryParam("value") String value) {
                                return value;
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
    }

    @Test
    void listQueryParameterCanUseDelimitedStyle() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-list-query-delimited-style"))
                .addSource("ValidOpenApiEndpoint.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/valid")
                        class ValidOpenApiEndpoint {
                            @Http.GET
                            String get(@OpenApi.Parameter(style = OpenApi.Style.PIPE_DELIMITED,
                                                          explode = OpenApi.Explode.FALSE)
                                       @Http.QueryParam("value") List<String> values) {
                                return values.toString();
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
    }

    private static void assertCompilationFails(TestCompiler.Result result, String... diagnosticParts) {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        for (String diagnosticPart : diagnosticParts) {
            assertThat(diagnostics, containsString(diagnosticPart));
        }
    }

    private static String generatedSource(TestCompiler.Result result) throws IOException {
        StringBuilder generatedContent = new StringBuilder();
        var generatedSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith(".java"))
                .toList();
        for (Path generatedSource : generatedSources) {
            generatedContent.append(Files.readString(generatedSource));
            generatedContent.append('\n');
        }
        return generatedContent.toString();
    }
}
