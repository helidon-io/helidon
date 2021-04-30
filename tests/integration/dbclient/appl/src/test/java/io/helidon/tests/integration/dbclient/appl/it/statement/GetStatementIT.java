/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.appl.it.statement;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.tests.integration.dbclient.appl.it.LogData;
import io.helidon.tests.integration.dbclient.appl.it.VerifyData;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.tools.QueryParams;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test DbStatementGet methods.
 */
public class GetStatementIT {

    private static final Logger LOGGER = Logger.getLogger(GetStatementIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("GetStatement")
            .build();

    // Test executor method
    public void executeTest(final String testName, final int fromId, final int toId) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        JsonObject data = testClient.callServiceAndGetData(
                testName,
                QueryParams.builder()
                    .add(QueryParams.FROM_ID, String.valueOf(fromId))
                    .add(QueryParams.TO_ID, String.valueOf(toId))
                    .build()
        ).asJsonObject();
        LogData.logJsonObject(Level.FINER, data);
        int counter[] = { 0 };
        Pokemon.POKEMONS.keySet().forEach(id -> {
            if (id > fromId && id < toId) {
                final Pokemon pokemon = Pokemon.POKEMONS.get(id);
                VerifyData.verifyPokemon(data, pokemon);
                counter[0]++;
            }
        });
        assertThat(counter[0], equalTo(1));
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     */
    @Test
    public void testGetArrayParams() {
        executeTest("testGetArrayParams", 0, 2);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     */
    @Test
    public void testGetListParams() {
        executeTest("testGetListParams", 1, 3);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     */
    @Test
    public void testGetMapParams() {
        executeTest("testGetMapParams", 2, 4);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     */
    @Test
    public void testGetOrderParam() {
        executeTest("testGetOrderParam", 3, 5);

    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     */
    @Test
    public void testGetNamedParam() {
        executeTest("testGetNamedParam", 4, 6);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testGetMappedNamedParam() {
        executeTest("testGetMappedNamedParam", 5, 7);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testGetMappedOrderParam() {
        executeTest("testGetMappedOrderParam", 6, 8);
    }

}
