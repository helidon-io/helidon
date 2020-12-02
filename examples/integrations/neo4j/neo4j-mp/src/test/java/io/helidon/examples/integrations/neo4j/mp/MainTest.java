/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.neo4j.mp;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import io.helidon.microprofile.server.Server;

import org.junit.Ignore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;

/**
 * Main tests of the application done here.
 */
class MainTest {
    private static Server server;
    private static Neo4jContainer neo4jContainer;

    //@BeforeAll Decide if we need testcontainers.
    public static void startTheServer() throws Exception {
        neo4jContainer = new Neo4jContainer<>("neo4j:4.0")
                .withAdminPassword("secret");
        neo4jContainer.start();

        server = Server.create().start();

        try (var driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic("neo4j", "secret"));
                var session = driver.session()
        ) {
            session.writeTransaction(tx -> tx.run(""
                                                          + "CREATE (TheMatrix:Movie {title:'The Matrix', released:1999, tagline:'Welcome to the Real World'})\n"
                                                          + "CREATE (Keanu:Person {name:'Keanu Reeves', born:1964})\n"
                                                          + "CREATE (Carrie:Person {name:'Carrie-Anne Moss', born:1967})\n"
                                                          + "CREATE (Laurence:Person {name:'Laurence Fishburne', born:1961})\n"
                                                          + "CREATE (Hugo:Person {name:'Hugo Weaving', born:1960})\n"
                                                          + "CREATE (LillyW:Person {name:'Lilly Wachowski', born:1967})\n"
                                                          + "CREATE (LanaW:Person {name:'Lana Wachowski', born:1965})\n"
                                                          + "CREATE (JoelS:Person {name:'Joel Silver', born:1952})\n"
                                                          + "CREATE\n"
                                                          + "  (Keanu)-[:ACTED_IN {roles:['Neo']}]->(TheMatrix),\n"
                                                          + "  (Carrie)-[:ACTED_IN {roles:['Trinity']}]->(TheMatrix),\n"
                                                          + "  (Laurence)-[:ACTED_IN {roles:['Morpheus']}]->(TheMatrix),\n"
                                                          + "  (Hugo)-[:ACTED_IN {roles:['Agent Smith']}]->(TheMatrix),\n"
                                                          + "  (LillyW)-[:DIRECTED]->(TheMatrix),\n"
                                                          + "  (LanaW)-[:DIRECTED]->(TheMatrix),\n"
                                                          + "  (JoelS)-[:PRODUCED]->(TheMatrix)").consume()
            );
        }

        // Don't know how to set this dynamically otherwise in Helidon
        System.setProperty("neo4j.uri", neo4jContainer.getBoltUrl());
    }

    //@AfterAll
    static void destroyClass() {
        CDI<Object> current = CDI.current();
        ((SeContainer) current).close();
    }

    //@AfterAll
    public static void stopNeo4j() {
        neo4jContainer.stop();
    }

    @Test
    @Ignore// Currently ignore. Decide if we need testcontainers.
    void testMovies() {

        Client client = ClientBuilder.newClient();

        JsonArray jsorArray = client
                .target(getConnectionString("/movies"))
                .request()
                .get(JsonArray.class);
        JsonObject first = jsorArray.getJsonObject(0);
        Assertions.assertEquals("The Matrix", first.getString("title"));

    }

    private String getConnectionString(String path) {
        return "http://localhost:" + server.port() + path;
    }
}
