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
package io.helidon.tests.integration.dbclient.appl.it;

import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.TestClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test data verification helper methods.
 */
public class VerifyData {

    private static final Logger LOGGER = Logger.getLogger(VerifyData.class.getName());

    private static final String POKEMON_ID_KEY = "id";
    private static final String POKEMON_NAME_KEY = "name";

    /**
     * Verify that returned data contain single record with provided pokemon.
     *
     * @param data database query result to verify
     * @param pokemon pokemon to compare with
     */
    public static void verifyPokemon(JsonObject data, Pokemon pokemon) {
        assertThat(data, notNullValue());
        Integer id = data.getInt(POKEMON_ID_KEY);
        String name = data.getString(POKEMON_NAME_KEY);
        assertThat(id, equalTo(pokemon.getId()));
        assertThat(name, pokemon.getName().equals(name));
    }

    /**
     * Verify that returned data contain single record with provided pokemon.
     *
     * @param data database query result to verify
     * @param pokemon pokemon to compare with
     */
    public static void verifyPokemon(JsonObject data, JsonObject pokemon) {
        assertThat(data, notNullValue());
        assertThat(pokemon, notNullValue());
        Integer id = data.getInt(POKEMON_ID_KEY);
        String name = data.getString(POKEMON_NAME_KEY);
        assertThat(id, equalTo(pokemon.getInt(POKEMON_ID_KEY)));
        assertThat(name, equalTo(pokemon.getString(POKEMON_NAME_KEY)));
    }

    /**
     * Retrieve Pokemon as JSON object from remote web resource.
     *
     * @param testClient webclient used to access remote web resource
     * @param id ID of pokemon to retrieve
     * @return pokemon stored in JSON object
     */
    public static JsonObject getPokemon(final TestClient testClient, final int id) {
        return testClient.callServiceAndGetData(
                "Verify",
                "getPokemonById",
                QueryParams.single(QueryParams.ID, String.valueOf(id))
        ).asJsonObject();
    }

}
