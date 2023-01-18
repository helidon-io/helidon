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
package io.helidon.tests.integration.dbclient.appl.simple;

import java.lang.System.Logger.Level;
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
 * Web resource to test set of basic DbCliebnt updates.
 */
public class SimpleUpdateService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(SimpleUpdateService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Single<Long>> {}

    public SimpleUpdateService(DbClient dbClient, Map<String, String> statements) {
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

    // Verify {@code createNamedUpdate(String, String)} API method with ordered parameters.
    private void testCreateNamedUpdateStrStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedUpdate("update-spearow", statement("update-pokemon-named-arg"))
                                    .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code createNamedUpdate(String)} API method with named parameters.
    private void testCreateNamedUpdateStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrNamedArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedUpdate("update-pokemon-named-arg")
                                    .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                                    .execute()
                    ));
    }


    // Verify {@code createNamedUpdate(String)} API method with ordered parameters.
    private void testCreateNamedUpdateStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrOrderArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedUpdate("update-pokemon-order-arg")
                                    .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code createUpdate(String)} API method with named parameters.
    private void testCreateUpdateNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateUpdateNamedArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createUpdate(statement("update-pokemon-named-arg"))
                                    .addParam("name", updatedPokemon.getName()).addParam("id", updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code createUpdate(String)} API method with ordered parameters.
    private void testCreateUpdateOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateUpdateOrderArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createUpdate(statement("update-pokemon-order-arg"))
                                    .addParam(updatedPokemon.getName()).addParam(updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code namedUpdate(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
    private void testNamedUpdateNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testNamedUpdateNamedArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .namedUpdate("update-pokemon-order-arg", updatedPokemon.getName(), updatedPokemon.getId())
                    ));
    }

    // Verify {@code update(String)} API method with ordered parameters passed directly to the {@code query} method.
    private void testUpdateOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testUpdateOrderArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .update(statement("update-pokemon-order-arg"),
                                            updatedPokemon.getName(),
                                            updatedPokemon.getId())
                    ));
    }

    // DML update

    // Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
    private void testCreateNamedDmlWithUpdateStrStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDmlWithUpdateStrStrNamedArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedDmlStatement("update-piplup", statement("update-pokemon-named-arg"))
                                    .addParam("name", updatedPokemon.getName())
                                    .addParam("id", updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
    private void testCreateNamedDmlWithUpdateStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDmlWithUpdateStrNamedArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedDmlStatement("update-pokemon-named-arg")
                                    .addParam("name", updatedPokemon.getName())
                                    .addParam("id", updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
    private void testCreateNamedDmlWithUpdateStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDmlWithUpdateStrOrderArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createNamedDmlStatement("update-pokemon-order-arg")
                                    .addParam(updatedPokemon.getName())
                                    .addParam(updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code createDmlStatement(String)} API method with update with named parameters.
    private void testCreateDmlWithUpdateNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateDmlWithUpdateNamedArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createDmlStatement(statement("update-pokemon-named-arg"))
                                    .addParam("name", updatedPokemon.getName())
                                    .addParam("id", updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
    private void testCreateDmlWithUpdateOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateDmlWithUpdateOrderArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .createDmlStatement(statement("update-pokemon-order-arg"))
                                    .addParam(updatedPokemon.getName())
                                    .addParam(updatedPokemon.getId())
                                    .execute()
                    ));
    }

    // Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
    private void testNamedDmlWithUpdateOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testNamedDmlWithUpdateOrderArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .namedDml("update-pokemon-order-arg",
                                              updatedPokemon.getName(),
                                              updatedPokemon.getId())
                    ));
    }

    // Verify {@code dml(String)} API method with update with ordered parameters passed directly
    private void testDmlWithUpdateOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlWithUpdateOrderArgs",
                    (updatedPokemon) -> dbClient().execute(
                            exec -> exec
                                    .dml(statement("update-pokemon-order-arg"),
                                         updatedPokemon.getName(),
                                         updatedPokemon.getId())
                    ));
    }

}
