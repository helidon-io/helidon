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

import javax.json.JsonArray;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

/**
 * Test DbStatementQuery methods.
 */
public class QueryStatementIT {

   /** Local logger instance. */
    static final Logger LOGGER = Logger.getLogger(QueryStatementIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("QueryStatement")
            .build();

    // Test executor method
    private void executeTest(final String testName, final int fromId, final int toId) {
        LOGGER.fine(() -> String.format("Running %s", testName));
        JsonArray data = testClient.callServiceAndGetData(
                testName,
                QueryParams.builder()
                    .add(QueryParams.FROM_ID, String.valueOf(fromId))
                    .add(QueryParams.TO_ID, String.valueOf(toId))
                    .build()
        ).asJsonArray();
        LogData.logJsonArray(Level.FINER, data);
        assertThat(data.size(), equalTo(toId - fromId - 1));
        data.getValuesAs(JsonObject.class).forEach(dataPokemon -> {
            int id = dataPokemon.getInt("id");
            final Pokemon pokemon = Pokemon.POKEMONS.get(id);
            assertThat(id, greaterThan(fromId));
            assertThat(id, lessThan(toId));
            VerifyData.verifyPokemon(dataPokemon, pokemon);
        });
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     */
    @Test
    public void testQueryArrayParams() {
        executeTest("testQueryArrayParams", 0, 7);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     */
    @Test
    public void testQueryListParams() {
        executeTest("testQueryListParams", 0, 7);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     */
    @Test
    public void testQueryMapParams() {
        executeTest("testQueryMapParams", 0, 7);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     */
    @Test
    public void testQueryOrderParam() {
        executeTest("testQueryOrderParam", 0, 7);
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     */
    @Test
    public void testQueryNamedParam() {
        executeTest("testQueryNamedParam", 0, 7);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testQueryMappedNamedParam() {
        executeTest("testQueryMappedNamedParam", 0, 7);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testQueryMappedOrderParam() {
        executeTest("testQueryMappedOrderParam", 0, 7);
    }

}
