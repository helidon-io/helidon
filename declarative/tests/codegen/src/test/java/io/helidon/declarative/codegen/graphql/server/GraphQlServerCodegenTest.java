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

package io.helidon.declarative.codegen.graphql.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.Annotation;
import io.helidon.config.Config;
import io.helidon.graphql.GraphQl;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.graphql.GraphQlEntryPoint;
import io.helidon.webserver.graphql.GraphQlServer;
import io.helidon.webserver.graphql.GraphQlService;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.DataLoader;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class GraphQlServerCodegenTest {
    @Test
    void generatedGraphQlFeatureRegistersSchemaAndEntryPoints() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(
                        Annotation.class,
                        Config.class,
                        DataFetcher.class,
                        DataFetchingEnvironment.class,
                        DataLoader.class,
                        Dependency.class,
                        Generated.class,
                        GraphQLSchema.class,
                        GraphQl.class,
                        GraphQlEntryPoint.class,
                        GraphQlServer.class,
                        GraphQlService.class,
                        HttpFeature.class,
                        HttpRouting.class,
                        InvocationHandler.class,
                        Prototype.class,
                        RuntimeWiring.class,
                        SchemaGenerator.class,
                        SchemaParser.class,
                        Service.class,
                        ServiceDescriptor.class,
                        TypeDefinitionRegistry.class
                ))
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Listener("@default")
                        @GraphQlServer.Context("/api/graphql")
                        @GraphQlServer.SchemaUri("/schema")
                        class GraphEndpoint {
                            @GraphQl.Query
                            String hello(@GraphQl.Argument("name") String name) {
                                return "Hello " + name;
                            }

                            @GraphQl.Mutation
                            Boolean update(@GraphQl.Argument("enabled") boolean enabled) {
                                return enabled;
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
                .filter(it -> it.getFileName().toString().equals("GraphEndpoint__GraphQlFeature.java"))
                .toList();
        assertThat(generatedSources.size(), is(1));

        String generated = Files.readString(generatedSources.getFirst(), StandardCharsets.UTF_8);
        assertThat(generated, containsString("class GraphEndpoint__GraphQlFeature implements HttpFeature"));
        assertThat(generated, containsString("routing.register(GraphQlService.builder()"));
        assertThat(generated, containsString(".webContext(\"/api/graphql\")"));
        assertThat(generated, containsString(".schemaUri(\"/schema\")"));
        assertThat(generated, containsString("entryPoints.dataFetcher("));
        assertThat(generated, containsString("type Query"));
        assertThat(generated, containsString("hello(name: String): String"));
        assertThat(generated, containsString("type Mutation"));
        assertThat(generated, containsString("update(enabled: Boolean!): Boolean"));
    }
}
