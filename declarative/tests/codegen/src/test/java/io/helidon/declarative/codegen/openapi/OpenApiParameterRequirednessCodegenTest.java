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
import static org.hamcrest.MatcherAssert.assertThat;

class OpenApiParameterRequirednessCodegenTest {
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
    void nonOptionalQueryParameterCannotBeDocumentedAsOptional() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-required-query"))
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
                            String get(@OpenApi.Parameter(required = OpenApi.Required.FALSE)
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
        assertThat("Build should fail", result.success(), is(false));
        assertThat(diagnostics, containsString("@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get"));
        assertThat(diagnostics, containsString("cannot make a required query parameter optional"));
    }

    @Test
    void nonOptionalHeaderParameterCannotBeDocumentedAsOptional() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-required-header"))
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
                            String get(@OpenApi.Parameter(required = OpenApi.Required.FALSE)
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

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        assertThat(diagnostics, containsString("@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get"));
        assertThat(diagnostics, containsString("cannot make a required header parameter optional"));
    }

    @Test
    void defaultedQueryParameterCanBeDocumentedAsOptional() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/openapi-default-query"))
                .addSource("DefaultOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.common.Default;
                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @OpenApi.Document
                        @OpenApi.Info(title = "Test", version = "1.0")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/default")
                        class DefaultOpenApiEndpoint {
                            @Http.GET
                            String get(@OpenApi.Parameter(required = OpenApi.Required.FALSE)
                                       @Http.QueryParam("limit") @Default.Value("13") Integer limit) {
                                return Integer.toString(limit);
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
}
