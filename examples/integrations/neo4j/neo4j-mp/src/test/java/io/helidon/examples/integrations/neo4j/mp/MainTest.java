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

package io.helidon.examples.integrations.neo4j.mp;

import io.helidon.microprofile.server.Server;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Main tests of the application done here.
 */
class MainTest {
    private static Server server;
    private static Neo4j embeddedDatabaseServer;

    @BeforeAll
    public static void startTheServer() throws Exception {

        embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withFixture(FIXTURE)
                .build();

        System.setProperty("neo4j.uri", embeddedDatabaseServer.boltURI().toString());

        server = Server.create().start();

    }

    @AfterAll
    static void destroyClass() {
        CDI<Object> current = CDI.current();
        ((SeContainer) current).close();
        embeddedDatabaseServer.close();
    }


    @Test
    void testMovies() {

        Client client = ClientBuilder.newClient();

        JsonArray jsonArray = client
                .target(getConnectionString("/movies"))
                .request()
                .get(JsonArray.class);
        JsonObject first = jsonArray.getJsonObject(0);
        assertThat(first.getString("title"), is("The Matrix"));

    }

    private String getConnectionString(String path) {
        return "http://localhost:" + server.port() + path;
    }

    static final String FIXTURE = """
            CREATE (TheMatrix:Movie {title:'The Matrix', released:1999, tagline:'Welcome to the Real World'})
            CREATE (Keanu:Person {name:'Keanu Reeves', born:1964})
            CREATE (Carrie:Person {name:'Carrie-Anne Moss', born:1967})
            CREATE (Laurence:Person {name:'Laurence Fishburne', born:1961})
            CREATE (Hugo:Person {name:'Hugo Weaving', born:1960})
            CREATE (LillyW:Person {name:'Lilly Wachowski', born:1967})
            CREATE (LanaW:Person {name:'Lana Wachowski', born:1965})
            CREATE (JoelS:Person {name:'Joel Silver', born:1952})
            CREATE (KevinB:Person {name:'Kevin Bacon', born:1958})
            CREATE
            (Keanu)-[:ACTED_IN {roles:['Neo']}]->(TheMatrix),
            (Carrie)-[:ACTED_IN {roles:['Trinity']}]->(TheMatrix),
            (Laurence)-[:ACTED_IN {roles:['Morpheus']}]->(TheMatrix),
            (Hugo)-[:ACTED_IN {roles:['Agent Smith']}]->(TheMatrix),
            (LillyW)-[:DIRECTED]->(TheMatrix),
            (LanaW)-[:DIRECTED]->(TheMatrix),
            (JoelS)-[:PRODUCED]->(TheMatrix)

            CREATE (Emil:Person {name:"Emil Eifrem", born:1978})
            CREATE (Emil)-[:ACTED_IN {roles:["Emil"]}]->(TheMatrix)

            CREATE (TheMatrixReloaded:Movie {title:'The Matrix Reloaded', released:2003, tagline:'Free your mind'})
            CREATE
            (Keanu)-[:ACTED_IN {roles:['Neo']}]->(TheMatrixReloaded),
            (Carrie)-[:ACTED_IN {roles:['Trinity']}]->(TheMatrixReloaded),
            (Laurence)-[:ACTED_IN {roles:['Morpheus']}]->(TheMatrixReloaded),
            (Hugo)-[:ACTED_IN {roles:['Agent Smith']}]->(TheMatrixReloaded),
            (LillyW)-[:DIRECTED]->(TheMatrixReloaded),
            (LanaW)-[:DIRECTED]->(TheMatrixReloaded),
            (JoelS)-[:PRODUCED]->(TheMatrixReloaded)

            CREATE (TheMatrixRevolutions:Movie {title:'The Matrix Revolutions', released:2003, tagline:'Everything that has a beginning has an end'})
            CREATE
            (Keanu)-[:ACTED_IN {roles:['Neo']}]->(TheMatrixRevolutions),
            (Carrie)-[:ACTED_IN {roles:['Trinity']}]->(TheMatrixRevolutions),
            (Laurence)-[:ACTED_IN {roles:['Morpheus']}]->(TheMatrixRevolutions),
            (KevinB)-[:ACTED_IN {roles:['Unknown']}]->(TheMatrixRevolutions),
            (Hugo)-[:ACTED_IN {roles:['Agent Smith']}]->(TheMatrixRevolutions),
            (LillyW)-[:DIRECTED]->(TheMatrixRevolutions),
            (LanaW)-[:DIRECTED]->(TheMatrixRevolutions),
            (JoelS)-[:PRODUCED]->(TheMatrixRevolutions)
            """;
}
