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

class RestClientParameterNullCodegenTest {
    @Test
    void generatedClientRejectsNullParameterValues() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
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
                .workDir(Path.of("target/test-compiler/rest-client-null-params"))
                .addSource("ParamClient.java", """
                        package com.example;

                        import java.util.List;
                        import java.util.Optional;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface ParamClient {
                            @Http.GET
                            @Http.Path("/items/{id}")
                            String get(@Http.HeaderParam("X-CUSTOM") String header,
                                       @Http.QueryParam("q") Optional<List<String>> query,
                                       @Http.PathParam("id") String id);
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

        var generatedClient = result.sourceOutput().resolve("com/example/ParamClient__DeclarativeClient.java");
        assertThat(diagnostics, Files.exists(generatedClient), is(true));

        String generated = Files.readString(generatedClient, StandardCharsets.UTF_8);
        assertThat(generated, containsString("import java.util.Objects;"));
        assertThat(generated, containsString("declarative__parameterValue(\"Path parameter\", \"id\", id)"));
        assertThat(generated, containsString("declarative__parameterValue(\"Header\", \"X-CUSTOM\", header)"));
        assertThat(generated,
                   containsString("Objects.requireNonNull(query, \"Query parameter q must not be null.\")"));
        assertThat(generated,
                   containsString("declarative__parameterValue(\"Query parameter\", \"q\", declarative__value)"));
    }

    @Test
    void generatedClientEscapesQueryParameterNames() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
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
                .workDir(Path.of("target/test-compiler/rest-client-query-param-literal"))
                .addSource("QueryClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface QueryClient {
                            @Http.GET
                            String query(@Http.QueryParam("quoted\\\"and\\\\slash") String value);
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

        var generatedClient = result.sourceOutput().resolve("com/example/QueryClient__DeclarativeClient.java");
        assertThat(diagnostics, Files.exists(generatedClient), is(true));

        String generated = Files.readString(generatedClient, StandardCharsets.UTF_8);
        assertThat(generated,
                   containsString("declarative__builder.queryParam(\"quoted\\\"and\\\\slash\", "
                                          + "declarative__parameterValue(\"Query parameter\", "
                                          + "\"quoted\\\"and\\\\slash\", value));"));
    }
}
