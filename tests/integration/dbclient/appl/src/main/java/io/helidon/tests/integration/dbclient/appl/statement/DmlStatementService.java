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
package io.helidon.tests.integration.dbclient.appl.statement;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.helidon.dbclient.DbClient;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource to test DbStatementDml methods.
 */
public class DmlStatementService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(DmlStatementService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Long> {
    }

    public DmlStatementService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/testDmlArrayParams", this::testDmlArrayParams)
                .get("/testDmlListParams", this::testDmlListParams)
                .get("/testDmlMapParams", this::testDmlMapParams)
                .get("/testDmlOrderParam", this::testDmlOrderParam)
                .get("/testDmlNamedParam", this::testDmlNamedParam)
                .get("/testDmlMappedNamedParam", this::testDmlMappedNamedParam)
                .get("/testDmlMappedOrderParam", this::testDmlMappedOrderParam);
    }

    // Common test execution code
    private void executeTest(ServerRequest request, ServerResponse response, String testName, TestFunction test) {
        try {
            String name = param(request, QUERY_NAME_PARAM);
            String idStr = param(request, QUERY_ID_PARAM);
            int id = Integer.parseInt(idStr);
            Pokemon srcPokemon = Pokemon.POKEMONS.get(id);
            Pokemon updatedPokemon = new Pokemon(id, name, srcPokemon.getTypesArray());
            long count = test.apply(updatedPokemon);
            response.send(okStatus(Json.createValue(count)));
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.log(Level.WARNING, String.format("Error in SimpleUpdateService.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code params(Object... parameters)} parameters setting method.
    private void testDmlArrayParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlArrayParams",
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("update-pokemon-order-arg")
                        .params(pokemon.getName(), pokemon.getId())
                        .execute());
    }

    // Verify {@code params(List<?>)} parameters setting method.
    private void testDmlListParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlListParams",
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("update-pokemon-order-arg")
                        .params(List.of(pokemon.getName(), pokemon.getId()))
                        .execute());
    }

    // Verify {@code params(Map<?>)} parameters setting method.
    private void testDmlMapParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlMapParams",
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("update-pokemon-named-arg")
                        .params(Map.of("name", pokemon.getName(), "id", pokemon.getId()))
                        .execute());
    }

    // Verify {@code addParam(Object parameter)} parameters setting method.
    private void testDmlOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlOrderParam",
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("update-pokemon-order-arg")
                        .addParam(pokemon.getName())
                        .addParam(pokemon.getId())
                        .execute());
    }

    // Verify {@code addParam(String name, Object parameter)} parameters setting method.
    private void testDmlNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlNamedParam",
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("update-pokemon-named-arg")
                        .addParam("name", pokemon.getName())
                        .addParam("id", pokemon.getId())
                        .execute());
    }

    // Verify {@code namedParam(Object parameters)} mapped parameters setting method.
    private void testDmlMappedNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlMappedNamedParam",
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("update-pokemon-named-arg")
                        .namedParam(pokemon)
                        .execute());
    }

    // Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
    private void testDmlMappedOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlMappedOrderParam",
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("update-pokemon-order-arg")
                        .indexedParam(pokemon)
                        .execute());
    }

}
