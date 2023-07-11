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
package io.helidon.tests.integration.dbclient.appl.transaction;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbTransaction;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import jakarta.json.Json;

import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.function.Function;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource to test set of basic DbCliebnt updates in transaction.
 */
@SuppressWarnings("SpellCheckingInspection")
public class TransactionUpdateService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(TransactionUpdateService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Long> {
    }

    public TransactionUpdateService(final DbClient dbClient, final Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/testCreateNamedUpdateStrStrNamedArgs", this::testCreateNamedUpdateStrStrNamedArgs)
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
    private void executeTest(final ServerRequest request, final ServerResponse response, final String testName, final TestFunction test) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running SimpleUpdateService.%s on server", testName));
        try {
            String name = param(request, QUERY_NAME_PARAM);
            String idStr = param(request, QUERY_ID_PARAM);
            int id = Integer.parseInt(idStr);
            Pokemon srcPokemon = Pokemon.POKEMONS.get(id);
            Pokemon updatedPokemon = new Pokemon(id, name, srcPokemon.getTypesArray());
            long count = test.apply(updatedPokemon);
            response.send(okStatus(Json.createValue(count)));
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Error in SimpleUpdateService.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedUpdate(String, String)} API method with ordered parameters.
    private void testCreateNamedUpdateStrStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedUpdate("update-spearow", statement("update-pokemon-named-arg"))
                            .addParam("name", pokemon.getName())
                            .addParam("id", pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedUpdate(String)} API method with named parameters.
    private void testCreateNamedUpdateStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrNamedArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedUpdate("update-pokemon-named-arg")
                            .addParam("name", pokemon.getName())
                            .addParam("id", pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }


    // Verify {@code createNamedUpdate(String)} API method with ordered parameters.
    private void testCreateNamedUpdateStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedUpdate("update-pokemon-order-arg")
                            .addParam(pokemon.getName())
                            .addParam(pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createUpdate(String)} API method with named parameters.
    private void testCreateUpdateNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateUpdateNamedArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createUpdate(statement("update-pokemon-named-arg"))
                            .addParam("name", pokemon.getName())
                            .addParam("id", pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createUpdate(String)} API method with ordered parameters.
    private void testCreateUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateUpdateOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createUpdate(statement("update-pokemon-order-arg"))
                            .addParam(pokemon.getName())
                            .addParam(pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code namedUpdate(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
    private void testNamedUpdateNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testNamedUpdateNamedArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .namedUpdate("update-pokemon-order-arg",
                                    pokemon.getName(),
                                    pokemon.getId());
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code update(String)} API method with ordered parameters passed directly to the {@code query} method.
    private void testUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testUpdateOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .update(statement("update-pokemon-order-arg"),
                                    pokemon.getName(),
                                    pokemon.getId());
                    tx.commit();
                    return count;
                });
    }

    // DML update

    // Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
    private void testCreateNamedDmlWithUpdateStrStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testUpdateOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedDmlStatement("update-piplup", statement("update-pokemon-named-arg"))
                            .addParam("name", pokemon.getName())
                            .addParam("id", pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
    private void testCreateNamedDmlWithUpdateStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testUpdateOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedDmlStatement("update-pokemon-named-arg")
                            .addParam("name", pokemon.getName())
                            .addParam("id", pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
    private void testCreateNamedDmlWithUpdateStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testUpdateOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedDmlStatement("update-pokemon-order-arg")
                            .addParam(pokemon.getName())
                            .addParam(pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createDmlStatement(String)} API method with update with named parameters.
    private void testCreateDmlWithUpdateNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testUpdateOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createDmlStatement(statement("update-pokemon-named-arg"))
                            .addParam("name", pokemon.getName())
                            .addParam("id", pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
    private void testCreateDmlWithUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testUpdateOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createDmlStatement(statement("update-pokemon-order-arg"))
                            .addParam(pokemon.getName())
                            .addParam(pokemon.getId())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
    private void testNamedDmlWithUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testUpdateOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .namedDml("update-pokemon-order-arg",
                                    pokemon.getName(),
                                    pokemon.getId());
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code dml(String)} API method with update with ordered parameters passed directly
    private void testDmlWithUpdateOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testUpdateOrderArgs",
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .dml(statement("update-pokemon-order-arg"),
                                    pokemon.getName(),
                                    pokemon.getId());
                    tx.commit();
                    return count;
                });
    }
}
