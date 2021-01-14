/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

/**
 * Main test class for Neo4j Helidon SE quickstarter.
 */
public class MainTest {

    private static WebServer webServer;
    private static WebClient webClient;

    private static Neo4j embeddedDatabaseServer;

    @BeforeAll
    private static void startTheServer() throws Exception {


        embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withFixture(FIXTURE)
                .build();

        System.setProperty("neo4j.uri", embeddedDatabaseServer.boltURI().toString());

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

    @AfterAll
    private static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
        embeddedDatabaseServer.close();
    }

    @Test
    void testMovies() throws Exception {

        webClient.get()
                .path("api/movies")
                .request(JsonArray.class)
                .thenAccept(result -> Assertions.assertEquals("The Matrix", result.getJsonObject(0).getString("title")))
                .toCompletableFuture()
                .get();

    }

    @Test
    public void testHealth() throws Exception {

        webClient.get()
                .path("/health")
                .request()
                .thenAccept(response -> Assertions.assertEquals(200, response.status().code()))
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testMetrics() throws Exception {
        webClient.get()
                .path("/metrics")
                .request()
                .thenAccept(response -> Assertions.assertEquals(200, response.status().code()))
                .toCompletableFuture()
                .get();
    }

    static final String FIXTURE = ""
            + "CREATE (TheMatrix:Movie {title:'The Matrix', released:1999, tagline:'Welcome to the Real World'})\n"
            + "CREATE (Keanu:Person {name:'Keanu Reeves', born:1964})\n"
            + "CREATE (Carrie:Person {name:'Carrie-Anne Moss', born:1967})\n"
            + "CREATE (Laurence:Person {name:'Laurence Fishburne', born:1961})\n"
            + "CREATE (Hugo:Person {name:'Hugo Weaving', born:1960})\n"
            + "CREATE (LillyW:Person {name:'Lilly Wachowski', born:1967})\n"
            + "CREATE (LanaW:Person {name:'Lana Wachowski', born:1965})\n"
            + "CREATE (JoelS:Person {name:'Joel Silver', born:1952})\n"
            + "CREATE (KevinB:Person {name:'Kevin Bacon', born:1958})\n"
            + "CREATE\n"
            + "(Keanu)-[:ACTED_IN {roles:['Neo']}]->(TheMatrix),\n"
            + "(Carrie)-[:ACTED_IN {roles:['Trinity']}]->(TheMatrix),\n"
            + "(Laurence)-[:ACTED_IN {roles:['Morpheus']}]->(TheMatrix),\n"
            + "(Hugo)-[:ACTED_IN {roles:['Agent Smith']}]->(TheMatrix),\n"
            + "(LillyW)-[:DIRECTED]->(TheMatrix),\n"
            + "(LanaW)-[:DIRECTED]->(TheMatrix),\n"
            + "(JoelS)-[:PRODUCED]->(TheMatrix)\n"
            + "\n"
            + "CREATE (Emil:Person {name:\"Emil Eifrem\", born:1978})\n"
            + "CREATE (Emil)-[:ACTED_IN {roles:[\"Emil\"]}]->(TheMatrix)\n"
            + "\n"
            + "CREATE (TheMatrixReloaded:Movie {title:'The Matrix Reloaded', released:2003, tagline:'Free your mind'})\n"
            + "CREATE\n"
            + "(Keanu)-[:ACTED_IN {roles:['Neo']}]->(TheMatrixReloaded),\n"
            + "(Carrie)-[:ACTED_IN {roles:['Trinity']}]->(TheMatrixReloaded),\n"
            + "(Laurence)-[:ACTED_IN {roles:['Morpheus']}]->(TheMatrixReloaded),\n"
            + "(Hugo)-[:ACTED_IN {roles:['Agent Smith']}]->(TheMatrixReloaded),\n"
            + "(LillyW)-[:DIRECTED]->(TheMatrixReloaded),\n"
            + "(LanaW)-[:DIRECTED]->(TheMatrixReloaded),\n"
            + "(JoelS)-[:PRODUCED]->(TheMatrixReloaded)\n"
            + "\n"
            + "CREATE (TheMatrixRevolutions:Movie {title:'The Matrix Revolutions', released:2003, tagline:'Everything that has a beginning has an end'})\n"
            + "CREATE\n"
            + "(Keanu)-[:ACTED_IN {roles:['Neo']}]->(TheMatrixRevolutions),\n"
            + "(Carrie)-[:ACTED_IN {roles:['Trinity']}]->(TheMatrixRevolutions),\n"
            + "(Laurence)-[:ACTED_IN {roles:['Morpheus']}]->(TheMatrixRevolutions),\n"
            + "(KevinB)-[:ACTED_IN {roles:['Unknown']}]->(TheMatrixRevolutions),\n"
            + "(Hugo)-[:ACTED_IN {roles:['Agent Smith']}]->(TheMatrixRevolutions),\n"
            + "(LillyW)-[:DIRECTED]->(TheMatrixRevolutions),\n"
            + "(LanaW)-[:DIRECTED]->(TheMatrixRevolutions),\n"
            + "(JoelS)-[:PRODUCED]->(TheMatrixRevolutions)\n";
}