/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.examples.graphql.basics;

import java.util.List;

import io.helidon.graphql.server.GraphQlSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

/**
 * Main class of Graphql SE integration example.
 */
public class Main {

    private Main() {
    }

    /**
     * Start the example. Prints endpoints to standard output.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        WebServer server = WebServer.builder()
                .routing(Routing.builder()
                                 .register(GraphQlSupport.create(buildSchema()))
                                 .build())
                .build();

        server.start()
               .thenApply(webServer -> {
                   String endpoint = "http://localhost:" + webServer.port();
                   System.out.println("GraphQL started on " + endpoint + "/graphql");
                   System.out.println("GraphQL schema availanle on " + endpoint + "/graphql/schema.graphql");
                   return null;
               });
    }

    /**
     * Generate a {@link GraphQLSchema}.
     * @return  a {@link GraphQLSchema}
     */
    private static GraphQLSchema buildSchema() {
        String schema = "type Query{\n"
                + "hello: String \n"
                + "helloInDifferentLanguages: [String] \n"
                + "\n}";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        // DataFetcher to return various hello's in difference languages
        DataFetcher<List<String>> hellosDataFetcher = (DataFetcher<List<String>>) environment ->
                List.of("Bonjour", "Hola", "Zdravstvuyte", "Nǐn hǎo", "Salve", "Gudday", "Konnichiwa", "Guten Tag");

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("hello", new StaticDataFetcher("world")))
                .type("Query", builder -> builder.dataFetcher("helloInDifferentLanguages", hellosDataFetcher))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }
}
