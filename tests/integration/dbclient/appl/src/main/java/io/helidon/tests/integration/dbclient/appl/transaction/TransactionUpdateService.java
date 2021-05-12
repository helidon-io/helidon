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
package io.helidon.tests.integration.dbclient.appl.transaction;

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
 * Web resource to test set of basic DbCliebnt updates in transaction.
 */
public class TransactionUpdateService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(TransactionUpdateService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Single<Long>> {}

    public TransactionUpdateService(final DbClient dbClient, final Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testCreateNamedUpdateStrStrNamedArgs", this::testCreateNamedUpdateStrStrNamedArgs)
                .get("/testCreateNamedUpdateStrNamedArgs", this::testCreateNamedUpdateStrNamedArgs)
                .get("/testCreateNamedUpdateStrOrderArgs", this::testCreateNamedUpdateStrOrderArgs)
                .get("/testCreateUpdateNamedArgs", this::testCreateUpdateNamedArgs)
                .get("/testCreateUpdateOrderArgs", this::testCreateUpdateOrderArgs)
                .get("/testNamedUpdateNamedArgs", this::testNamedUpdateNamedArgs)
                .get("/testUpdateOrderArgs", this::testUpdateOrderArgs)
                .get("/testCreateNamedDmlWithUpdateStrStrNamedArgs", this::testCreateNamedDmlWithUpdateStrStrNamedArgs)
                .get("/testCreateNamedDmlWithUpdateStrNamedArgs", this::testCreateNamedDmlWithUpdateStrNamedArgs)
                .get("/testCreateNamedDmlWithUpdateStrOrderArgs", this::testCreateNamedDmlWithUpdateStrOrderArgs)
                .get("/testCreateDmlWithUpdateNamedArgs", this::testCreateDmlWithUpdateNamedArgs)
                .get("/testCreateDmlWithUpdateOrderArgs", this::testCreateDmlWithUpdateOrderArgs)
                .get("/testNamedDmlWithUpdateOrderArgs", this::testNamedDmlWithUpdateOrderArgs)
                .get("/testDmlWithUpdateOrderArgs", this::testDmlWithUpdateOrderArgs);
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

    // Verify {@code createNamedUpdate(String, String)} API method with ordered parameters.
    private JsonObject testCreateNamedUpdateStrStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedUpdate("update-spearow", statement("update-pokemon-named-arg"))
                                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code createNamedUpdate(String)} API method with named parameters.
    private JsonObject testCreateNamedUpdateStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrNamedArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedUpdate("update-pokemon-named-arg")
                                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                                .execute()
                ));
    }


    // Verify {@code createNamedUpdate(String)} API method with ordered parameters.
    private JsonObject testCreateNamedUpdateStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedUpdateStrOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedUpdate("update-pokemon-order-arg")
                                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code createUpdate(String)} API method with named parameters.
    private JsonObject testCreateUpdateNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateUpdateNamedArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createUpdate(statement("update-pokemon-named-arg"))
                                .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code createUpdate(String)} API method with ordered parameters.
    private JsonObject testCreateUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateUpdateOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createUpdate(statement("update-pokemon-order-arg"))
                                .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code namedUpdate(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
    private JsonObject testNamedUpdateNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testNamedUpdateNamedArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .namedUpdate("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId())
                ));
    }

    // Verify {@code update(String)} API method with ordered parameters passed directly to the {@code query} method.
    private JsonObject testUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testUpdateOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .update(statement("update-pokemon-order-arg"), updatedPokemon.getName(), updatedPokemon.getId())
                ));
    }

    // DML update

    // Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
    private JsonObject testCreateNamedDmlWithUpdateStrStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testUpdateOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedDmlStatement("update-piplup", statement("update-pokemon-named-arg"))
                                .addParam("name", updatedPokemon.getName())
                                .addParam("id", updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
    private JsonObject testCreateNamedDmlWithUpdateStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testUpdateOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedDmlStatement("update-pokemon-named-arg")
                                .addParam("name", updatedPokemon.getName())
                                .addParam("id", updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
    private JsonObject testCreateNamedDmlWithUpdateStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testUpdateOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedDmlStatement("update-pokemon-order-arg")
                                .addParam(updatedPokemon.getName())
                                .addParam(updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with update with named parameters.
    private JsonObject testCreateDmlWithUpdateNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testUpdateOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createDmlStatement(statement("update-pokemon-named-arg"))
                                .addParam("name", updatedPokemon.getName())
                                .addParam("id", updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
    private JsonObject testCreateDmlWithUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testUpdateOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createDmlStatement(statement("update-pokemon-order-arg"))
                                .addParam(updatedPokemon.getName())
                                .addParam(updatedPokemon.getId())
                                .execute()
                ));
    }

    // Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
    private JsonObject testNamedDmlWithUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testUpdateOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .namedDml("update-pokemon-order-arg",
                                        updatedPokemon.getName(),
                                        updatedPokemon.getId())
                ));
    }

    // Verify {@code dml(String)} API method with update with ordered parameters passed directly
    private JsonObject testDmlWithUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testUpdateOrderArgs",
                (updatedPokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .dml(statement("update-pokemon-order-arg"),
                                        updatedPokemon.getName(),
                                        updatedPokemon.getId())
                ));
    }

}
