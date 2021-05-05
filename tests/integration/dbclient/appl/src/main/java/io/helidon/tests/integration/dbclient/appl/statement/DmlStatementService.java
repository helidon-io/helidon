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
package io.helidon.tests.integration.dbclient.appl.statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to test DbStatementDml methods.
 */
public class DmlStatementService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(DmlStatementService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Single<Long>> {}

    public DmlStatementService(final DbClient dbClient, final Map<String, String> statements) {
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
    private JsonObject executeTest(final ServerRequest request, final ServerResponse response, final String testName, final TestFunction test) {
        LOGGER.fine(() -> String.format("Running SimpleUpdateService.%s on server", testName));
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
            LOGGER.fine(() -> String.format("Error in SimpleUpdateService.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
        return null;
    }

    // Verify {@code params(Object... parameters)} parameters setting method.
    private JsonObject testDmlArrayParams(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (updatedPokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("update-pokemon-order-arg")
                                .params(updatedPokemon.getName(), updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code params(List<?>)} parameters setting method.
    private JsonObject testDmlListParams(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
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
    private JsonObject testDmlMapParams(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
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
    private JsonObject testDmlOrderParam(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (updatedPokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("update-pokemon-order-arg")
                                .addParam(updatedPokemon.getName())
                                .addParam(updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code addParam(String name, Object parameter)} parameters setting method.
    private JsonObject testDmlNamedParam(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (updatedPokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("update-pokemon-named-arg")
                                .addParam("name", updatedPokemon.getName())
                                .addParam("id", updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code namedParam(Object parameters)} mapped parameters setting method.
    private JsonObject testDmlMappedNamedParam(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (updatedPokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("update-pokemon-named-arg")
                                .namedParam(updatedPokemon)
                                .execute()
                ));
    }

    // Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
    private JsonObject testDmlMappedOrderParam(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (updatedPokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("update-pokemon-order-arg")
                                .indexedParam(updatedPokemon)
                                .execute()
                ));
    }

}
