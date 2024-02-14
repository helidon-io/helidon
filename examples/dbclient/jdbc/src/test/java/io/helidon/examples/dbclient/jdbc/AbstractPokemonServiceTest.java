/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.dbclient.jdbc;

import java.util.List;
import java.util.Map;

import io.helidon.http.Status;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

abstract class AbstractPokemonServiceTest {
    private static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(Map.of());

    private static WebServer server;
    private static WebClient client;

    static void beforeAll() {
        server = JdbcExampleMain.setupServer(WebServer.builder());
        client = WebClient.create(config -> config.baseUri("http://localhost:" + server.port())
                .addMediaSupport(JsonpSupport.create()));
    }

    static void afterAll() {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    @Test
    void testListAndDeleteAllPokemons() {
        List<String> names = listAllPokemons();
        assertThat(names.isEmpty(), is(true));

        String endpoint = String.format("/db/%s/type/%s", "Raticate", 1);
        ClientResponseTyped<String> response = client.post(endpoint).request(String.class);
        assertThat(response.status(), is(Status.OK_200));

        names = listAllPokemons();
        assertThat(names.size(), is(1));
        assertThat(names.getFirst(), is("Raticate"));

        response = client.delete("/db").request(String.class);
        assertThat(response.status(), is(Status.OK_200));

        names = listAllPokemons();
        assertThat(names.isEmpty(), is(true));
    }

    @Test
    void testAddUpdateDeletePokemon() {
        ClientResponseTyped<String> response;
        ClientResponseTyped<JsonObject> jsonResponse;
        JsonObject pokemon = JSON_FACTORY.createObjectBuilder()
                .add("type", 1)
                .add("name", "Raticate")
                .build();

        // Add new pokemon
        response = client.put("/db").submit(pokemon, String.class);
        assertThat(response.entity(), is("Inserted: 1 values"));

        // Get the new pokemon added
        jsonResponse = client.get("/db/Raticate").request(JsonObject.class);
        assertThat(jsonResponse.status(), is(Status.OK_200));
        assertThat(getName(jsonResponse.entity()), is("Raticate"));
        assertThat(getType(jsonResponse.entity()), is("1"));

        // Update pokemon
        response = client.put("/db/Raticate/type/2").request(String.class);
        assertThat(response.status(), is(Status.OK_200));

        // Verify updated pokemon
        jsonResponse = client.get("/db/Raticate").request(JsonObject.class);
        assertThat(jsonResponse.status(), is(Status.OK_200));
        assertThat(getName(jsonResponse.entity()), is("Raticate"));
        assertThat(getType(jsonResponse.entity()), is("2"));

        // Delete Pokemon
        response = client.delete("/db/Raticate").request(String.class);
        assertThat(response.status(), is(Status.OK_200));

        // Verify pokemon is correctly deleted
        response = client.get("/db/Raticate").request(String.class);
        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }

    private List<String> listAllPokemons() {
        ClientResponseTyped<JsonArray> response = client.get("/db").request(JsonArray.class);
        assertThat(response.status(), is(Status.OK_200));
        return response.entity().stream().map(e -> getName(e.asJsonObject())).toList();
    }

    private String getName(JsonObject json) {
        return json.containsKey("name")
                ? json.getString("name")
                : json.getString("NAME");
    }

    private String getType(JsonObject json) {
        return json.containsKey("type")
                ? json.getString("type")
                : json.getString("TYPE");
    }
}
