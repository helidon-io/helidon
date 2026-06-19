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

package io.helidon.graphql.server;

import java.util.List;
import java.util.Map;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class InvocationHandlerObjectResultTest {
    @SuppressWarnings("unchecked")
    @Test
    void objectAndEnumResultsAreExecuted() {
        InvocationHandler handler = InvocationHandler.create(schema());

        Map<String, Object> result = handler.execute("{book { title status }}");

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        Map<String, Object> book = (Map<String, Object>) data.get("book");
        assertThat(book.get("title"), is("Dune"));
        assertThat(book.get("status"), is("AVAILABLE"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void nestedResolverErrorUsesFullPath() {
        InvocationHandler handler = InvocationHandler.create(schema());

        Map<String, Object> result = handler.execute("{book { title summary }}");

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        Map<String, Object> book = (Map<String, Object>) data.get("book");
        assertThat(book.get("title"), is("Dune"));
        assertThat(book.get("summary"), is((Object) null));

        List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
        assertThat(errors.getFirst().get("path"), is(List.of("book", "summary")));
    }

    private static GraphQLSchema schema() {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser()
                .parse("""
                               type Query {
                                 book: Book
                               }

                               type Book {
                                 title: String!
                                 summary: String
                                 status: BookStatus
                               }

                               enum BookStatus {
                                 AVAILABLE
                                 OUT_OF_PRINT
                               }
                               """);
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("book", _ -> new Book("Dune", BookStatus.AVAILABLE)))
                .type("Book", builder -> builder
                        .dataFetcher("title", environment -> ((Book) environment.getSource()).title())
                        .dataFetcher("summary", _ -> {
                            throw new IllegalStateException("Summary unavailable.");
                        })
                        .dataFetcher("status", environment -> ((Book) environment.getSource()).status()))
                .build();
        return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }

    private record Book(String title, BookStatus status) {
    }

    private enum BookStatus {
        AVAILABLE,
        OUT_OF_PRINT
    }
}
