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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to test DbStatementDml methods.
 */
public class DmlStatementService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(DmlStatementService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Single<Long>> {}

    public DmlStatementService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testDmlArrayParams", this::testDmlArrayParams)
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
            test.apply(updatedPokemon)
                    .thenAccept(
                            result -> response.send(
                                    AppResponse.okStatus(Json.createValue(result))))
                    .exceptionally(t -> {
                        response.send(exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.log(Level.WARNING, String.format("Error in SimpleUpdateService.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code params(Object... parameters)} parameters setting method.
    private void testDmlArrayParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlArrayParams",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedDmlStatement("update-pokemon-order-arg")
                                    .params(updatedPokemon.getName(), updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code params(List<?>)} parameters setting method.
    private void testDmlListParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlListParams",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> {
                                List<Object> params = new ArrayList<>(2);
                                params.add(updatedPokemon.getName());
                                params.add(updatedPokemon.getId());
                                return exec
                                        .createNamedDmlStatement("update-pokemon-order-arg")
                                        .params(params)
                                        .execute();
                            }
                    ));
    }

    // Verify {@code params(Map<?>)} parameters setting method.
    private void testDmlMapParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlMapParams",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> {
                                Map<String, Object> params = new HashMap<>(2);
                                params.put("name", updatedPokemon.getName());
                                params.put("id", updatedPokemon.getId());
                                return exec
                                        .createNamedDmlStatement("update-pokemon-named-arg")
                                        .params(params)
                                        .execute();
                            }
                    ));
    }

    // Verify {@code addParam(Object parameter)} parameters setting method.
    private void testDmlOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlOrderParam",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedDmlStatement("update-pokemon-order-arg")
                                    .addParam(updatedPokemon.getName())
                                    .addParam(updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code addParam(String name, Object parameter)} parameters setting method.
    private void testDmlNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlNamedParam",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedDmlStatement("update-pokemon-named-arg")
                                    .addParam("name", updatedPokemon.getName())
                                    .addParam("id", updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code namedParam(Object parameters)} mapped parameters setting method.
    private void testDmlMappedNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlMappedNamedParam",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedDmlStatement("update-pokemon-named-arg")
                                    .namedParam(updatedPokemon)
                                    .execute()
                    ));
    }

    // Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
    private void testDmlMappedOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlMappedOrderParam",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedDmlStatement("update-pokemon-order-arg")
                                    .indexedParam(updatedPokemon)
                                    .execute()
                    ));
    }

}
