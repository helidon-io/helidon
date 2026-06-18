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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class GraphQlServerCodegenTest {
    private static final List<Class<?>> GRAPHQL_CLASSPATH = List.of(
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
    );

    @Test
    void generatedGraphQlFeatureRegistersSchemaAndEntryPoints() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
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

                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune", BookStatus.AVAILABLE, "hidden");
                            }

                            @GraphQl.Query
                            AuthorDto author() {
                                return new AuthorDto("Frank Herbert", true);
                            }

                            @GraphQl.Mutation
                            Boolean update(@GraphQl.Argument("enabled") boolean enabled) {
                                return enabled;
                            }

                            @GraphQlServer.Field
                            @GraphQl.Description("Book summary")
                            String summary(@GraphQlServer.Source Book book,
                                           @GraphQl.Argument("prefix") String prefix) {
                                return prefix + ": " + book.title();
                            }

                            @GraphQlServer.Field("score")
                            int score(Book book) {
                                return 10;
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        @GraphQl.Description("Book result")
                        record Book(@GraphQl.NonNull String title,
                                    @GraphQl.Name("state") BookStatus status,
                                    @GraphQl.Ignore String internal) {
                        }
                        """)
                .addSource("BookStatus.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        enum BookStatus {
                            @GraphQl.Description("Currently available")
                            AVAILABLE,
                            @GraphQl.Name("OUT_OF_PRINT")
                            OUT
                        }
                        """)
                .addSource("AuthorDto.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        public class AuthorDto {
                            private final String name;
                            private final boolean active;

                            AuthorDto(String name, boolean active) {
                                this.name = name;
                                this.active = active;
                            }

                            public String getName() {
                                return name;
                            }

                            public boolean isActive() {
                                return active;
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
        assertThat(generated, containsString("book: Book"));
        assertThat(generated, containsString("author: AuthorDto"));
        assertThat(generated, containsString("type Mutation"));
        assertThat(generated, containsString("update(enabled: Boolean!): Boolean"));
        assertThat(generated, containsString("Book result"));
        assertThat(generated, containsString("type Book"));
        assertThat(generated, containsString("title: String!"));
        assertThat(generated, containsString("state: BookStatus"));
        assertThat(generated, containsString("Book summary"));
        assertThat(generated, containsString("summary(prefix: String): String"));
        assertThat(generated, containsString("score: Int!"));
        assertThat(generated, not(containsString("internal:")));
        assertThat(generated, containsString("enum BookStatus"));
        assertThat(generated, containsString("Currently available"));
        assertThat(generated, containsString("AVAILABLE"));
        assertThat(generated, containsString("OUT_OF_PRINT"));
        assertThat(generated, containsString("type AuthorDto"));
        assertThat(generated, containsString("name: String"));
        assertThat(generated, containsString("active: Boolean!"));
        assertThat(generated, containsString("builder.type(\"Book\""));
        assertThat(generated, containsString(".dataFetcher(\"title\", environment -> ((Book) environment.getSource()).title())"));
        assertThat(generated, containsString(".dataFetcher(\"state\", environment -> ((Book) environment.getSource()).status())"));
        assertThat(generated, containsString(".dataFetcher(\"summary\", fetcher_"));
        assertThat(generated, containsString(".dataFetcher(\"score\", fetcher_"));
        assertThat(generated, containsString("this.endpoint.summary("));
        assertThat(generated, containsString("((Book) environment.getSource())"));
        assertThat(generated, containsString("(String) environment.getArgument(\"prefix\")"));
        assertThat(generated, containsString("builder.type(\"AuthorDto\""));
        assertThat(generated, containsString(".dataFetcher(\"name\", environment -> ((AuthorDto) environment.getSource()).getName())"));
        assertThat(generated, containsString(".dataFetcher(\"active\", environment -> ((AuthorDto) environment.getSource()).isActive())"));
    }

    @Test
    void missingGraphQlEntityOnObjectReturnTypeFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-missing-entity"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune");
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        record Book(String title) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("must be annotated with @GraphQl.Entity"));
    }

    @Test
    void missingGraphQlEntityOnNestedObjectFieldFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-missing-nested-entity"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune", new Publisher("Ace"));
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record Book(String title, Publisher publisher) {
                        }
                        """)
                .addSource("Publisher.java", """
                        package com.example;

                        record Publisher(String name) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("must be annotated with @GraphQl.Entity"));
    }

    @Test
    void missingGraphQlEntityOnEnumReturnTypeFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-missing-enum-entity"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            BookStatus status() {
                                return BookStatus.AVAILABLE;
                            }
                        }
                        """)
                .addSource("BookStatus.java", """
                        package com.example;

                        enum BookStatus {
                            AVAILABLE
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("must be annotated with @GraphQl.Entity"));
    }

    @Test
    void childFieldWithoutSourceParameterFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-field-missing-source"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune");
                            }

                            @GraphQlServer.Field
                            String summary() {
                                return "Dune";
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record Book(String title) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("must declare exactly one source parameter"));
    }

    @Test
    void childFieldDuplicateObjectFieldFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-field-duplicate"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune");
                            }

                            @GraphQlServer.Field
                            String title(Book book) {
                                return book.title();
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record Book(String title) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Duplicate GraphQL field 'title'"));
    }

    @Test
    void childFieldConflictingNamesFailCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-field-name-conflict"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune");
                            }

                            @GraphQlServer.Field("summary")
                            @GraphQl.Name("description")
                            String summary(Book book) {
                                return book.title();
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record Book(String title) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("@GraphQlServer.Field value and @GraphQl.Name"));
    }

    @Test
    void mixedResolverAnnotationsFailCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-mixed-resolver-annotations"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune");
                            }

                            @GraphQl.Query
                            @GraphQlServer.Field
                            String invalid(Book book) {
                                return book.title();
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record Book(String title) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("can only use one of @GraphQl.Query, @GraphQl.Mutation"));
    }
}
