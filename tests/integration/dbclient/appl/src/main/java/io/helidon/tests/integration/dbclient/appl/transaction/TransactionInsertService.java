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

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbTransaction;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.model.Type;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import static io.helidon.tests.integration.dbclient.appl.model.Type.TYPES;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource to test set of basic DbClient inserts in transaction.
 */
@SuppressWarnings("SpellCheckingInspection")
public class TransactionInsertService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(TransactionInsertService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Long> {
    }

    public TransactionInsertService(final DbClient dbClient, final Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/testCreateNamedInsertStrStrNamedArgs", this::testCreateNamedInsertStrStrNamedArgs)
                .get("/testCreateNamedInsertStrNamedArgs", this::testCreateNamedInsertStrNamedArgs)
                .get("/testCreateNamedInsertStrOrderArgs", this::testCreateNamedInsertStrOrderArgs)
                .get("/testCreateInsertNamedArgs", this::testCreateInsertNamedArgs)
                .get("/testCreateInsertOrderArgs", this::testCreateInsertOrderArgs)
                .get("/testNamedInsertOrderArgs", this::testNamedInsertOrderArgs)
                .get("/testInsertOrderArgs", this::testInsertOrderArgs)
                .get("/testCreateNamedDmlWithInsertStrStrNamedArgs", this::testCreateNamedDmlWithInsertStrStrNamedArgs)
                .get("/testCreateNamedDmlWithInsertStrNamedArgs", this::testCreateNamedDmlWithInsertStrNamedArgs)
                .get("/testCreateNamedDmlWithInsertStrOrderArgs", this::testCreateNamedDmlWithInsertStrOrderArgs)
                .get("/testCreateDmlWithInsertNamedArgs", this::testCreateDmlWithInsertNamedArgs)
                .get("/testCreateDmlWithInsertOrderArgs", this::testCreateDmlWithInsertOrderArgs)
                .get("/testNamedDmlWithInsertOrderArgs", this::testNamedDmlWithInsertOrderArgs)
                .get("/testDmlWithInsertOrderArgs", this::testDmlWithInsertOrderArgs);
    }

    // Common test execution code
    private void executeTest(ServerRequest request,
                             ServerResponse response,
                             String testName,
                             String pokemonName,
                             List<Type> pokemonTypes,
                             TestFunction test) {

        LOGGER.log(Level.DEBUG, () -> String.format("Running SimpleInsertService.%s on server", testName));
        try {
            String idStr = param(request, QUERY_ID_PARAM);
            int id = Integer.parseInt(idStr);
            Pokemon pokemon = new Pokemon(id, pokemonName, pokemonTypes);
            test.apply(pokemon);
            response.send(okStatus(pokemon.toJsonObject()));
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Error in SimpleInsertService.%s on server", testName));
            response.send(AppResponse.exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedInsert(String, String)} API method with named parameters.
    private void testCreateNamedInsertStrStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedInsertStrStrNamedArgs",
                "Bounsweet",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedInsert("insert-bulbasaur", statement("insert-pokemon-named-arg"))
                            .addParam("id", pokemon.getId())
                            .addParam("name", pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedInsert(String)} API method with named parameters.
    private void testCreateNamedInsertStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedInsertStrNamedArgs",
                "Steenee",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedInsert("insert-pokemon-named-arg")
                            .addParam("id", pokemon.getId())
                            .addParam("name", pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedInsert(String)} API method with ordered parameters.
    private void testCreateNamedInsertStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedInsertStrOrderArgs",
                "Tsareena",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedInsert("insert-pokemon-order-arg")
                            .addParam(pokemon.getId())
                            .addParam(pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createInsert(String)} API method with named parameters.
    private void testCreateInsertNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateInsertNamedArgs",
                "Fennekin",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createInsert(statement("insert-pokemon-named-arg"))
                            .addParam("id", pokemon.getId())
                            .addParam("name", pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createInsert(String)} API method with ordered parameters.
    private void testCreateInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateInsertOrderArgs",
                "Braixen",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createInsert(statement("insert-pokemon-order-arg"))
                            .addParam(pokemon.getId())
                            .addParam(pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
    private void testNamedInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Delphox",
                Pokemon.typesList(TYPES.get(10), TYPES.get(14)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .namedInsert("insert-pokemon-order-arg",
                                    pokemon.getId(),
                                    pokemon.getName());
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
    private void testInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testInsertOrderArgs",
                "Bouffalant",
                Pokemon.typesList(TYPES.get(1)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .insert(statement("insert-pokemon-order-arg"),
                                    pokemon.getId(),
                                    pokemon.getName());
                    tx.commit();
                    return count;
                });
    }

    // DML insert

    // Verify {@code createNamedDmlStatement(String, String)} API method with insert with named parameters.
    private void testCreateNamedDmlWithInsertStrStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Roggenrola",
                Pokemon.typesList(TYPES.get(6)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedDmlStatement("insert-torchic", statement("insert-pokemon-named-arg"))
                            .addParam("id", pokemon.getId())
                            .addParam("name", pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
    private void testCreateNamedDmlWithInsertStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Boldore",
                Pokemon.typesList(TYPES.get(6)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedDmlStatement("insert-pokemon-named-arg")
                            .addParam("id", pokemon.getId())
                            .addParam("name", pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
    private void testCreateNamedDmlWithInsertStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Gigalith",
                Pokemon.typesList(TYPES.get(6)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createNamedDmlStatement("insert-pokemon-order-arg")
                            .addParam(pokemon.getId())
                            .addParam(pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createDmlStatement(String)} API method with insert with named parameters.
    private void testCreateDmlWithInsertNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Wurmple",
                Pokemon.typesList(TYPES.get(7)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createDmlStatement(statement("insert-pokemon-named-arg"))
                            .addParam("id", pokemon.getId())
                            .addParam("name", pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
    private void testCreateDmlWithInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Silcoon",
                Pokemon.typesList(TYPES.get(7)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .createDmlStatement(statement("insert-pokemon-order-arg"))
                            .addParam(pokemon.getId())
                            .addParam(pokemon.getName())
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
    private void testNamedDmlWithInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Cascoon",
                Pokemon.typesList(TYPES.get(7)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .namedDml("insert-pokemon-order-arg",
                                    pokemon.getId(),
                                    pokemon.getName());
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code dml(String)} API method with insert with ordered parameters passed directly
    private void testDmlWithInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Beautifly",
                Pokemon.typesList(TYPES.get(3), TYPES.get(7)),
                (pokemon) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx
                            .dml(statement("insert-pokemon-order-arg"),
                                    pokemon.getId(),
                                    pokemon.getName());
                    tx.commit();
                    return count;
                });
    }
}
