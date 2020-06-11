/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.examples.dbclient.pokemons2;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MainTest {
    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());

    private static WebServer webServer;
    private static WebClient webClient;

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = PokemonMain.startServer();

        long timeout = 2000;
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

    @Test
    public void testPokemonTypes() throws Exception {
        webClient.get()
                .path("/type")
                .request(JsonArray.class)
                .thenAccept(array -> assertThat(array.size(), is(18)))
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testPokemons() throws Exception {
        assertThat(getPokemonCount(), is(6));

        webClient.get()
                .path("/pokemon/1")
                .request(JsonObject.class)
                .thenAccept(pokemon -> assertThat(pokemon.getString("NAME"), is("Bulbasaur")))
                .toCompletableFuture()
                .get();

        webClient.get()
                .path("/pokemon/name/Charmander")
                .request(JsonObject.class)
                .thenAccept(pokemon -> assertThat(pokemon.getJsonNumber("ID_TYPE").intValue(), is(10)))
                .toCompletableFuture()
                .get();

        JsonObject json = JSON_BUILDER.createObjectBuilder()
                .add("id", 100)
                .add("idType", 1)
                .add("name", "Test")
                .build();
        webClient.post()
                .path("/pokemon")
                .submit(json)
                .thenAccept(r -> assertThat(r.status(), is(Http.Status.OK_200)))
                .toCompletableFuture()
                .get();
        assertThat(getPokemonCount(), is(7));

        webClient.delete()
                .path("/pokemon/100")
                .request()
                .thenAccept(r -> assertThat(r.status(), is(Http.Status.OK_200)))
                .toCompletableFuture()
                .get();

        assertThat(getPokemonCount(), is(6));
    }

    private int getPokemonCount() throws Exception {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        webClient.get()
                .path("/pokemon")
                .request(JsonArray.class)
                .thenAccept(array -> result.complete(array.size()));
        return result.get();
    }
}
