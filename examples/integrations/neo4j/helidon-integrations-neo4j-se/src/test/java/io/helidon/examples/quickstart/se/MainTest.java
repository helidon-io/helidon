package io.helidon.examples.quickstart.se;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.examples.integrations.neo4j.se.Main;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;

import org.testcontainers.containers.Neo4jContainer;

/**
 * @author Dmitry Aleksandrov
 */


public class MainTest {

    private static Neo4jContainer neo4jContainer;

    private static WebServer webServer;
    private static WebClient webClient;


    @BeforeAll
    public static void startTheServer() throws Exception {

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

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @AfterAll
    public static void stopNeo4j() {
        neo4jContainer.stop();
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

}