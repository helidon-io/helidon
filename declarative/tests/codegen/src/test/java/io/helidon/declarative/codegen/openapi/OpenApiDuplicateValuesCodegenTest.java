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

class OpenApiDuplicateValuesCodegenTest {
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
    void documentCannotDeclareDuplicateServers() {
        var result = compile("openapi-duplicate-document-servers", """
                @OpenApi.Server("https://api.example.com")
                @OpenApi.Server("https://api.example.com")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Server on com.example.InvalidOpenApiEndpoint",
                               "cannot define server https://api.example.com more than once");
    }

    @Test
    void documentCannotDeclareDuplicateTags() {
        var result = compile("openapi-duplicate-document-tags", """
                @OpenApi.Tag("greeting")
                @OpenApi.Tag("greeting")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Tag on com.example.InvalidOpenApiEndpoint",
                               "cannot define tag greeting more than once");
    }

    @Test
    void documentCannotDeclareDuplicateExtensions() {
        var result = compile("openapi-duplicate-document-extensions", """
                @OpenApi.Extension(name = "x-test", value = "one")
                @OpenApi.Extension(name = "x-test", value = "two")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Extension on com.example.InvalidOpenApiEndpoint",
                               "cannot define extension x-test more than once");
    }

    @Test
    void operationCannotDeclareDuplicateServers() {
        var result = compile("openapi-duplicate-operation-servers", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Server("https://api.example.com")
                    @OpenApi.Server("https://api.example.com")
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Server on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define server https://api.example.com more than once");
    }

    @Test
    void operationCannotDeclareDuplicateTags() {
        var result = compile("openapi-duplicate-operation-tags", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Operation(tags = {"greeting", "greeting"})
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Operation on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define tag greeting more than once");
    }

    @Test
    void securitySchemeCannotUseDuplicateName() {
        var result = compile("openapi-duplicate-security-scheme", """
                @OpenApi.SecurityScheme(name = "bearerAuth", type = "http", scheme = "bearer")
                @OpenApi.SecurityScheme(name = "bearerAuth",
                                        type = "apiKey",
                                        in = "header",
                                        apiKeyName = "Authorization")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityScheme on com.example.InvalidOpenApiEndpoint",
                               "cannot define security scheme bearerAuth more than once");
    }

    @Test
    void oauthFlowCannotDeclareDuplicateScopes() {
        var result = compile("openapi-duplicate-oauth-scopes", """
                @OpenApi.SecurityScheme(
                        name = "oauth2",
                        type = "oauth2",
                        flows = @OpenApi.OAuthFlows(
                                clientCredentials = @OpenApi.OAuthFlow(
                                        tokenUrl = "https://api.example.com/token",
                                        scopes = {
                                                @OpenApi.OAuthScope(value = "read", description = "Read"),
                                                @OpenApi.OAuthScope(value = "read", description = "Read again")
                                        })))
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.OAuthFlow on com.example.InvalidOpenApiEndpoint",
                               "cannot define scope read more than once");
    }

    @Test
    void securityRequirementCannotDeclareDuplicateSchemes() {
        var result = compile("openapi-duplicate-security-requirement-schemes", """
                @OpenApi.SecurityRequirement({"bearerAuth", "bearerAuth"})
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityRequirement on com.example.InvalidOpenApiEndpoint",
                               "cannot define scheme bearerAuth more than once");
    }

    @Test
    void securityRequirementCannotRepeatSameRequirement() {
        var result = compile("openapi-duplicate-security-requirement", """
                @OpenApi.SecurityRequirement("bearerAuth")
                @OpenApi.SecurityRequirement("bearerAuth")
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.SecurityRequirement on com.example.InvalidOpenApiEndpoint",
                               "cannot define security requirement [bearerAuth] more than once");
    }

    @Test
    void methodParameterCannotRepeatSameLocationAndName() {
        var result = compile("openapi-duplicate-method-parameter", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Parameter(name = "value", in = "query", value = "First")
                    @OpenApi.Parameter(name = "value", in = "query", value = "Second")
                    String get(@Http.QueryParam("value") String value) {
                        return value;
                    }
                }
                """);

        assertCompilationFails(result,
                               "Method-level @OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define parameter query value more than once");
    }

    @Test
    void parameterAnnotationCannotRepeatOnSameParameter() {
        var result = compile("openapi-duplicate-parameter-annotation", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get(@OpenApi.Parameter("First")
                               @OpenApi.Parameter("Second")
                               @Http.QueryParam("value") String value) {
                        return value;
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define metadata for query parameter value more than once");
    }

    @Test
    void parameterExamplesCannotUseDuplicateNames() {
        var result = compile("openapi-duplicate-parameter-examples", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    String get(@OpenApi.Parameter(examples = {
                                       @OpenApi.Example(name = "sample", value = "one"),
                                       @OpenApi.Example(name = "sample", value = "two")
                               })
                               @Http.QueryParam("value") String value) {
                        return value;
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Parameter on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define example sample more than once");
    }

    @Test
    void requestBodyCannotDeclareDuplicateContentMediaTypes() {
        var result = compile("openapi-duplicate-request-body-content", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.POST
                    @OpenApi.RequestBody(content = {
                            @OpenApi.Content("application/json"),
                            @OpenApi.Content("application/json")
                    })
                    String post(@Http.Entity String value) {
                        return value;
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.RequestBody on com.example.InvalidOpenApiEndpoint.post",
                               "cannot define content media type application/json more than once");
    }

    @Test
    void responseCannotDeclareDuplicateContentMediaTypes() {
        var result = compile("openapi-duplicate-response-content", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      content = {
                                              @OpenApi.Content("application/json"),
                                              @OpenApi.Content("application/json")
                                      })
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define content media type application/json more than once");
    }

    @Test
    void contentExamplesCannotUseDuplicateNames() {
        var result = compile("openapi-duplicate-content-examples", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      content = @OpenApi.Content(
                                              examples = {
                                                      @OpenApi.Example(name = "sample", value = "one"),
                                                      @OpenApi.Example(name = "sample", value = "two")
                                              }))
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define example sample more than once");
    }

    @Test
    void responseCannotDeclareDuplicateHeaderNames() {
        var result = compile("openapi-duplicate-response-headers", """
                @OpenApi.Document
                @OpenApi.Info(title = "Test", version = "1.0")
                @RestServer.Endpoint
                @Service.Singleton
                @Http.Path("/invalid")
                class InvalidOpenApiEndpoint {
                    @Http.GET
                    @OpenApi.Response(status = 200,
                                      description = "OK",
                                      headers = {
                                              @OpenApi.Header(name = "X-Value", value = "First"),
                                              @OpenApi.Header(name = "x-value", value = "Second")
                                      })
                    String get() {
                        return "ok";
                    }
                }
                """);

        assertCompilationFails(result,
                               "@OpenApi.Response on com.example.InvalidOpenApiEndpoint.get",
                               "cannot define response header x-value more than once");
    }

    private static TestCompiler.Result compile(String workDir, String source) {
        return TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/" + workDir))
                .addSource("InvalidOpenApiEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.openapi.OpenApi;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        %s
                        """.formatted(source))
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

    private static void assertCompilationFails(TestCompiler.Result result, String... diagnosticParts) {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        for (String diagnosticPart : diagnosticParts) {
            assertThat(diagnostics, containsString(diagnosticPart));
        }
    }
}
