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

package io.helidon.declarative.codegen.http.webserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.LazyValue;
import io.helidon.common.mapper.Mappers;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.types.Annotation;
import io.helidon.config.Config;
import io.helidon.http.Http;
import io.helidon.http.media.ReadableEntity;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
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

class FormParamCodegenTest {
    @Test
    void generatedFormParamsUseSingleParsedParameters() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-form-params"))
                .addSource("FormEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/form")
                        class FormEndpoint {
                            @Http.POST
                            String form(@Http.FormParam("first") String first,
                                        @Http.FormParam("second") String second,
                                        @Http.FormParam("count") int count) {
                                return first + second + count;
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

        var generatedSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith(".java"))
                .toList();

        StringBuilder generatedContent = new StringBuilder();
        for (Path generatedSource : generatedSources) {
            generatedContent.append(Files.readString(generatedSource, StandardCharsets.UTF_8));
            generatedContent.append('\n');
        }

        String generated = generatedContent.toString();
        String formRead = ".asOptional(Parameters.GENERIC_TYPE)";
        int occurrences = 0;
        int index = 0;
        while ((index = generated.indexOf(formRead, index)) != -1) {
            occurrences++;
            index += formRead.length();
        }

        assertThat(occurrences, is(1));
        assertThat(generated, containsString("var declarative__formParams = HttpSupport.lazyFormParams(() -> req.content()"));
        assertThat(generated, not(containsString("declarative__readFormParams")));
        assertThat(generated,
                   containsString("HttpSupport.paramValue(declarative__formParams.get(), \"first\", \"Form parameter\")"));
        assertThat(generated,
                   containsString("HttpSupport.paramValue(declarative__formParams.get(), \"second\", \"Form parameter\")"));
        assertThat(generated,
                   containsString("HttpSupport.paramValue(declarative__formParams.get(), \"count\", \"Form parameter\")"));
        assertThat(generated,
                   containsString("mappers.map(HttpSupport.paramValue(declarative__formParams.get(), \"count\","
                                          + " \"Form parameter\"), GenericType.STRING, GTYPE, me -> new"
                                          + " BadRequestException(\"Form parameter count has invalid value.\", me),"
                                          + " \"form-params\")"));
        assertThat(generated, not(containsString("\", \"form\")")));
    }

    @Test
    void generatedFormParamsEscapeParameterNames() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-form-params-escaped-name"))
                .addSource("FormEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/form")
                        class FormEndpoint {
                            @Http.POST
                            String form(@Http.FormParam("quoted\\\"and\\\\slash") String value) {
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

        var generatedSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith(".java"))
                .toList();

        StringBuilder generatedContent = new StringBuilder();
        for (Path generatedSource : generatedSources) {
            generatedContent.append(Files.readString(generatedSource, StandardCharsets.UTF_8));
            generatedContent.append('\n');
        }

        String generated = generatedContent.toString();
        assertThat(generated,
                   containsString("HttpSupport.paramValue(declarative__formParams.get(), \"quoted\\\"and\\\\slash\","
                                          + " \"Form parameter\")"));
    }

    @Test
    void generatedRequestParamsFormUsesSingleParsedParameters() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-request-params-form"))
                .addSource("FormParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record FormParams(@Http.FormParam("first") String first,
                                          @Http.FormParam("second") String second) {
                        }
                        """)
                .addSource("FormEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/form")
                        class FormEndpoint {
                            @Http.POST
                            String form(@Http.RequestParams FormParams params) {
                                return params.first() + params.second();
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

        var generatedSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith(".java"))
                .toList();

        StringBuilder generatedContent = new StringBuilder();
        for (Path generatedSource : generatedSources) {
            generatedContent.append(Files.readString(generatedSource, StandardCharsets.UTF_8));
            generatedContent.append('\n');
        }

        String generated = generatedContent.toString();
        String formRead = ".asOptional(Parameters.GENERIC_TYPE)";
        int occurrences = 0;
        int index = 0;
        while ((index = generated.indexOf(formRead, index)) != -1) {
            occurrences++;
            index += formRead.length();
        }

        assertThat(occurrences, is(1));
        assertThat(generated, containsString("var declarative__formParams = HttpSupport.lazyFormParams(() -> req.content()"));
        assertThat(generated, not(containsString("declarative__readFormParams")));
        assertThat(generated,
                   containsString("requestParams_0_0(ServerRequest req, ServerResponse res, "
                                          + "LazyValue<Parameters> declarative__formParams)"));
        assertThat(generated, containsString("requestParams_0_0(req, res, declarative__formParams);"));
    }

    @Test
    void generatedRequestParamsTypedParameterUsesServerRequest() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-request-params-typed"))
                .addSource("TypedParams.java", """
                        package com.example;

                        import io.helidon.webserver.http.ServerRequest;

                        record TypedParams(ServerRequest request) {
                        }
                        """)
                .addSource("TypedEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/typed")
                        class TypedEndpoint {
                            @Http.GET
                            String typed(@Http.RequestParams TypedParams params) {
                                return String.valueOf(params.request());
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

        var generatedSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith(".java"))
                .toList();

        StringBuilder generatedContent = new StringBuilder();
        for (Path generatedSource : generatedSources) {
            generatedContent.append(Files.readString(generatedSource, StandardCharsets.UTF_8));
            generatedContent.append('\n');
        }

        String generated = generatedContent.toString();
        assertThat(generated, containsString("var requestParam_request = req;"));
        assertThat(generated, containsString("return new TypedParams("));
    }

    @Test
    void generatedRequestParamsTypedParameterUsesServerResponse() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-request-params-typed-response"))
                .addSource("TypedParams.java", """
                        package com.example;

