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

class RestClientSuppressionCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            TypeName.class,
            Config.class,
            Http.class,
            Service.class,
            RestClient.class,
            WebClient.class
    );

    @Test
    void generatedDeclarativeClientSuppressesApiWarnings() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-suppression"))
                .addSource("HelloApi.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        @Http.Path("/greet")
                        interface HelloApi {
                            @Http.GET
                            String hello();
                        }
                        """)
                .addSource("HelloClient.java", """
                        package com.example;

                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface HelloClient extends HelloApi {
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .printDiagnostics(true)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));

        var generatedClient = result.sourceOutput().resolve("com/example/HelloClient__DeclarativeClient.java");
        assertThat(String.join("\n", result.diagnostics()), Files.exists(generatedClient), is(true));

        var content = Files.readString(generatedClient, StandardCharsets.UTF_8);
        assertThat(content, containsString("@SuppressWarnings(\"helidon:api\")"));
    }
}
