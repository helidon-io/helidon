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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class RestClientCookieParamCodegenTest {
    @Test
    void generatedCookieParametersUseHttpSupport() throws IOException {
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
                .workDir(Path.of("target/test-compiler/rest-client-cookie-params"))
                .addSource("CookieClient.java", """
                        package com.example;

                        import java.util.List;
                        import java.util.Optional;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface CookieClient {
                            @Http.GET
                            @Http.Path("/cookies")
                            String cookies(@Http.CookieParam("first") String first,
                                           @Http.CookieParam("tag") List<String> tags,
                                           @Http.CookieParam("optional") Optional<List<String>> optionalTags);
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

        var generatedClient = result.sourceOutput().resolve("com/example/CookieClient__DeclarativeClient.java");
        assertThat(diagnostics, Files.exists(generatedClient), is(true));

        String generated = Files.readString(generatedClient, StandardCharsets.UTF_8);
        assertThat(generated, containsString("import java.util.Objects;"));
        assertThat(generated,
                   containsString("return String.valueOf(Objects.requireNonNull(value, source + \" \" + name"
                                          + " + \" must not be null.\"));"));
        assertThat(generated,
                   containsString("declarative__cookies.add(HttpSupport.cookie(\"first\", "
                                          + "declarative__parameterValue(\"Cookie parameter\", \"first\", first)));"));
        assertThat(generated,
                   containsString("if (Objects.requireNonNull(tags, \"Cookie parameter tag must not be null.\").isEmpty())"));
        assertThat(generated, containsString("throw new IllegalArgumentException(\"Cookie parameter tag has no values.\");"));
        assertThat(generated,
                   containsString("Objects.requireNonNull(tags, \"Cookie parameter tag must not be null.\")"));
        assertThat(generated,
                   containsString(".map(declarative__it -> HttpSupport.cookie(\"tag\", "
                                          + "declarative__parameterValue(\"Cookie parameter\", \"tag\","
                                          + " declarative__it)))"));
        assertThat(generated,
                   containsString(".map(declarative__value -> HttpSupport.cookie(\"optional\", "
                                          + "declarative__parameterValue(\"Cookie parameter\", \"optional\","
                                          + " declarative__value)))"));
        assertThat(generated,
                   containsString("Objects.requireNonNull(optionalTags, \"Cookie parameter optional must not be null.\")"));
        assertThat(generated, not(containsString("if (optionalTags.isEmpty())")));
        assertThat(generated,
                   containsString("declarative__builder.header(HeaderNames.COOKIE, "
                                          + "HttpSupport.cookieHeader(declarative__cookies));"));
        assertThat(generated, not(containsString("declarative__cookieHeader")));
    }

    @Test
    void invalidCookieParameterNameIsRejected() {
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
                .workDir(Path.of("target/test-compiler/rest-client-invalid-cookie-param-name"))
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.GET
                            String invalid(@Http.CookieParam("bad;name") String value);
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
        assertThat(diagnostics, containsString("@Http.CookieParam value must be a valid cookie name."));
    }

    @Test
    void invalidRequestParamsCookieParameterNameIsRejected() {
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
                .workDir(Path.of("target/test-compiler/rest-client-request-params-invalid-cookie-param-name"))
                .addSource("CookieParams.java", """
                        package com.example;

                        import io.helidon.http.Http;

                        record CookieParams(@Http.CookieParam("bad;name") String value) {
                        }
                        """)
                .addSource("InvalidClient.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.webclient.api.RestClient;

                        @RestClient.Endpoint("http://localhost:8080")
                        interface InvalidClient {
                            @Http.GET
                            String invalid(@Http.RequestParams CookieParams params);
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
        assertThat(diagnostics, containsString("@Http.CookieParam value must be a valid cookie name."));
    }
}
