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

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link io.helidon.examples.dbclient.pokemons.PokemonService}.
 */
@ServerTest
class PokemonServiceTest {

    private static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(Map.of());

    private final Http1Client client;

    PokemonServiceTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.register("/db", new PokemonService());
    }

    @Test
    void testListAllPokemons() {
        ClientResponseTyped<JsonArray> response = client.get("/db/pokemon").request(JsonArray.class);
        assertThat(response.status(), is(Status.OK_200));
        List<String> names = response.entity().stream().map(e -> e.asJsonObject().getString("NAME")).toList();
        assertThat(names, is(pokemonNames()));
    }

    @Test
    void testListAllPokemonTypes() {
        ClientResponseTyped<JsonArray> response = client.get("/db/type").request(JsonArray.class);
        assertThat(response.status(), is(Status.OK_200));
        List<String> names = response.entity().stream().map(e -> e.asJsonObject().getString("NAME")).toList();
        assertThat(names, is(pokemonTypes()));
    }

    @Test
    void testGetPokemonById() {
        ClientResponseTyped<JsonObject> response = client.get("/db/pokemon/2").request(JsonObject.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity().getString("NAME"), is("Charmander"));
    }

    @Test
    void testGetPokemonByName() {
        ClientResponseTyped<JsonObject> response = client.get("/db/pokemon/name/Squirtle").request(JsonObject.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity().getInt("ID"), is(3));
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
            return reader.readArray().stream().map(e -> e.asJsonObject().getString("name")).toList();
        }
    }

    private static List<String> pokemonTypes() {
        try (JsonReader reader = Json.createReader(PokemonService.class.getResourceAsStream("/pokemon-types.json"))) {
            return reader.readArray().stream().map(e -> e.asJsonObject().getString("name")).toList();
        }
    }

}
