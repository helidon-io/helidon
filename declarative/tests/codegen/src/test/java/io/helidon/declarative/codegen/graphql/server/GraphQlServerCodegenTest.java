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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.context.Context;
import io.helidon.common.types.Annotation;
import io.helidon.config.Config;
import io.helidon.declarative.codegen.graphql.server.spi.GraphQlParameterCodegenProvider;
import io.helidon.graphql.GeneratedGraphQl;
import io.helidon.graphql.GraphQl;
import io.helidon.graphql.server.ExecutionContext;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.graphql.spi.GraphQlScalar;
import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authenticated;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.graphql.GraphQlEntryPoint;
import io.helidon.webserver.graphql.GraphQlServer;
import io.helidon.webserver.graphql.GraphQlService;
import io.helidon.webserver.http.HttpEntryPoint;
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
            io.helidon.common.buffers.BufferData.class,
            io.helidon.common.concurrency.limits.Limit.class,
            io.helidon.common.configurable.ThreadPool.class,
            io.helidon.common.mapper.Mapper.class,
            io.helidon.common.media.type.MediaType.class,
            io.helidon.common.parameters.Parameters.class,
            io.helidon.common.socket.SocketContext.class,
            io.helidon.common.tls.Tls.class,
            io.helidon.common.uri.UriQuery.class,
            Config.class,
            DataFetcher.class,
            DataFetchingEnvironment.class,
            DataLoader.class,
            Dependency.class,
            Generated.class,
            GraphQLSchema.class,
            GraphQl.class,
            GraphQlScalar.class,
            GeneratedGraphQl.CustomScalar.class,
            Context.class,
            ExecutionContext.class,
            GraphQlEntryPoint.class,
            GraphQlServer.class,
            GraphQlService.class,
            HttpEntryPoint.class,
            HttpFeature.class,
            HttpRouting.class,
            io.helidon.http.Status.class,
            io.helidon.http.encoding.ContentEncoding.class,
            io.helidon.http.http1.Http1ConnectionListener.class,
            io.helidon.http.media.MediaContext.class,
            io.helidon.common.security.SecurityContext.class,
            InvocationHandler.class,
            io.helidon.json.JsonObject.class,
            io.helidon.json.binding.JsonBinding.class,
            io.helidon.metrics.api.Metrics.class,
            Prototype.class,
            RuntimeWiring.class,
            SchemaGenerator.class,
            SchemaParser.class,
            SecurityContext.class,
            Authenticated.class,
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

                        import java.util.List;

                        import io.helidon.common.context.Context;
                        import io.helidon.graphql.GraphQl;
                        import io.helidon.graphql.server.ExecutionContext;
                        import io.helidon.security.SecurityContext;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        import graphql.schema.DataFetchingEnvironment;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Listener("@default")
                        @GraphQlServer.Context("/api/graphql")
                        @GraphQlServer.SchemaUri("/schema")
                        class GraphEndpoint {
                            @GraphQl.Query
                            @GraphQl.Description("Greets a user")
                            String hello(@GraphQl.Argument("name") @GraphQl.Description("User name") String name) {
                                return "Hello " + name;
                            }

                            @GraphQl.Query
                            String search(@GraphQl.Argument("criteria") @GraphQl.NonNull BookSearch criteria) {
                                return criteria.phrase() + ":" + criteria.minimumScore() + ":" + criteria.status();
                            }

                            @GraphQl.Query
                            String statusName(@GraphQl.Argument("status") BookStatus status) {
                                return status.name();
                            }

                            @GraphQl.Query
                            BookStatus renamedStatus() {
                                return BookStatus.OUT;
                            }

                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune", BookStatus.AVAILABLE, new Isbn("9780441172719"),
                                                List.of("classic", "desert"), List.of(new Isbn("9780441172720")), "hidden");
                            }

                            @GraphQl.Query
                            List<Book> recommendedBooks() {
                                return List.of(book());
                            }

                            @GraphQl.Query
                            String statusNames(@GraphQl.Argument("statuses") List<BookStatus> statuses) {
                                return statuses.toString();
                            }

                            @GraphQl.Query
                            String isbns(@GraphQl.Argument("values") List<Isbn> values) {
                                return values.toString();
                            }

                            @GraphQl.Query
                            AuthorDto author() {
                                return new AuthorDto("Frank Herbert", true);
                            }

                            @GraphQl.Mutation
                            Boolean update(@GraphQl.Argument("enabled") boolean enabled) {
                                return enabled;
                            }

                            @GraphQl.Query
                            Isbn bookIsbn(@GraphQl.Argument("value") Isbn value) {
                                return value;
                            }

                            @GraphQl.Query
                            String contextSummary(Context context,
                                                  ExecutionContext executionContext,
                                                  DataFetchingEnvironment environment) {
                                return context.id() + executionContext.hasPartialResultsException()
                                        + environment.getField().getName();
                            }

                            @GraphQl.Query
                            boolean secure(SecurityContext securityContext) {
                                return securityContext.isAuthenticated();
                            }

                            @GraphQlServer.Field
                            @GraphQl.Description("Book summary")
                            String summary(@GraphQlServer.Source Book book,
                                           @GraphQl.Argument("prefix") String prefix,
                                           @GraphQl.Argument("tags") List<String> tags) {
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

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        @GraphQl.Description("Book\\rresult")
                        record Book(@GraphQl.NonNull String title,
                                    @GraphQl.Name("state") BookStatus status,
                                    Isbn isbn,
                                    List<String> tags,
                                    List<Isbn> relatedIsbns,
                                    @GraphQl.Ignore String internal) {
                        }
                        """)
                .addSource("BookSearch.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        @GraphQl.Description("Book search input")
                        record BookSearch(@GraphQl.NonNull String phrase,
                                          int minimumScore,
                                          @GraphQl.NonNull BookStatus status,
                                          List<String> tags,
                                          List<BookStatus> statuses,
                                          List<Isbn> isbns,
                                          List<BookFilter> filters) {
                        }
                        """)
                .addSource("BookFilter.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record BookFilter(@GraphQl.NonNull String field,
                                          @GraphQl.NonNull String value) {
                        }
                        """)
                .addSource("Isbn.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Scalar("ISBN")
                        @GraphQl.Description("ISBN scalar")
                        record Isbn(String value) {
                        }
                        """)
                .addSource("IsbnScalar.java", """
                        package com.example;

                                                import io.helidon.graphql.spi.GraphQlScalar;import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class IsbnScalar implements GraphQlScalar<Isbn> {
                            @Override
                            public Object serialize(Isbn value) {
                                return value.value();
                            }

                            @Override
                            public Isbn parseValue(Object value) {
                                return new Isbn((String) value);
                            }
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
        var scalarSources = Files.walk(result.sourceOutput())
                .filter(it -> it.getFileName().toString().equals("com_2e_example_2e_IsbnScalar__GraphQlScalar.java"))
                .toList();
        assertThat(scalarSources.size(), is(1));

        String generated = Files.readString(generatedSources.getFirst(), StandardCharsets.UTF_8);
        String generatedScalar = Files.readString(scalarSources.getFirst(), StandardCharsets.UTF_8);
        assertThat(generated, containsString("class GraphEndpoint__GraphQlFeature implements HttpFeature"));
        assertThat(generated,
                   containsString("routing.register(GraphQlService.builder()\n"
                                          + "                .config(config.get(\"graphql\"))\n"
                                          + "                .webContext(\"/api/graphql\")"));
        assertThat(generated, containsString(".schemaUri(\"/schema\")"));
        assertThat(generated, not(containsString(".permitAll(true)")));
        assertThat(generated, containsString("Config config"));
        assertThat(generated, containsString("this.config = config;"));
        assertThat(generated, containsString("HttpEntryPoint.EntryPoints httpEntryPoints"));
        assertThat(generated, containsString("this.httpEntryPoints = httpEntryPoints;"));
        assertThat(generated,
                   containsString(".declarativeHttpEntryPoints(httpEntryPoints, descriptor, descriptor.qualifiers(), annotations)"));
        assertThat(generated, containsString("entryPoints.dataFetcher("));
        assertThat(generated, containsString("type Query"));
        assertThat(generated, containsString("\\\"Greets a user\\\"\\n  hello(\\n"
                                                     + "    \\\"User name\\\"\\n"
                                                     + "    name: String\\n"
                                                     + "  ): String"));
        assertThat(generated, containsString("search(criteria: BookSearchInput!): String"));
        assertThat(generated, containsString("statusName(status: BookStatus): String"));
        assertThat(generated, containsString("renamedStatus: BookStatus"));
        assertThat(generated, containsString("statusNames(statuses: [BookStatus]): String"));
        assertThat(generated, containsString("isbns(values: [ISBN]): String"));
        assertThat(generated, containsString("book: Book"));
        assertThat(generated, containsString("recommendedBooks: [Book]"));
        assertThat(generated, containsString("author: AuthorDto"));
        assertThat(generated, containsString("bookIsbn(value: ISBN): ISBN"));
        assertThat(generated, containsString("contextSummary: String"));
        assertThat(generated, containsString("secure: Boolean!"));
        assertThat(generated, containsString("type Mutation"));
        assertThat(generated, containsString("update(enabled: Boolean!): Boolean"));
        assertThat(generated, containsString("ISBN scalar"));
        assertThat(generated, containsString("scalar ISBN"));
        assertThat(generated, containsString("Book\\\\rresult"));
        assertThat(generated, containsString("type Book"));
        assertThat(generated, containsString("title: String!"));
        assertThat(generated, containsString("state: BookStatus"));
        assertThat(generated, containsString("isbn: ISBN"));
        assertThat(generated, containsString("tags: [String]"));
        assertThat(generated, containsString("relatedIsbns: [ISBN]"));
        assertThat(generated, containsString("Book summary"));
        assertThat(generated, containsString("summary(prefix: String, tags: [String]): String"));
        assertThat(generated, containsString("score: Int!"));
        assertThat(generated, not(containsString("internal:")));
        assertThat(generated, containsString("enum BookStatus"));
        assertThat(generated, containsString("Currently available"));
        assertThat(generated, containsString("AVAILABLE"));
        assertThat(generated, containsString("OUT_OF_PRINT"));
        assertThat(generated, containsString("builder.type(\"BookStatus\""));
        assertThat(generated, containsString(".enumValues(enumName -> switch (enumName)"));
        assertThat(generated, containsString("case \"OUT_OF_PRINT\" -> BookStatus.OUT;"));
        assertThat(generated, containsString("type AuthorDto"));
        assertThat(generated, containsString("name: String"));
        assertThat(generated, containsString("active: Boolean!"));
        assertThat(generated, containsString("Book search input"));
        assertThat(generated, containsString("input BookSearchInput"));
        assertThat(generated, containsString("phrase: String!"));
        assertThat(generated, containsString("minimumScore: Int!"));
        assertThat(generated, containsString("status: BookStatus!"));
        assertThat(generated, containsString("tags: [String]"));
        assertThat(generated, containsString("statuses: [BookStatus]"));
        assertThat(generated, containsString("isbns: [ISBN]"));
        assertThat(generated, containsString("filters: [BookFilterInput]"));
        assertThat(generated, containsString("input BookFilterInput"));
        assertThat(generated, containsString("field: String!"));
        assertThat(generated, containsString("value: String!"));
        assertThat(generated, containsString("builder.type(\"Book\""));
        assertThat(generated, containsString(".dataFetcher(\"title\", environment -> ((Book) environment.getSource()).title())"));
        assertThat(generated, containsString(".dataFetcher(\"state\", environment -> ((Book) environment.getSource()).status())"));
        assertThat(generated, containsString(".dataFetcher(\"isbn\", environment -> ((Book) environment.getSource()).isbn())"));
        assertThat(generated, containsString("List<GeneratedGraphQl.CustomScalar> scalars"));
        assertThat(generated, containsString("GraphQlScalar<Isbn> scalar_0"));
        assertThat(generated,
                   containsString("this.scalars = List.of("
                                          + "com_2e_example_2e_IsbnScalar__GraphQlScalar.create(scalar_0));"));
        assertThat(generated, containsString("builder.scalar(graphQlScalar(\"ISBN\", Isbn.class));"));
        assertThat(generated, containsString("new Coercing<Object, Object>()"));
        assertThat(generated, containsString("scalar.serialize(dataFetcherResult)"));
        assertThat(generated, containsString("scalar.parseValue(input)"));
        assertThat(generated, containsString("Object literalValue = input == null ? null : "
                                                     + "scalarLiteralValue(input, variables);"));
        assertThat(generated, containsString("scalar.parseLiteral(literalValue)"));
        assertThat(generated, containsString("scalarLiteralValue("));
        assertThat(generated, containsString("case graphql.language.ArrayValue arrayValue"));
        assertThat(generated, containsString("case graphql.language.ObjectValue objectValue"));
        assertThat(generatedScalar,
                   containsString("class com_2e_example_2e_IsbnScalar__GraphQlScalar "
                                          + "{"));
        assertThat(generatedScalar,
                   containsString("static GeneratedGraphQl.CustomScalar create(GraphQlScalar<Isbn> delegate)"));
        assertThat(generatedScalar, not(containsString("@Service.Singleton")));
        assertThat(generatedScalar, not(containsString("@Service.Inject")));
        assertThat(generatedScalar, containsString("return \"ISBN\";"));
        assertThat(generatedScalar, containsString("return Isbn.class;"));
        assertThat(generatedScalar, containsString("return \"ISBN scalar\";"));
        assertThat(generatedScalar, containsString("return java.util.Objects.requireNonNull(delegate.serialize((Isbn) value), "
                                                           + "\"serialize result\");"));
        assertThat(generatedScalar, containsString("return java.util.Objects.requireNonNull(delegate.parseValue(value), "
                                                           + "\"parseValue result\");"));
        assertThat(generatedScalar, containsString("delegate.parseValue(java.util.Objects.requireNonNull(value))"));
        assertThat(generatedScalar, containsString("\"parseLiteral result\""));
        assertThat(generated, containsString("private static Object inputValue(Object value, Class<?> type, String graphQlType)"));
        assertThat(generated, containsString("if (type.isInstance(value))"));
        assertThat(generated, containsString("Expected GraphQL \" + graphQlType"));
        assertThat(generated, containsString("(Isbn) inputValue(environment.getArgument(\"value\"), Isbn.class, \"ISBN\")"));
        assertThat(generated, containsString("enum_com_2e_example_2e_BookStatus(environment.getArgument(\"status\"))"));
        assertThat(generated, containsString("list_java_2e_util_2e_List_3c_com_2e_example_2e_BookStatus_3e_("
                                                     + "environment.getArgument(\"statuses\"))"));
        assertThat(generated, containsString("list_java_2e_util_2e_List_3c_com_2e_example_2e_Isbn_3e_("
                                                     + "environment.getArgument(\"values\"))"));
        assertThat(generated, containsString("private static List<BookStatus> "
                                                     + "list_java_2e_util_2e_List_3c_com_2e_example_2e_BookStatus_3e_("
                                                     + "Object value)"));
        assertThat(generated, containsString("private static List<Isbn> "
                                                     + "list_java_2e_util_2e_List_3c_com_2e_example_2e_Isbn_3e_("
                                                     + "Object value)"));
        assertThat(generated, containsString("Expected GraphQL list value for [BookStatus]."));
        assertThat(generated, containsString("Expected GraphQL list value for [ISBN]."));
        assertThat(generated, containsString("result.add(enum_com_2e_example_2e_BookStatus(it));"));
        assertThat(generated, containsString("result.add((Isbn) inputValue(it, Isbn.class, \"ISBN\"));"));
        assertThat(generated, containsString("input_com_2e_example_2e_BookSearch(environment.getArgument(\"criteria\"))"));
        assertThat(generated, containsString("private static BookStatus enum_com_2e_example_2e_BookStatus(Object value)"));
        assertThat(generated, containsString("Expected GraphQL enum value for BookStatus to be a string"));
        assertThat(generated, containsString("return switch (enumName)"));
        assertThat(generated, containsString("case \"OUT_OF_PRINT\" -> BookStatus.OUT;"));
        assertThat(generated, containsString("private static BookSearch input_com_2e_example_2e_BookSearch(Object value)"));
        assertThat(generated, containsString("private static BookFilter input_com_2e_example_2e_BookFilter(Object value)"));
        assertThat(generated, containsString("if (!(value instanceof java.util.Map<?, ?> input))"));
        assertThat(generated, containsString("Expected GraphQL input object value for BookSearchInput."));
        assertThat(generated, containsString("Expected GraphQL input object value for BookFilterInput."));
        assertThat(generated, containsString("return new BookSearch("));
        assertThat(generated, containsString("(String) inputValue(input.get(\"phrase\"), String.class, \"String\")"));
        assertThat(generated, containsString("(Integer) inputValue(input.get(\"minimumScore\"), Integer.class, \"Int\")"));
        assertThat(generated, containsString("enum_com_2e_example_2e_BookStatus(input.get(\"status\"))"));
        assertThat(generated, containsString("list_java_2e_util_2e_List_3c_java_2e_lang_2e_String_3e_(input.get(\"tags\"))"));
        assertThat(generated, containsString("list_java_2e_util_2e_List_3c_com_2e_example_2e_BookStatus_3e_("
                                                     + "input.get(\"statuses\"))"));
        assertThat(generated, containsString("list_java_2e_util_2e_List_3c_com_2e_example_2e_Isbn_3e_("
                                                     + "input.get(\"isbns\"))"));
        assertThat(generated, containsString("list_java_2e_util_2e_List_3c_com_2e_example_2e_BookFilter_3e_("
                                                     + "input.get(\"filters\"))"));
        assertThat(generated, containsString("result.add(input_com_2e_example_2e_BookFilter(it));"));
        assertThat(generated, containsString("helidonContext(environment)"));
        assertThat(generated, containsString("graphQlExecutionContext(environment)"));
        assertThat(generated, containsString("securityContext(environment)"));
        assertThat(generated, containsString("ExecutionContext.HELIDON_CONTEXT_KEY"));
        assertThat(generated, containsString("ExecutionContext.EXECUTION_CONTEXT_KEY"));
        assertThat(generated, containsString(".get(SecurityContext.class)"));
        assertThat(generated, containsString(".dataFetcher(\"summary\", fetcher_"));
        assertThat(generated, containsString(".dataFetcher(\"score\", fetcher_"));
        assertThat(generated, containsString("this.endpoint_0.summary("));
        assertThat(generated, containsString("((Book) environment.getSource())"));
        assertThat(generated, containsString("(String) inputValue(environment.getArgument(\"prefix\"), String.class, \"String\")"));
        assertThat(generated, containsString("list_java_2e_util_2e_List_3c_java_2e_lang_2e_String_3e_("
                                                     + "environment.getArgument(\"tags\"))"));
        assertThat(generated, containsString("builder.type(\"AuthorDto\""));
        assertThat(generated, containsString(".dataFetcher(\"name\", environment -> ((AuthorDto) environment.getSource()).getName())"));
        assertThat(generated, containsString(".dataFetcher(\"active\", environment -> ((AuthorDto) environment.getSource()).isActive())"));
    }

    @Test
    void dependencyModuleCustomScalarsGenerateConsumerOwnedAdapters() throws IOException {
        Path workDir = Path.of("target/test-compiler/graphql-server-cross-module-custom-scalars");
        var dependency = TestCompiler.builder()
                .currentRelease()
                .addModulepath(GRAPHQL_CLASSPATH)
                .workDir(workDir.resolve("dependency"))
                .addSource("module-info.java", """
                        module com.example.scalars {
                            requires io.helidon.graphql;

                            exports com.example.scalars.first;
                            exports com.example.scalars.second;
                        }
                        """)
                .addSource("com/example/scalars/first/Value.java", """
                        package com.example.scalars.first;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Scalar("FIRST_VALUE")
                        public record Value(String value) {
                        }
                        """)
                .addSource("com/example/scalars/second/Value.java", """
                        package com.example.scalars.second;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Scalar("SECOND_VALUE")
                        public record Value(String value) {
                        }
                        """)
                .build()
                .compile();

        String dependencyDiagnostics = String.join("\n", dependency.diagnostics());
        assertThat(dependencyDiagnostics, dependency.success(), is(true));

        var consumer = TestCompiler.builder()
                .currentRelease()
                .addModulepath(GRAPHQL_CLASSPATH)
                .addModulepathEntry(dependency.classOutput())
                .addProcessor(AptProcessor::new)
                .workDir(workDir.resolve("consumer"))
                .addSource("module-info.java", """
                        module com.example.endpoint {
                            requires com.example.scalars;
                            requires io.helidon.common;
                            requires io.helidon.common.context;
                            requires io.helidon.common.types;
                            requires io.helidon.config;
                            requires io.helidon.graphql;
                            requires io.helidon.graphql.server;
                            requires io.helidon.service.registry;
                            requires io.helidon.webserver;
                            requires io.helidon.webserver.graphql;
                        }
                        """)
                .addSource("GraphEndpoint.java", """
                        package com.example.endpoint;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            com.example.scalars.first.Value firstValue() {
                                return new com.example.scalars.first.Value("first");
                            }

                            @GraphQl.Query
                            com.example.scalars.second.Value secondValue() {
                                return new com.example.scalars.second.Value("second");
                            }
                        }
                        """)
                .addSource("ScalarProviders.java", """
                        package com.example.endpoint;

                        import io.helidon.graphql.spi.GraphQlScalar;
                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class FirstScalar implements GraphQlScalar<com.example.scalars.first.Value> {
                            @Override
                            public Object serialize(com.example.scalars.first.Value value) {
                                return value.value();
                            }

                            @Override
                            public com.example.scalars.first.Value parseValue(Object value) {
                                return new com.example.scalars.first.Value((String) value);
                            }
                        }

                        @Service.Singleton
                        class SecondScalar implements GraphQlScalar<com.example.scalars.second.Value> {
                            @Override
                            public Object serialize(com.example.scalars.second.Value value) {
                                return value.value();
                            }

                            @Override
                            public com.example.scalars.second.Value parseValue(Object value) {
                                return new com.example.scalars.second.Value((String) value);
                            }
                        }
                        """)
                .build()
                .compile();

        String consumerDiagnostics = String.join("\n", consumer.diagnostics());
        assertThat(consumerDiagnostics, consumer.success(), is(true));

        var scalarAdapters = Files.walk(consumer.sourceOutput())
                .filter(it -> it.getFileName().toString().endsWith("Scalar__GraphQlScalar.java"))
                .toList();
        assertThat(scalarAdapters.size(), is(2));
        assertThat(scalarAdapters.stream()
                           .allMatch(it -> it.getParent().endsWith(Path.of("com", "example", "endpoint"))),
                   is(true));
        assertThat(scalarAdapters.stream()
                           .map(it -> it.getFileName().toString())
                           .distinct()
                           .count(),
                   is(2L));
    }

    @Test
    void twoConsumersUseFeatureLocalScalarAdapters() throws IOException {
        Path workDir = Path.of("target/test-compiler/graphql-server-two-consumer-custom-scalar");
        var dependency = TestCompiler.builder()
                .currentRelease()
                .addModulepath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(workDir.resolve("dependency"))
                .addSource("module-info.java", """
                        module com.example.scalars {
                            requires io.helidon.graphql;
                            requires io.helidon.service.registry;

                            exports com.example.scalars;
                        }
                        """)
                .addSource("com/example/scalars/Money.java", """
                        package com.example.scalars;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Scalar("MONEY")
                        public record Money(String value) {
                        }
                        """)
                .addSource("com/example/scalars/MoneyScalar.java", """
                        package com.example.scalars;

                        import io.helidon.graphql.spi.GraphQlScalar;
                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        public class MoneyScalar implements GraphQlScalar<Money> {
                            @Override
                            public Object serialize(Money value) {
                                return value.value();
                            }

                            @Override
                            public Money parseValue(Object value) {
                                return new Money((String) value);
                            }
                        }
                        """)
                .build()
                .compile();

        String dependencyDiagnostics = String.join("\n", dependency.diagnostics());
        assertThat(dependencyDiagnostics, dependency.success(), is(true));

        for (String consumerName : List.of("a", "b")) {
            var consumer = TestCompiler.builder()
                    .currentRelease()
                    .addModulepath(GRAPHQL_CLASSPATH)
                    .addModulepathEntry(dependency.classOutput())
                    .addProcessor(AptProcessor::new)
                    .workDir(workDir.resolve(consumerName))
                    .addSource("module-info.java", """
                            module com.example.%1$s {
                                requires com.example.scalars;
                                requires io.helidon.common;
                                requires io.helidon.common.context;
                                requires io.helidon.common.types;
                                requires io.helidon.config;
                                requires io.helidon.graphql;
                                requires io.helidon.graphql.server;
                                requires io.helidon.service.registry;
                                requires io.helidon.webserver;
                                requires io.helidon.webserver.graphql;
                            }
                            """.formatted(consumerName))
                    .addSource("GraphEndpoint.java", """
                            package com.example.%1$s;

                            import com.example.scalars.Money;
                            import io.helidon.graphql.GraphQl;
                            import io.helidon.webserver.graphql.GraphQlServer;

                            @GraphQlServer.Endpoint
                            class GraphEndpoint {
                                @GraphQl.Query
                                Money money() {
                                    return new Money("%1$s");
                                }
                            }
                            """.formatted(consumerName))
                    .build()
                    .compile();

            String consumerDiagnostics = String.join("\n", consumer.diagnostics());
            assertThat(consumerDiagnostics, consumer.success(), is(true));

            Path generatedFeature = consumer.sourceOutput()
                    .resolve(Path.of("com", "example", consumerName, "GraphEndpoint__GraphQlFeature.java"));
            assertThat("Generated source should exist: " + generatedFeature,
                       Files.exists(generatedFeature),
                       is(true));
            String featureSource = Files.readString(generatedFeature, StandardCharsets.UTF_8);
            assertThat(featureSource, containsString("GraphQlScalar<Money> scalar_0"));
            assertThat(featureSource,
                       not(containsString("List<GeneratedGraphQl.CustomScalar> scalars)")));
            assertThat(featureSource,
                       containsString("this.scalars = List.of("
                                              + "com_2e_example_2e_scalars_2e_MoneyScalar__GraphQlScalar.create(scalar_0));"));

            Path generatedAdapter = consumer.sourceOutput()
                    .resolve(Path.of("com",
                                          "example",
                                          consumerName,
                                          "com_2e_example_2e_scalars_2e_MoneyScalar__GraphQlScalar.java"));
            assertThat("Generated source should exist: " + generatedAdapter,
                       Files.exists(generatedAdapter),
                       is(true));
            String adapterSource = Files.readString(generatedAdapter, StandardCharsets.UTF_8);
            assertThat(adapterSource, not(containsString("@Service.Singleton")));
            assertThat(adapterSource, not(containsString("@Service.Inject")));

            Path registryMetadata = consumer.classOutput()
                    .resolve(Path.of("META-INF",
                                          "helidon",
                                          "unnamed",
                                          "com.example." + consumerName,
                                          "service-registry.json"));
            assertThat("Generated registry metadata should exist: " + registryMetadata,
                       Files.exists(registryMetadata),
                       is(true));
            assertThat(Files.readString(registryMetadata, StandardCharsets.UTF_8),
                       not(containsString("io.helidon.graphql.GeneratedGraphQl.CustomScalar")));
        }
    }

    @Test
    void typeUseNonNullListElementsContributeToSdl() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-list-element-non-null"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            List<@GraphQl.NonNull Book> books() {
                                return List.of(new Book("Dune", List.of("classic")));
                            }

                            @GraphQl.Query
                            String byTags(@GraphQl.Argument("tags") List<@GraphQl.NonNull String> tags) {
                                return tags.toString();
                            }

                            @GraphQl.Query
                            String search(BookSearch criteria) {
                                return criteria.tags().toString();
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record Book(String title, List<@GraphQl.NonNull String> tags) {
                        }
                        """)
                .addSource("BookSearch.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record BookSearch(List<@GraphQl.NonNull String> tags) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = Files.readString(result.sourceOutput()
                                                    .resolve("com/example/GraphEndpoint__GraphQlFeature.java"),
                                            StandardCharsets.UTF_8);
        assertThat(generated, containsString("books: [Book!]"));
        assertThat(generated, containsString("byTags(tags: [String!]): String"));
        assertThat(generated, containsString("tags: [String!]"));
    }

    @Test
    void customParameterProviderBindsResolverParameters() throws IOException {
        Path servicesFile = registerTestGraphQlParameterProvider();
        try {
            var result = TestCompiler.builder()
                    .currentRelease()
                    .addClasspath(GRAPHQL_CLASSPATH)
                    .addProcessor(AptProcessor::new)
                    .workDir(Path.of("target/test-compiler/graphql-server-custom-parameter-provider"))
                    .addSource("GraphEndpoint.java", """
                            package com.example;

                            import io.helidon.graphql.GraphQl;
                            import io.helidon.webserver.graphql.GraphQlServer;

                            @GraphQlServer.Endpoint
                            class GraphEndpoint {
                                @GraphQl.Query
                                String fieldName(RequestInfo requestInfo) {
                                    return requestInfo.value();
                                }

                                @GraphQl.Query
                                Book book() {
                                    return new Book("Dune");
                                }

                                @GraphQlServer.Field
                                String summary(Book book, RequestInfo requestInfo, @GraphQl.Argument("prefix") String prefix) {
                                    return prefix + ": " + book.title() + ": " + requestInfo.value();
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
                    .addSource("RequestInfo.java", """
                            package com.example;

                            record RequestInfo(String value) {
                            }
                            """)
                    .build()
                    .compile();

            String diagnostics = String.join("\n", result.diagnostics());
            assertThat(diagnostics, result.success(), is(true));

            String generated = Files.readString(result.sourceOutput()
                                                        .resolve("com/example/GraphEndpoint__GraphQlFeature.java"),
                                                StandardCharsets.UTF_8);
            assertThat(generated, containsString("fieldName: String"));
            assertThat(generated, not(containsString("fieldName(requestInfo:")));
            assertThat(generated, containsString("summary(prefix: String): String"));
            assertThat(generated, not(containsString("summary(requestInfo:")));
            assertThat(generated, containsString("new com.example.RequestInfo(environment.getField().getName() + \":QUERY\")"));
            assertThat(generated, containsString("new com.example.RequestInfo(environment.getField().getName() + \":FIELD\")"));
            assertThat(generated, containsString("((Book) environment.getSource())"));
            assertThat(generated, containsString("(String) inputValue(environment.getArgument(\"prefix\"), String.class, \"String\")"));
        } finally {
            Files.deleteIfExists(servicesFile);
        }
    }

    @Test
    void endpointsWithSameListenerAndContextShareGeneratedFeature() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-grouped-endpoints"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Listener("@default")
                        @GraphQlServer.Context("/graphql")
                        @GraphQlServer.SchemaUri("/schema.graphql")
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune");
                            }
                        }
                        """)
                .addSource("LibraryEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class LibraryEndpoint {
                            @GraphQl.Query
                            String library() {
                                return "central";
                            }

                            @GraphQl.Mutation
                            boolean reset() {
                                return true;
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
                .filter(it -> it.getFileName().toString().endsWith("__GraphQlFeature.java"))
                .toList();
        assertThat(generatedSources.size(), is(1));
        assertThat(generatedSources.getFirst().getFileName().toString(),
                   is("GraphEndpoint__GraphQlFeature.java"));

        String generated = Files.readString(generatedSources.getFirst(), StandardCharsets.UTF_8);
        assertThat(generated, containsString("class GraphEndpoint__GraphQlFeature implements HttpFeature"));
        assertThat(generated, containsString("GraphEndpoint__ServiceDescriptor.INSTANCE"));
        assertThat(generated, containsString("requestAnnotations(GraphEndpoint__ServiceDescriptor.ANNOTATIONS)"));
        assertThat(generated, containsString("GraphEndpoint endpoint_0"));
        assertThat(generated, containsString("LibraryEndpoint endpoint_1"));
        assertThat(generated, containsString("this.endpoint_0.book("));
        assertThat(generated, containsString("this.endpoint_1.library("));
        assertThat(generated, containsString("this.endpoint_1.reset("));
        assertThat(generated, containsString("book: Book"));
        assertThat(generated, containsString("library: String"));
        assertThat(generated, containsString("type Mutation"));
        assertThat(generated, containsString("reset: Boolean!"));
        assertThat(generated.split("routing.register", -1).length - 1, is(1));
    }

    @Test
    void endpointsWithEquivalentRoutePathsShareGeneratedFeature() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-normalized-routes-v2"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Context("graphql/")
                        @GraphQlServer.SchemaUri("schema.graphql")
                        class GraphEndpoint {
                            @GraphQl.Query
                            String hello() {
                                return "hello";
                            }
                        }
                        """)
                .addSource("LibraryEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class LibraryEndpoint {
                            @GraphQl.Query
                            String library() {
                                return "library";
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
                .filter(it -> it.getFileName().toString().endsWith("__GraphQlFeature.java"))
                .toList();
        assertThat(generatedSources.size(), is(1));

        String generated = Files.readString(generatedSources.getFirst(), StandardCharsets.UTF_8);
        assertThat(generated, containsString(".webContext(\"/graphql\")"));
        assertThat(generated, containsString(".schemaUri(\"/schema.graphql\")"));
        assertThat(generated.split("routing.register", -1).length - 1, is(1));
    }

    @Test
    void endpointsWithDifferentContextsGenerateSeparateFeatures() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-separate-contexts"))
                .addSource("AdminEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Context("/admin/graphql")
                        class AdminEndpoint {
                            @GraphQl.Query
                            String admin() {
                                return "admin";
                            }
                        }
                        """)
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String user() {
                                return "user";
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
                .filter(it -> it.getFileName().toString().endsWith("__GraphQlFeature.java"))
                .toList();
        assertThat(generatedSources.size(), is(2));
        String generated = Files.readString(generatedSources.getFirst(), StandardCharsets.UTF_8)
                + Files.readString(generatedSources.get(1), StandardCharsets.UTF_8);
        assertThat(generated, containsString(".webContext(\"/admin/graphql\")"));
        assertThat(generated, containsString(".webContext(\"/graphql\")"));
        assertThat(generated.split("routing.register", -1).length - 1, is(2));
    }

    @Test
    void routeGroupsProduceDistinctFeatureNames() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-primary-endpoint-feature-names"))
                .addSource("FirstEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Context("/api/v1")
                        class FirstEndpoint {
                            @GraphQl.Query
                            String first() {
                                return "first";
                            }
                        }
                        """)
                .addSource("SecondEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Context("/api/v1")
                        class SecondEndpoint {
                            @GraphQl.Query
                            String second() {
                                return "second";
                            }
                        }
                        """)
                .addSource("ThirdEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Context("/api_2f_v1")
                        class ThirdEndpoint {
                            @GraphQl.Query
                            String third() {
                                return "third";
                            }
                        }
                        """)
                .addSource("FourthEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Context("/api_2f_v1")
                        class FourthEndpoint {
                            @GraphQl.Query
                            String fourth() {
                                return "fourth";
                            }
                        }
                        """)
                .addSource("GraphQl__40_default__2f_api_2f_v1.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.Context("/single")
                        class GraphQl__40_default__2f_api_2f_v1 {
                            @GraphQl.Query
                            String single() {
                                return "single";
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
                .filter(it -> it.getFileName().toString().endsWith("__GraphQlFeature.java"))
                .map(it -> it.getFileName().toString())
                .sorted()
                .toList();
        assertThat(generatedSources,
                   is(List.of("FirstEndpoint__GraphQlFeature.java",
                              "FourthEndpoint__GraphQlFeature.java",
                              "GraphQl__40_default__2f_api_2f_v1__GraphQlFeature.java")));
    }

    @Test
    void groupedEndpointsWithConflictingSchemaUriFailCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-schema-uri-conflict"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.SchemaUri("/schema")
                        class GraphEndpoint {
                            @GraphQl.Query
                            String hello() {
                                return "hello";
                            }
                        }
                        """)
                .addSource("LibraryEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        @GraphQlServer.SchemaUri("/other")
                        class LibraryEndpoint {
                            @GraphQl.Query
                            String library() {
                                return "library";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Conflicting GraphQL schema URI"));
    }

    @Test
    void outputAndInputTypeNameCollisionFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-input-output-name-conflict"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            BookInput book() {
                                return new BookInput("Dune");
                            }

                            @GraphQl.Query
                            String search(BookInput criteria) {
                                return criteria.title();
                            }
                        }
                        """)
                .addSource("BookInput.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record BookInput(String title) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Duplicate GraphQL type name 'BookInput'"));
        assertThat(diagnostics, containsString("GraphQL output and input types share one schema namespace"));
    }

    @Test
    void invalidGraphQlNameFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-invalid-name"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            @GraphQl.Name("bad-name")
                            String invalid() {
                                return "invalid";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("GraphQL name 'bad-name' must match [_A-Za-z][_0-9A-Za-z]*"));
    }

    @Test
    void reservedGraphQlNameFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-reserved-name"))
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

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        @GraphQl.Name("__Book")
                        record Book(String title) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("GraphQL name '__Book' must not start with '__'"));
    }

    @Test
    void builtinScalarObjectNameFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-builtin-object-name"))
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

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        @GraphQl.Name("String")
                        record Book(String title) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("cannot use reserved type name 'String'"));
    }

    @Test
    void builtinScalarCustomScalarNameFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-builtin-scalar-name"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Isbn isbn() {
                                return new Isbn("9780441172719");
                            }
                        }
                        """)
                .addSource("Isbn.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Scalar("ID")
                        record Isbn(String value) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("cannot use reserved type name 'ID'"));
    }

    @Test
    void groupedEndpointsWithDifferentRequestMetadataAnnotationsFailCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-request-metadata-conflict"))
                .addSource("RequestPolicy.java", """
                        package com.example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        @Retention(RetentionPolicy.RUNTIME)
                        @Target({ElementType.METHOD, ElementType.TYPE})
                        public @interface RequestPolicy {
                        }
                        """)
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @RequestPolicy
                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String hello() {
                                return "hello";
                            }
                        }
                        """)
                .addSource("LibraryEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class LibraryEndpoint {
                            @GraphQl.Query
                            String library() {
                                return "library";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("different endpoint-level request metadata annotations"));
    }

    @Test
    void duplicateQueryAcrossGroupedEndpointsFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-group-duplicate-query"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String hello() {
                                return "hello";
                            }
                        }
                        """)
                .addSource("LibraryEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class LibraryEndpoint {
                            @GraphQl.Query
                            String hello() {
                                return "library";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Duplicate GraphQL query field 'hello'"));
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
    void missingGraphQlEntityOnInputObjectArgumentFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-missing-input-entity"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String search(BookSearch criteria) {
                                return criteria.phrase();
                            }
                        }
                        """)
                .addSource("BookSearch.java", """
                        package com.example;

                        record BookSearch(String phrase) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("must be annotated with @GraphQl.Entity"));
    }

    @Test
    void ignoredInputObjectRecordComponentFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-ignored-input-component"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String search(BookSearch criteria) {
                                return criteria.phrase();
                            }
                        }
                        """)
                .addSource("BookSearch.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record BookSearch(@GraphQl.Ignore String phrase) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("@GraphQl.Ignore cannot be used on GraphQL input record component"));
    }

    @Test
    void defaultValueInputObjectRecordComponentFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-default-input-component"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String search(BookSearch criteria) {
                                return criteria.phrase();
                            }
                        }
                        """)
                .addSource("BookSearch.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record BookSearch(@GraphQl.DefaultValue("\\\"Dune\\\"") String phrase) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("@GraphQl.DefaultValue cannot be used on GraphQL input record component"));
    }

    @Test
    void parameterizedScalarUseFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-parameterized-scalar"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String hello(@GraphQl.Argument("box") Box<String> box) {
                                return box.value();
                            }
                        }
                        """)
                .addSource("Box.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Scalar
                        record Box<T>(T value) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("GraphQL scalar type com.example.Box<java.lang.String> "
                                                      + "must not be parameterized."));
    }

    @Test
    void unsupportedAnnotationOnAutomaticObjectFieldFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-annotated-automatic-field"))
                .addSource("FieldPolicy.java", """
                        package com.example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        @Retention(RetentionPolicy.RUNTIME)
                        @Target({ElementType.METHOD, ElementType.FIELD, ElementType.RECORD_COMPONENT})
                        public @interface FieldPolicy {
                        }
                        """)
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book();
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        class Book {
                            @FieldPolicy
                            public String title = "Dune";
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Automatic GraphQL fields only support GraphQL schema annotations"));
    }

    @Test
    void inheritedUnsupportedAnnotationOnAutomaticObjectGetterFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-annotated-inherited-automatic-field"))
                .addSource("FieldPolicy.java", """
                        package com.example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        @Retention(RetentionPolicy.RUNTIME)
                        @Target({ElementType.METHOD, ElementType.FIELD, ElementType.RECORD_COMPONENT})
                        public @interface FieldPolicy {
                        }
                        """)
                .addSource("Titled.java", """
                        package com.example;

                        interface Titled {
                            @FieldPolicy
                            String getTitle();
                        }
                        """)
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book();
                            }
                        }
                        """)
                .addSource("Book.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        class Book implements Titled {
                            @Override
                            public String getTitle() {
                                return "Dune";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Automatic GraphQL fields only support GraphQL schema annotations"));
    }

    @Test
    void rawListArgumentFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-raw-list-argument"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String invalid(List values) {
                                return values.toString();
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("must declare exactly one type argument"));
    }

    @Test
    void wildcardListArgumentFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-wildcard-list-argument"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String invalid(List<?> values) {
                                return values.toString();
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("must use a concrete element type"));
    }

    @Test
    void rawListInputFieldFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-raw-list-input-field"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String search(BookSearch search) {
                                return search.values().toString();
                            }
                        }
                        """)
                .addSource("BookSearch.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record BookSearch(List values) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("must declare exactly one type argument"));
    }

    @Test
    void wildcardListInputFieldFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-wildcard-list-input-field"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String search(BookSearch search) {
                                return search.values().toString();
                            }
                        }
                        """)
                .addSource("BookSearch.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        record BookSearch(List<?> values) {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("must use a concrete element type"));
    }

    @Test
    void childFieldEnumAndListArgumentsDoNotInterfereWithSourceInference() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-field-source-inference-with-arguments"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune");
                            }

                            @GraphQlServer.Field
                            String stateLabel(@GraphQlServer.Source Book book, BookStatus status) {
                                return book.title() + ": " + status;
                            }

                            @GraphQlServer.Field
                            String tagSummary(Book book, List<String> tags) {
                                return book.title() + ": " + tags;
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
                .addSource("BookStatus.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        enum BookStatus {
                            AVAILABLE
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String generated = Files.readString(result.sourceOutput()
                                                    .resolve("com/example/GraphEndpoint__GraphQlFeature.java"),
                                            StandardCharsets.UTF_8);
        assertThat(generated, containsString("stateLabel(status: BookStatus): String"));
        assertThat(generated, containsString("tagSummary(tags: [String]): String"));
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
    void childFieldRawListArgumentFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-field-raw-list-argument"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune");
                            }

                            @GraphQlServer.Field
                            String summary(@GraphQlServer.Source Book book, List values) {
                                return values.toString();
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
        assertThat(diagnostics, containsString("must declare exactly one type argument"));
    }

    @Test
    void childFieldWildcardListArgumentFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-field-wildcard-list-argument"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            Book book() {
                                return new Book("Dune");
                            }

                            @GraphQlServer.Field
                            String summary(@GraphQlServer.Source Book book, List<?> values) {
                                return values.toString();
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
        assertThat(diagnostics, containsString("must use a concrete element type"));
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
    void duplicateArgumentNameFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-argument-duplicate"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String book(@GraphQl.Argument("id") String first,
                                        @GraphQl.Argument("id") String second) {
                                return first + second;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Duplicate GraphQL argument 'id'"));
    }

    @Test
    void specialArgumentParameterFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-special-argument"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import graphql.schema.DataFetchingEnvironment;
                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String invalid(@GraphQl.Argument("env") DataFetchingEnvironment env) {
                                return env.getField().getName();
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("@GraphQl.Argument cannot be used on special GraphQL resolver parameter"));
    }

    @Test
    void argumentConflictingNamesFailCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-argument-name-conflict"))
                .addSource("GraphEndpoint.java", """
                        package com.example;

                        import io.helidon.graphql.GraphQl;
                        import io.helidon.webserver.graphql.GraphQlServer;

                        @GraphQlServer.Endpoint
                        class GraphEndpoint {
                            @GraphQl.Query
                            String book(@GraphQl.Argument("id") @GraphQl.Name("bookId") String id) {
                                return id;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("@GraphQl.Argument value and @GraphQl.Name"));
    }

    @Test
    void duplicateEnumValueNameFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-enum-value-duplicate"))
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

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        enum BookStatus {
                            @GraphQl.Name("AVAILABLE")
                            AVAILABLE,
                            @GraphQl.Name("AVAILABLE")
                            READY
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Duplicate GraphQL enum value 'AVAILABLE'"));
    }

    @Test
    void reservedEnumValueLiteralFailsCodegen() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(GRAPHQL_CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/graphql-server-enum-value-reserved-literal"))
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

                        import io.helidon.graphql.GraphQl;

                        @GraphQl.Entity
                        enum BookStatus {
                            @GraphQl.Name("true")
                            AVAILABLE
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("GraphQL enum value 'true' must not be true, false, or null."));
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

    private static Path registerTestGraphQlParameterProvider() throws IOException {
        Path testClasses = testClasses();
        Path servicesFile = testClasses.resolve("META-INF/services/")
                .resolve(GraphQlParameterCodegenProvider.class.getName());
        Files.createDirectories(servicesFile.getParent());
        Files.writeString(servicesFile,
                          TestGraphQlParameterCodegenProvider.class.getName() + "\n",
                          StandardCharsets.UTF_8);
        return servicesFile;
    }

    private static Path testClasses() throws IOException {
        try {
            return Path.of(TestGraphQlParameterCodegenProvider.class.getProtectionDomain()
                                   .getCodeSource()
                                   .getLocation()
                                   .toURI());
        } catch (URISyntaxException e) {
            throw new IOException("Failed to resolve test classpath", e);
        }
    }
}
