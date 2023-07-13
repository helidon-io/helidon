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
package io.helidon.tests.integration.dbclient.app.it;

import io.helidon.tests.integration.dbclient.app.model.Pokemon;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;
import io.helidon.tests.integration.tools.client.TestClient;

import jakarta.json.JsonObject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test data verification helper methods.
 */
public class VerifyData {

    private static final String POKEMON_ID_KEY = "id";
    private static final String POKEMON_NAME_KEY = "name";

    /**
     * Verify that returned data contain single record with expected data.
     *
     * @param actual   database query result to verify
     * @param expected data to compare with
     */
    public static void verifyPokemon(JsonObject actual, Pokemon expected) {
        assertThat(actual, notNullValue());
        assertThat(actual.isEmpty(), is(false));
        Integer id = actual.getInt(POKEMON_ID_KEY);
        String name = actual.getString(POKEMON_NAME_KEY);
        assertThat(id, equalTo(expected.getId()));
        assertThat(name, expected.getName().equals(name));
    }

    /**
     * Verify that returned data contain single record with expected data.
     *
     * @param actual   database query result to verify
     * @param expected data to compare with
     */
    public static void verifyPokemon(JsonObject actual, JsonObject expected) {
        assertThat(actual, notNullValue());
        assertThat(expected, notNullValue());
        Integer id = actual.getInt(POKEMON_ID_KEY);
        String name = actual.getString(POKEMON_NAME_KEY);
        assertThat(id, equalTo(expected.getInt(POKEMON_ID_KEY)));
        assertThat(name, equalTo(expected.getString(POKEMON_NAME_KEY)));
    }

    /**
     * Retrieve record as JSON object from remote web resource.
     *
     * @param testClient webclient used to access remote web resource
     * @param id         ID of record to retrieve
     * @return JSON object
     */
    public static JsonObject getPokemon(TestClient testClient, int id) {
        return testClient.callServiceAndGetData(
                        "Verify",
                        "getPokemonById",
                        QueryParams.single(QueryParams.ID, String.valueOf(id)))
                .asJsonObject();
    }

}
