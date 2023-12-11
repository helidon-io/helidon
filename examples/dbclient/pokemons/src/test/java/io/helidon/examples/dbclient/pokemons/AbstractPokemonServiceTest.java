/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.dbclient.pokemons;

import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

abstract class AbstractPokemonServiceTest {
    private static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(Map.of());

    private static WebServer server;
    private static WebClient client;

    static void beforeAll() {
        server = Main.setupServer(WebServer.builder());
        client = WebClient.create(config -> config.baseUri("http://localhost:" + server.port())
                .addMediaSupport(JsonpSupport.create()));
    }

    static void afterAll() {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    @Test
    void testListAllPokemons() {
        ClientResponseTyped<JsonArray> response = client.get("/db/pokemon").request(JsonArray.class);
        assertThat(response.status(), is(Status.OK_200));
        List<String> names = response.entity().stream().map(AbstractPokemonServiceTest::mapName).toList();
        assertThat(names, is(pokemonNames()));
    }

    @Test
    void testListAllPokemonTypes() {
        ClientResponseTyped<JsonArray> response = client.get("/db/type").request(JsonArray.class);
        assertThat(response.status(), is(Status.OK_200));
        List<String> names = response.entity().stream().map(AbstractPokemonServiceTest::mapName).toList();
        assertThat(names, is(pokemonTypes()));
    }

    @Test
    void testGetPokemonById() {
        ClientResponseTyped<JsonObject> response = client.get("/db/pokemon/2").request(JsonObject.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(name(response.entity()), is("Charmander"));
    }

    @Test
    void testGetPokemonByName() {
        ClientResponseTyped<JsonObject> response = client.get("/db/pokemon/name/Squirtle").request(JsonObject.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(id(response.entity()), is(3));
    }

    @Test
    void testAddUpdateDeletePokemon() {
        JsonObject pokemon;
        ClientResponseTyped<String> response;

        // add a new Pokémon Rattata
        pokemon = JSON_FACTORY.createObjectBuilder()
                .add("id", 7)
                .add("name", "Rattata")
                .add("idType", 1)
                .build();
        response = client.post("/db/pokemon").submit(pokemon, String.class);
        assertThat(response.status(), is(Status.CREATED_201));

        // rename Pokémon with id 7 to Raticate
        pokemon = JSON_FACTORY.createObjectBuilder()
                .add("id", 7)
                .add("name", "Raticate")
                .add("idType", 2)
                .build();

        response = client.put("/db/pokemon").submit(pokemon, String.class);
        assertThat(response.status(), is(Status.OK_200));

        // delete Pokémon with id 7
        response = client.delete("/db/pokemon/7").request(String.class);
        assertThat(response.status(), is(Status.NO_CONTENT_204));

        response = client.get("/db/pokemon/7").request(String.class);
        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }

    private static List<String> pokemonNames() {
        try (JsonReader reader = Json.createReader(PokemonService.class.getResourceAsStream("/pokemons.json"))) {
            return reader.readArray().stream().map(AbstractPokemonServiceTest::mapName).toList();
        }
    }

    private static List<String> pokemonTypes() {
        try (JsonReader reader = Json.createReader(PokemonService.class.getResourceAsStream("/pokemon-types.json"))) {
            return reader.readArray().stream().map(AbstractPokemonServiceTest::mapName).toList();
        }
    }

    private static String mapName(JsonValue value) {
        return name(value.asJsonObject());
    }

    private static String name(JsonObject json) {
        return json.containsKey("name")
                ? json.getString("name")
                : json.getString("NAME");
    }

    private static int id(JsonObject json) {
        return json.containsKey("id")
                ? json.getInt("id")
                : json.getInt("ID");
    }

}
