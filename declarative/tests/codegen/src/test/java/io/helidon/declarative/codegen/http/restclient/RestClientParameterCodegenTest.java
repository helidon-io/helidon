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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.mapper.Mappers;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.types.Annotation;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.http.Http;
import io.helidon.http.media.ReadableEntity;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webclient.api.RestClient;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RestClientParameterCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
            BufferData.class,
            Config.class,
            io.helidon.common.config.Config.class,
            Dependency.class,
            Generated.class,
            GenericType.class,
            Http.class,
            Mappers.class,
            MediaType.class,
            Parameters.class,
            Prototype.class,
            ReadableEntity.class,
            RestClient.class,
            Service.class,
            ServiceDescriptor.class,
            UriQuery.class,
            WebClient.class
    );

    @Test
    void requiredStringQueryParameterCompiles() {
        assertCompiles("required-string-query", """
                @Http.GET
                String hello(@Http.QueryParam("name") String name);
                """);
    }

    @Test
    void requiredPrimitiveQueryParameterCompiles() {
        assertCompiles("required-primitive-query", """
                @Http.GET
                String hello(@Http.QueryParam("count") int count);
                """);
    }

    @Test
    void optionalStringHeaderParameterCompiles() {
        assertCompiles("optional-string-header", """
                @Http.GET
                String hello(@Http.HeaderParam("X-Value") Optional<String> value);
                """);
    }

    @Test
    void otherParameterShapesCompile() {
        assertCompiles("other-parameter-shapes", """
                @Http.GET
                String optionalQuery(@Http.QueryParam("name") Optional<String> name);

                @Http.GET
                String scalarHeader(@Http.HeaderParam("X-Value") String value);

                @Http.GET
                String listHeader(@Http.HeaderParam("X-Value") List<String> values);

                @Http.GET
                String arrayHeader(@Http.HeaderParam("X-Value") String[] values);

                @Http.GET
                String optionalListHeader(@Http.HeaderParam("X-Value") Optional<List<String>> values);
                """);
    }

    private static void assertCompiles(String scenario, String methods) {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/rest-client-parameters", scenario))
                .addSource("HelloApi.java", """
                        package com.example;

                        import java.util.List;
                        import java.util.Optional;

                        import io.helidon.http.Http;

                        @Http.Path("/greet")
                        interface HelloApi {
                        %s
                        }
                        """.formatted(methods))
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
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(diagnostics,
                   Files.exists(result.classOutput().resolve("com/example/HelloClient__DeclarativeClient.class")),
                   is(true));
    }
}
