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

package io.helidon.declarative.codegen.http.restclient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.builder.api.RuntimeType;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.http.Http;
import io.helidon.service.registry.Service;
import io.helidon.webclient.api.RestClient;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RestClientFormParamCodegenTest {
    @Test
    void generatedFormParamsEscapeParameterNames() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        Prototype.class,
                        RuntimeType.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-form-params-escaped-name"))
                .addSource("FormClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface FormClient {
                            @Http.POST
                            String form(@Http.FormParam("quoted\\\"and\\\\slash") String value);
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

        var generatedClient = result.sourceOutput().resolve("com/example/FormClient__DeclarativeClient.java");
        assertThat(diagnostics, Files.exists(generatedClient), is(true));

        String generated = Files.readString(generatedClient, StandardCharsets.UTF_8);
        assertThat(generated, containsString("declarative__formParams.add(\"quoted\\\"and\\\\slash\", "));
    }

    @Test
    void methodEntityAndFormParametersAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-form-entity-method-conflict"))
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.POST
                            String invalid(@Http.Entity String entity,
                                           @Http.FormParam("field") String field);
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
                   containsString("@Http.Entity and @Http.FormParam cannot be combined on declarative client method "
                                          + "com.example.InvalidClient.invalid()."));
    }

    @Test
    void methodMultipleEntityParametersAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-method-multiple-entity-params"))
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.POST
                            String invalid(@Http.Entity String first,
                                           @Http.Entity String second);
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
                   containsString("Only one @Http.Entity parameter is supported on declarative client method "
                                          + "com.example.InvalidClient.invalid()."));
    }

    @Test
    void methodEntityAndRequestParamsFormComponentsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-entity-request-params-form-conflict"))
                .addSource("FormParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record FormParams(@Http.FormParam("field") String field) {
                        }
                        """)
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.POST
                            String invalid(@Http.Entity String entity,
                                           @Http.RequestParams FormParams params);
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
                   containsString("@Http.Entity and @Http.FormParam cannot be combined on declarative client method "
                                          + "com.example.InvalidClient.invalid()."));
    }

    @Test
    void requestParamsEntityAndFormComponentsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-request-params-body-conflict"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record InvalidParams(@Http.Entity String entity,
                                             @Http.FormParam("field") String field) {
                        }
                        """)
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.POST
                            String invalid(@Http.RequestParams InvalidParams params);
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
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-request-params-entity-conflict"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record InvalidParams(@Http.Entity String first,
                                             @Http.Entity String second) {
                        }
                        """)
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.POST
                            String invalid(@Http.RequestParams InvalidParams params);
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
    void methodMultipleParameterAnnotationsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-method-multiple-annotations"))
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.GET
                            String invalid(@Http.HeaderParam("X-CUSTOM")
                                           @Http.QueryParam("custom")
                                           String value);
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
                   containsString("Parameter 'value' of declarative client method com.example.InvalidClient.invalid() "
                                          + "must have at most one supported request parameter annotation."));
    }

    @Test
    void requestParamsMultipleComponentAnnotationsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-request-params-multiple-annotations"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record InvalidParams(@Http.HeaderParam("X-CUSTOM")
                                             @Http.QueryParam("custom")
                                             String value) {
                        }
                        """)
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.GET
                            String invalid(@Http.RequestParams InvalidParams params);
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
    void requestParamsTypeMustBeRecord() {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-request-params-non-record"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        class InvalidParams {
                        }
                        """)
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.GET
                            String invalid(@Http.RequestParams InvalidParams params);
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
                .procOnly()
                .addClasspath(List.of(
                        Generated.class,
                        TypeName.class,
                        Config.class,
                        Http.class,
                        Service.class,
                        RestClient.class,
                        WebClient.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-request-params-unannotated-component"))
                .addSource("InvalidParams.java", """
                        package com.example;

                        record InvalidParams(String value) {
                        }
                        """)
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.GET
                            String invalid(@Http.RequestParams InvalidParams params);
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
                                          + "is not annotated with a supported request parameter annotation."));
    }
}
