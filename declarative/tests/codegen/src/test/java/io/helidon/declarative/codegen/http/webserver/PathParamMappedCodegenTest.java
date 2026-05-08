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
import io.helidon.common.Default;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.mapper.Mappers;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.types.Annotation;
import io.helidon.common.uri.UriQuery;
import io.helidon.config.Config;
import io.helidon.http.Http;
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

class PathParamMappedCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
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
            Mappers.class,
            Parameters.class,
            Prototype.class,
            RestServer.class,
            ServerRequest.class,
            ServerResponse.class,
            Service.class,
            ServiceDescriptor.class,
            UriQuery.class
    );

    @Test
    void generatedStringPathParamDoesNotUseFeatureMappers() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-path-string"))
                .addSource("StringPathEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/string-path/{name}")
                        class StringPathEndpoint {
                            @Http.GET
                            String name(@Http.PathParam("name") String name) {
                                return name;
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

        String generated = generatedContent(result);
        assertThat(generated, containsString(".path().pathParameters().first(\"name\")"));
        assertThat(generated, not(containsString("helidonDeclarative__")));
        assertThat(generated, not(containsString("Value.create(mappers, \"name\"")));
    }

    @Test
    void generatedMappedPathParamUsesFeatureMappers() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/http-path-mapped"))
                .addSource("MappedPathEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.http.RestServer;

                        @RestServer.Listener("@default")
                        @RestServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/mapped-path/{id}")
                        class MappedPathEndpoint {
                            @Http.GET
                            String id(@Http.PathParam("id") Integer id) {
                                return Integer.toString(id);
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

        String generated = generatedContent(result);
        assertThat(generated,
                   containsString("mappers.map(it, GenericType.STRING, GTYPE, me -> new BadRequestException(\"Path"
                                          + " parameter id has invalid value.\", me), \"http\", \"path\")"));
        assertThat(generated, not(containsString("\"http/path\"")));
        assertThat(generated, not(containsString("Value.create(")));
        assertThat(generated, not(containsString("helidonDeclarative__")));
    }

    private String generatedContent(TestCompiler.Result result) throws IOException {
        var generatedSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith(".java"))
                .toList();

        StringBuilder generatedContent = new StringBuilder();
        for (Path generatedSource : generatedSources) {
            generatedContent.append(Files.readString(generatedSource, StandardCharsets.UTF_8));
            generatedContent.append('\n');
        }

        return generatedContent.toString();
    }
}