                        import io.helidon.webserver.http.ServerResponse;

                        record TypedParams(ServerResponse response) {
                        }
                        """)
                .addSource("TypedEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/typed")
                        class TypedEndpoint {
                            @Http.GET
                            String typed(@Http.RequestParams TypedParams params) {
                                return String.valueOf(params.response());
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

        var generatedSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith(".java"))
                .toList();

        StringBuilder generatedContent = new StringBuilder();
        for (Path generatedSource : generatedSources) {
            generatedContent.append(Files.readString(generatedSource, StandardCharsets.UTF_8));
            generatedContent.append('\n');
        }

        String generated = generatedContent.toString();
        assertThat(generated, containsString("var requestParam_response = res;"));
        assertThat(generated, containsString("return new TypedParams("));
    }

    @Test
    void methodEntityAndFormParametersAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-form-entity-method-conflict"))
                .addSource("InvalidEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidEndpoint {
                            @Http.POST
                            String invalid(@Http.Entity String entity,
                                           @Http.FormParam("field") String field) {
                                return "invalid";
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("@Http.Entity and @Http.FormParam cannot be combined on declarative server method "
                                          + "com.example.InvalidEndpoint.invalid()."));
    }

    @Test
    void methodMultipleEntityParametersAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-method-multiple-entity-params"))
                .addSource("InvalidEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidEndpoint {
                            @Http.POST
                            String invalid(@Http.Entity String first,
                                           @Http.Entity String second) {
                                return "invalid";
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("Only one @Http.Entity parameter is supported on declarative server method "
                                          + "com.example.InvalidEndpoint.invalid()."));
    }

    @Test
    void methodEntityAndRequestParamsFormComponentsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-entity-request-params-form-conflict"))
                .addSource("FormParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record FormParams(@Http.FormParam("field") String field) {
                        }
                        """)
                .addSource("InvalidEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidEndpoint {
                            @Http.POST
                            String invalid(@Http.Entity String entity,
                                           @Http.RequestParams FormParams params) {
                                return "invalid";
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("@Http.Entity and @Http.FormParam cannot be combined on declarative server method "
                                          + "com.example.InvalidEndpoint.invalid()."));
    }

    @Test
    void requestParamsEntityAndFormComponentsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-request-params-body-conflict"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record InvalidParams(@Http.Entity String entity,
                                             @Http.FormParam("field") String field) {
                        }
                        """)
                .addSource("InvalidEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidEndpoint {
                            @Http.POST
                            String invalid(@Http.RequestParams InvalidParams params) {
                                return "invalid";
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("@Http.Entity and @Http.FormParam record components cannot be combined in "
                                          + "@Http.RequestParams type com.example.InvalidParams."));
    }

    @Test
    void requestParamsMultipleEntityComponentsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-request-params-entity-conflict"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record InvalidParams(@Http.Entity String first,
                                             @Http.Entity String second) {
                        }
                        """)
                .addSource("InvalidEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidEndpoint {
                            @Http.POST
                            String invalid(@Http.RequestParams InvalidParams params) {
                                return "invalid";
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("Only one @Http.Entity record component is supported in @Http.RequestParams type "
                                          + "com.example.InvalidParams."));
    }

    @Test
    void requestParamsMultipleComponentAnnotationsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-request-params-multiple-annotations"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record InvalidParams(@Http.HeaderParam("X-CUSTOM")
                                             @Http.QueryParam("custom")
                                             String value) {
                        }
                        """)
                .addSource("InvalidEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidEndpoint {
                            @Http.GET
                            String invalid(@Http.RequestParams InvalidParams params) {
                                return "invalid";
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("Record component 'value' of @Http.RequestParams type com.example.InvalidParams "
                                          + "must have at most one supported request parameter annotation."));
    }

    @Test
    void methodMultipleParameterAnnotationsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-method-multiple-annotations"))
                .addSource("InvalidEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidEndpoint {
                            @Http.GET
                            String invalid(@Http.HeaderParam("X-CUSTOM")
                                           @Http.QueryParam("custom")
                                           String value) {
                                return "invalid";
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("Parameter 'value' of declarative server method com.example.InvalidEndpoint.invalid() "
                                          + "must have at most one supported request parameter annotation."));
    }

    @Test
    void requestParamsTypeMustBeRecord() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-request-params-non-record"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        class InvalidParams {
                        }
                        """)
                .addSource("InvalidEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidEndpoint {
                            @Http.POST
                            String invalid(@Http.RequestParams InvalidParams params) {
                                return "invalid";
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("@Http.RequestParams type must be a record. Type com.example.InvalidParams is CLASS."));
    }

    @Test
    void requestParamsComponentsMustBeAnnotated() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
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
                        Mappers.class,
                        Parameters.class,
                        Prototype.class,
                        ReadableEntity.class,
                        RestServer.class,
                        ServerRequest.class,
                        ServerResponse.class,
                        Service.class,
                        ServiceDescriptor.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-request-params-unannotated-component"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        record InvalidParams(String value) {
                        }
                        """)
                .addSource("InvalidEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/invalid")
                        class InvalidEndpoint {
                            @Http.POST
                            String invalid(@Http.RequestParams InvalidParams params) {
                                return "invalid";
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
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("Record component 'value' of @Http.RequestParams type com.example.InvalidParams "
                                          + "is not annotated with a supported request parameter annotation and is not "
                                          + "a supported typed parameter."));
    }
}
