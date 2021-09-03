/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.cdi.pokemon;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.json.JsonArray;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MainTest {

    private static Server server;
    private static Client client;

    @BeforeAll
    public static void startTheServer() {
        client = ClientBuilder.newClient();
        server = Server.create().start();
    }

    @AfterAll
    static void destroyClass() {
        CDI<Object> current = CDI.current();
        ((SeContainer) current).close();
    }

    @Test
    void testPokemonTypes() {
        JsonArray types = client.target(getConnectionString("/type"))
                .request()
                .get(JsonArray.class);
        assertThat(types.size(), is(18));
    }

    @Test
    void testPokemon() {
        assertThat(getPokemonCount(), is(6));

        Pokemon pokemon = client.target(getConnectionString("/pokemon/1"))
                .request()
                .get(Pokemon.class);
        assertThat(pokemon.getName(), is("Bulbasaur"));

        pokemon = client.target(getConnectionString("/pokemon/name/Charmander"))
                .request()
                .get(Pokemon.class);
        assertThat(pokemon.getType(), is(10));

        try (Response response = client.target(getConnectionString("/pokemon/1"))
                .request()
                .get()) {
            assertThat(response.getStatus(), is(200));
        }

        Pokemon test = new Pokemon();
        test.setType(1);
        test.setId(100);
        test.setName("Test");
        try (Response response = client.target(getConnectionString("/pokemon"))
                .request()
                .post(Entity.entity(test, MediaType.APPLICATION_JSON))) {
            assertThat(response.getStatus(), is(204));
            assertThat(getPokemonCount(), is(7));
        }

        try (Response response = client.target(getConnectionString("/pokemon/100"))
                .request()
                .delete()) {
            assertThat(response.getStatus(), is(204));
            assertThat(getPokemonCount(), is(6));
        }
    }

    private int getPokemonCount() {
        JsonArray pokemons = client.target(getConnectionString("/pokemon"))
                .request()
                .get(JsonArray.class);
        return pokemons.size();
    }

    private String getConnectionString(String path) {
        return "http://localhost:" + server.port() + path;
    }
}
