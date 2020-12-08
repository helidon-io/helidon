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

package io.helidon.examples.quickstart.se;

import java.util.concurrent.TimeUnit;

import javax.json.JsonArray;

import io.helidon.examples.integrations.neo4j.se.Main;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.Ignore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;

/**
 * Main test class for Neo4j Helidon SE quickstarter.
 */
public class MainTest {

    private static Neo4jContainer neo4jContainer;

    private static WebServer webServer;
    private static WebClient webClient;
    //private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());


    //@BeforeAll Decide if we need testcontainers.
    private static void startTheServer() throws Exception {

        neo4jContainer = new Neo4jContainer<>("neo4j:4.0")
                .withAdminPassword("secret");
        neo4jContainer.start();

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

        webServer = Main.startServer();

        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Failed to start webserver");
            }
        }

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    //@AfterAll
    private static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    //@AfterAll
    public static void stopNeo4j() {
        neo4jContainer.stop();
    }

    //@Test
    //@Ignore// Currently ignore. Decide if we need testcontainers.
    void testMovies() throws Exception {

        webClient.get()
                .path("api/movies")
                .request(JsonArray.class)
                .thenAccept(result -> Assertions.assertEquals("The Matrix", result.getJsonObject(0).getString("title")))
                .toCompletableFuture()
                .get();

    }

    //@Test
    //@Ignore// Currently ignore. Decide if we need testcontainers.
    public void testHealth() throws Exception {

        webClient.get()
                .path("/health")
                .request()
                .thenAccept(response -> Assertions.assertEquals(200, response.status().code()))
                .toCompletableFuture()
                .get();
    }

    //@Test
    //@Ignore// Currently ignore. Decide if we need testcontainers.
    public void testMetrics() throws Exception {
        webClient.get()
                .path("/metrics")
                .request()
                .thenAccept(response -> Assertions.assertEquals(200, response.status().code()))
                .toCompletableFuture()
                .get();
    }

}