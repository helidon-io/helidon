/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.webserver.WebServer;
import io.helidon.webserver.graphql.GraphQlService;

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
        WebServer server = WebServer.builder()
                .routing(r -> r.register(GraphQlService.create(buildSchema())))
                .build();
        // end::snippet_1[]
    }
}
