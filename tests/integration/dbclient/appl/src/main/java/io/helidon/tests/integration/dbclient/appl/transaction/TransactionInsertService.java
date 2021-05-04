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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.model.Type;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import static io.helidon.tests.integration.dbclient.appl.model.Type.TYPES;

/**
 * Web resource to test set of basic DbClient inserts in transaction.
 */
public class TransactionInsertService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(TransactionInsertService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Single<Long>> {}

    public TransactionInsertService(final DbClient dbClient, final Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testCreateNamedInsertStrStrNamedArgs", this::testCreateNamedInsertStrStrNamedArgs)
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
    private JsonObject executeTest(
            final ServerRequest request,
            final ServerResponse response,
            final String testName,
            final String pokemonName,
            final List<Type> pokemonTypes,
            final TestFunction test) {
        LOGGER.fine(() -> String.format("Running SimpleInsertService.%s on server", testName));
        try {
            String idStr = param(request, QUERY_ID_PARAM);
            int id = Integer.parseInt(idStr);
            Pokemon pokemon = new Pokemon(id, pokemonName, pokemonTypes);
            test.apply(pokemon)
                    .thenAccept(
                            result -> response.send(
                                    AppResponse.okStatus(pokemon.toJsonObject())))
                    .exceptionally(t -> {
                        response.send(AppResponse.exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.fine(() -> String.format("Error in SimpleInsertService.%s on server", testName));
            response.send(AppResponse.exceptionStatus(ex));
        }
        return null;
    }

    // Verify {@code createNamedInsert(String, String)} API method with named parameters.
    private JsonObject testCreateNamedInsertStrStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testCreateNamedInsertStrStrNamedArgs",
                "Bounsweet",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedInsert("insert-bulbasaur", statement("insert-pokemon-named-arg"))
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createNamedInsert(String)} API method with named parameters.
    private JsonObject testCreateNamedInsertStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testCreateNamedInsertStrNamedArgs",
                "Steenee",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedInsert("insert-pokemon-named-arg")
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createNamedInsert(String)} API method with ordered parameters.
    private JsonObject testCreateNamedInsertStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testCreateNamedInsertStrOrderArgs",
                "Tsareena",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedInsert("insert-pokemon-order-arg")
                                .addParam(pokemon.getId())
                                .addParam(pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createInsert(String)} API method with named parameters.
    private JsonObject testCreateInsertNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testCreateInsertNamedArgs",
                "Fennekin",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createInsert(statement("insert-pokemon-named-arg"))
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createInsert(String)} API method with ordered parameters.
    private JsonObject testCreateInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testCreateInsertOrderArgs",
                "Braixen",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createInsert(statement("insert-pokemon-order-arg"))
                                .addParam(pokemon.getId())
                                .addParam(pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
    private JsonObject testNamedInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Delphox",
                Pokemon.typesList(TYPES.get(10), TYPES.get(14)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .namedInsert(
                                        "insert-pokemon-order-arg",
                                        pokemon.getId(),
                                        pokemon.getName())
                ));
    }

    // Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
    private JsonObject testInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testInsertOrderArgs",
                "Bouffalant",
                Pokemon.typesList(TYPES.get(1)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .insert(statement("insert-pokemon-order-arg"),
                                        pokemon.getId(),
                                        pokemon.getName())
                ));
    }

    // DML insert

    // Verify {@code createNamedDmlStatement(String, String)} API method with insert with named parameters.
    private JsonObject testCreateNamedDmlWithInsertStrStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Roggenrola",
                Pokemon.typesList(TYPES.get(6)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedDmlStatement("insert-torchic", statement("insert-pokemon-named-arg"))
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
    private JsonObject testCreateNamedDmlWithInsertStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Boldore",
                Pokemon.typesList(TYPES.get(6)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedDmlStatement("insert-pokemon-named-arg")
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
    private JsonObject testCreateNamedDmlWithInsertStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Gigalith",
                Pokemon.typesList(TYPES.get(6)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedDmlStatement("insert-pokemon-order-arg")
                                .addParam(pokemon.getId())
                                .addParam(pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with insert with named parameters.
    private JsonObject testCreateDmlWithInsertNamedArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Wurmple",
                Pokemon.typesList(TYPES.get(7)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createDmlStatement(statement("insert-pokemon-named-arg"))
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
    private JsonObject testCreateDmlWithInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Silcoon",
                Pokemon.typesList(TYPES.get(7)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .createDmlStatement(statement("insert-pokemon-order-arg"))
                                .addParam(pokemon.getId())
                                .addParam(pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
    private JsonObject testNamedDmlWithInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Cascoon",
                Pokemon.typesList(TYPES.get(7)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .namedDml("insert-pokemon-order-arg",
                                        pokemon.getId(),
                                        pokemon.getName())
                ));
    }

    // Verify {@code dml(String)} API method with insert with ordered parameters passed directly
    private JsonObject testDmlWithInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        return executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Beautifly",
                Pokemon.typesList(TYPES.get(3), TYPES.get(7)),
                (pokemon) -> dbClient().inTransaction(
                        exec -> exec
                                .dml(statement("insert-pokemon-order-arg"),
                                        pokemon.getId(),
                                        pokemon.getName())
                ));
    }

}
