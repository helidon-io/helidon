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
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.helidon.dbclient.DbClient;
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
 * Web resource to test set of basic DbClient inserts.
 */
@SuppressWarnings("SpellCheckingInspection")
public class SimpleInsertService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(SimpleUpdateService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Pokemon, Long> {
    }

    /**
     * Creates an instance of web resource to test set of basic DbClient inserts.
     *
     * @param dbClient   DbClient instance
     * @param statements statements from configuration file
     */
    public SimpleInsertService(DbClient dbClient, Map<String, String> statements) {
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
        try {
            String idStr = param(request, QUERY_ID_PARAM);
            int id = Integer.parseInt(idStr);
            Pokemon pokemon = new Pokemon(id, pokemonName, pokemonTypes);
            test.apply(pokemon);
            response.send(okStatus(pokemon.toJsonObject()));
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.log(Level.WARNING, String.format("Error in SimpleInsertService.%s on server", testName), ex);
            response.send(AppResponse.exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedInsert(String, String)} API method with named parameters.
    private void testCreateNamedInsertStrStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedInsertStrStrNamedArgs",
                "Bulbasaur",
                Pokemon.typesList(TYPES.get(4), TYPES.get(12)),
                (pokemon) -> dbClient().execute()
                        .createNamedInsert("insert-bulbasaur", statement("insert-pokemon-named-arg"))
                        .addParam("id", pokemon.getId())
                        .addParam("name", pokemon.getName())
                        .execute());
    }

    // Verify {@code createNamedInsert(String)} API method with named parameters.
    private void testCreateNamedInsertStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedInsertStrNamedArgs",
                "Ivysaur",
                Pokemon.typesList(TYPES.get(4), TYPES.get(12)),
                (pokemon) -> dbClient().execute()
                        .createNamedInsert("insert-pokemon-named-arg")
                        .addParam("id", pokemon.getId())
                        .addParam("name", pokemon.getName())
                        .execute());
    }

    // Verify {@code createNamedInsert(String)} API method with ordered parameters.
    private void testCreateNamedInsertStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedInsertStrOrderArgs",
                "Venusaur",
                Pokemon.typesList(TYPES.get(4), TYPES.get(12)),
                (pokemon) -> dbClient().execute()
                        .createNamedInsert("insert-pokemon-order-arg")
                        .addParam(pokemon.getId())
                        .addParam(pokemon.getName())
                        .execute());
    }

    // Verify {@code createInsert(String)} API method with named parameters.
    private void testCreateInsertNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateInsertNamedArgs",
                "Magby",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> dbClient().execute()
                        .createInsert(statement("insert-pokemon-named-arg"))
                        .addParam("id", pokemon.getId())
                        .addParam("name", pokemon.getName())
                        .execute());
    }

    // Verify {@code createInsert(String)} API method with ordered parameters.
    private void testCreateInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateInsertOrderArgs",
                "Magmar",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> dbClient().execute()
                        .createInsert(statement("insert-pokemon-order-arg"))
                        .addParam(pokemon.getId())
                        .addParam(pokemon.getName())
                        .execute());
    }

    // Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
    private void testNamedInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedInsertOrderArgs",
                "Rattata",
                Pokemon.typesList(TYPES.get(1)),
                (pokemon) -> dbClient().execute()
                        .namedInsert(
                                "insert-pokemon-order-arg",
                                pokemon.getId(),
                                pokemon.getName()));
    }

    // Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
    private void testInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testInsertOrderArgs",
                "Raticate",
                Pokemon.typesList(TYPES.get(1)),
                (pokemon) -> dbClient().execute()
                        .insert(statement("insert-pokemon-order-arg"),
                                pokemon.getId(),
                                pokemon.getName()));
    }

    // DML insert

    // Verify {@code createNamedDmlStatement(String, String)} API method with insert with named parameters.
    private void testCreateNamedDmlWithInsertStrStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedDmlWithInsertStrStrNamedArgs",
                "Torchic",
                Pokemon.typesList(TYPES.get(10)),
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("insert-torchic", statement("insert-pokemon-named-arg"))
                        .addParam("id", pokemon.getId())
                        .addParam("name", pokemon.getName())
                        .execute());
    }

    // Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
    private void testCreateNamedDmlWithInsertStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedDmlWithInsertStrNamedArgs",
                "Combusken",
                Pokemon.typesList(TYPES.get(2), TYPES.get(10)),
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("insert-pokemon-named-arg")
                        .addParam("id", pokemon.getId())
                        .addParam("name", pokemon.getName())
                        .execute());
    }

    // Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
    private void testCreateNamedDmlWithInsertStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateNamedDmlWithInsertStrOrderArgs",
                "Treecko",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> dbClient().execute()
                        .createNamedDmlStatement("insert-pokemon-order-arg")
                        .addParam(pokemon.getId())
                        .addParam(pokemon.getName())
                        .execute());
    }

    // Verify {@code createDmlStatement(String)} API method with insert with named parameters.
    private void testCreateDmlWithInsertNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateDmlWithInsertNamedArgs",
                "Grovyle",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> dbClient().execute()
                        .createDmlStatement(statement("insert-pokemon-named-arg"))
                        .addParam("id", pokemon.getId())
                        .addParam("name", pokemon.getName())
                        .execute());
    }

    // Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
    private void testCreateDmlWithInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testCreateDmlWithInsertOrderArgs",
                "Sceptile",
                Pokemon.typesList(TYPES.get(12)),
                (pokemon) -> dbClient().execute()
                        .createDmlStatement(statement("insert-pokemon-order-arg"))
                        .addParam(pokemon.getId())
                        .addParam(pokemon.getName())
                        .execute());
    }

    // Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
    private void testNamedDmlWithInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testNamedDmlWithInsertOrderArgs",
                "Snover",
                Pokemon.typesList(TYPES.get(12), TYPES.get(15)),
                (pokemon) -> dbClient().execute()
                        .namedDml("insert-pokemon-order-arg",
                                pokemon.getId(),
                                pokemon.getName()));
    }

    // Verify {@code dml(String)} API method with insert with ordered parameters passed directly
    private void testDmlWithInsertOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(
                request,
                response,
                "testDmlWithInsertOrderArgs",
                "Abomasnow",
                Pokemon.typesList(TYPES.get(12), TYPES.get(15)),
                (pokemon) -> dbClient().execute()
                        .dml(statement("insert-pokemon-order-arg"),
                                pokemon.getId(),
                                pokemon.getName()));
    }

}
