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
package io.helidon.tests.integration.dbclient.appl.simple;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

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
 * Web resource to test set of basic DbClient inserts.
 */
public class SimpleInsertService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(SimpleUpdateService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Single<Long>> {}

    /**
     * Creates an instance of web resource to test set of basic DbClient inserts.
     *
     * @param dbClient DbClient instance
     * @param statements statements from configuration file
     */
    public SimpleInsertService(final DbClient dbClient, final Map<String, String> statements) {
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
    private void executeTest(
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
    }

    // Verify {@code createNamedInsert(String, String)} API method with named parameters.
    private void testCreateNamedInsertStrStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedInsertStrStrNamedArgs",
                "Bulbasaur",
                Pokemon.typesList(TYPES.get(4), TYPES.get(12)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedInsert("insert-bulbasaur", statement("insert-pokemon-named-arg"))
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createNamedInsert(String)} API method with named parameters.
    private void testCreateNamedInsertStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedInsertStrNamedArgs",
                "Ivysaur",
                Pokemon.typesList(TYPES.get(4), TYPES.get(12)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedInsert("insert-pokemon-named-arg")
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createNamedInsert(String)} API method with ordered parameters.
    private void testCreateNamedInsertStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedInsertStrOrderArgs",
                "Venusaur",
                Pokemon.typesList(TYPES.get(4), TYPES.get(12)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedInsert("insert-pokemon-order-arg")
                                .addParam(pokemon.getId())
                                .addParam(pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createInsert(String)} API method with named parameters.
    private void testCreateInsertNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateInsertNamedArgs",
                "Magby",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createInsert(statement("insert-pokemon-named-arg"))
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createInsert(String)} API method with ordered parameters.
    private void testCreateInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateInsertOrderArgs",
                "Magmar",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createInsert(statement("insert-pokemon-order-arg"))
                                .addParam(pokemon.getId())
                                .addParam(pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
    private void testNamedInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Rattata",
                Pokemon.typesList(TYPES.get(1)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .namedInsert(
                                        "insert-pokemon-order-arg",
                                        pokemon.getId(),
                                        pokemon.getName())
                ));
    }

    // Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
    private void testInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testInsertOrderArgs",
                "Raticate",
                Pokemon.typesList(TYPES.get(1)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .insert(statement("insert-pokemon-order-arg"),
                                        pokemon.getId(),
                                        pokemon.getName())
                ));
    }

    // DML insert

    // Verify {@code createNamedDmlStatement(String, String)} API method with insert with named parameters.
    private void testCreateNamedDmlWithInsertStrStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Torchic",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("insert-torchic", statement("insert-pokemon-named-arg"))
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
    private void testCreateNamedDmlWithInsertStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Combusken",
                Pokemon.typesList(TYPES.get(2), TYPES.get(10)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("insert-pokemon-named-arg")
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
    private void testCreateNamedDmlWithInsertStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Treecko",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("insert-pokemon-order-arg")
                                .addParam(pokemon.getId())
                                .addParam(pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with insert with named parameters.
    private void testCreateDmlWithInsertNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Grovyle",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createDmlStatement(statement("insert-pokemon-named-arg"))
                                .addParam("id", pokemon.getId())
                                .addParam("name", pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
    private void testCreateDmlWithInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Sceptile",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createDmlStatement(statement("insert-pokemon-order-arg"))
                                .addParam(pokemon.getId())
                                .addParam(pokemon.getName())
                                .execute()
                ));
    }

    // Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
    private void testNamedDmlWithInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Snover",
                Pokemon.typesList(TYPES.get(12), TYPES.get(15)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .namedDml("insert-pokemon-order-arg",
                                        pokemon.getId(),
                                        pokemon.getName())
                ));
    }

    // Verify {@code dml(String)} API method with insert with ordered parameters passed directly
    private void testDmlWithInsertOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Abomasnow",
                Pokemon.typesList(TYPES.get(12), TYPES.get(15)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .dml(statement("insert-pokemon-order-arg"),
                                        pokemon.getId(),
                                        pokemon.getName())
                ));
    }

}
