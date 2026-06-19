/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
package io.helidon.docs.se;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.graphql.GraphQl;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Binding;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.graphql.GraphQlService;
import io.helidon.webserver.graphql.GraphQlServer;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

@SuppressWarnings("ALL")
class GraphQlSnippets {

    // tag::snippet_2[]
    static GraphQLSchema buildSchema() {
        String schema = // <1>
                """ 
                type Query {
                    hello: String\s
                    helloInDifferentLanguages: [String]\s
                }
                """;

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        DataFetcher<List<String>> dataFetcher = env -> List.of( // <2>
                "Bonjour",
                "Hola",
                "Zdravstvuyte",
                "Nǐn hǎo",
                "Salve",
                "Gudday",
                "Konnichiwa",
                "Guten Tag");

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring() // <3>
                .type("Query", builder -> builder
                        .dataFetcher("hello", new StaticDataFetcher("world")))
                .type("Query", builder -> builder
                        .dataFetcher("helloInDifferentLanguages", dataFetcher))
                .build();

        SchemaGenerator generator = new SchemaGenerator();
        return generator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);  // <4>
    }
    // end::snippet_2[]

    void snippet_1() {
        // tag::snippet_1[]
        Config graphQlConfig = Services.get(Config.class).get("graphql");

        InvocationHandler invocationHandler = InvocationHandler.builder()
                .config(graphQlConfig)
                .schema(buildSchema())
                .build();

        WebServer server = WebServer.builder()
                .routing(r -> r.register(GraphQlService.builder()
                                         .config(graphQlConfig)
                                         .invocationHandler(invocationHandler)
                                         .permitAll(true)
                                         .build()))
                .build();
        // end::snippet_1[]
    }

    // tag::snippet_3[]
    @Service.GenerateBinding
    static class DeclarativeMain {
        public static void main(String[] args) {
            LogConfig.configureRuntime();
            ServiceRegistryManager.start(ApplicationBinding.create());
        }
    }
    // end::snippet_3[]

    // tag::snippet_4[]
    @GraphQlServer.Endpoint
    static class CatalogEndpoint {
        @GraphQl.Query
        Book book(@GraphQl.Argument("isbn") String isbn) {
            return new Book("Dune", BookStatus.AVAILABLE, List.of("classic"), isbn);
        }

        @GraphQl.Query
        String search(@GraphQl.Argument("criteria") @GraphQl.NonNull BookSearch criteria) {
            return criteria.phrase() + ":" + criteria.status();
        }

        @GraphQl.Mutation
        boolean update(@GraphQl.Argument("enabled") boolean enabled) {
            return enabled;
        }

        @GraphQlServer.Field
        String summary(@GraphQlServer.Source Book book,
                       @GraphQl.Argument("prefix") String prefix) {
            return prefix + ": " + book.title();
        }
    }

    @GraphQl.Entity
    @GraphQl.Description("Book result")
    record Book(@GraphQl.NonNull String title,
                @GraphQl.Name("state") BookStatus status,
                List<String> tags,
                @GraphQl.Ignore String internal) {
    }

    @GraphQl.Entity
    record BookSearch(@GraphQl.NonNull String phrase,
                      @GraphQl.NonNull BookStatus status) {
    }

    @GraphQl.Entity
    enum BookStatus {
        AVAILABLE,
        @GraphQl.Name("OUT_OF_PRINT")
        OUT
    }
    // end::snippet_4[]

    private static class ApplicationBinding {
        static Binding create() {
            return null;
        }
    }
}
