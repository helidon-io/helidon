/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.http.Http;
import io.helidon.examples.integrations.neo4j.se.Main;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import jakarta.json.JsonArray;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Main test class for Neo4j Helidon SE quickstarter.
 */
public class MainTest {

    private static WebServer webServer;
    private static WebClient webClient;

    private static Neo4j embeddedDatabaseServer;

    @BeforeAll
    static void startTheServer() {

        embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withFixture(FIXTURE)
                .build();

        System.setProperty("neo4j.uri", embeddedDatabaseServer.boltURI().toString());

        webServer = Main.startServer().await();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (webServer != null) {
            webServer.shutdown()
                    .await(10, TimeUnit.SECONDS);
        }
        if (embeddedDatabaseServer != null) {
            embeddedDatabaseServer.close();
        }
    }

    @Test
    void testMovies() {

        JsonArray result = webClient.get()
                .path("api/movies")
                .request(JsonArray.class)
                .await();

        assertThat(result.getJsonObject(0).getString("title"), is("The Matrix Reloaded"));
    }

    @Test
    public void testHealth() {

        WebClientResponse response = webClient.get()
                .path("/health")
                .request()
                .await();

        assertThat(response.status(), is(Http.Status.OK_200));
    }

    @Test
    public void testMetrics() {
        WebClientResponse response = webClient.get()
                .path("/metrics")
                .request()
                .await();

        assertThat(response.status(), is(Http.Status.OK_200));
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