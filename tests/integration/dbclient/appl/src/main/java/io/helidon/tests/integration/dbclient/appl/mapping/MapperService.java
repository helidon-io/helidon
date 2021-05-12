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
package io.helidon.tests.integration.dbclient.appl.mapping;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.model.Type;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import static io.helidon.tests.integration.dbclient.appl.model.Type.TYPES;
import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to verify mapping interface.
 */
public class MapperService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(MapperService.class.getName());

    private interface TestDMLFunction extends Function<Pokemon, Single<Long>> {}

    /**
     * Creates an instance of web resource to verify mapping interface.
     *
     * @param dbClient DbClient instance
     * @param statements statements from configuration file
     */
    public MapperService(final DbClient dbClient, final Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testInsertWithOrderMapping", this::testInsertWithOrderMapping)
                .get("/testInsertWithNamedMapping", this::testInsertWithNamedMapping)
                .get("/testUpdateWithOrderMapping", this::testUpdateWithOrderMapping)
                .get("/testUpdateWithNamedMapping", this::testUpdateWithNamedMapping)
                .get("/testDeleteWithOrderMapping", this::testDeleteWithOrderMapping)
                .get("/testDeleteWithNamedMapping", this::testDeleteWithNamedMapping)
                .get("/testQueryWithMapping", this::testQueryWithMapping)
                .get("/testGetWithMapping", this::testGetWithMapping);
    }

    private void executeInsertTest(
            final ServerRequest request,
            final ServerResponse response,
            final String testName,
            final String pokemonName,
            final List<Type> pokemonTypes,
            final TestDMLFunction test) {
        LOGGER.fine(() -> String.format("Running Mapper.%s on server", testName));
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
            LOGGER.fine(() -> String.format("Error in Mapper.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
    }

    private void executeUpdateTest(
            final ServerRequest request,
            final ServerResponse response,
            final String testName,
            final TestDMLFunction test) {
        LOGGER.fine(() -> String.format("Running Mapper.%s on server", testName));
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
            LOGGER.fine(() -> String.format("Error in Mapper.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
    }

    private void executeDeleteTest(
            final ServerRequest request,
            final ServerResponse response,
            final String testName,
            final TestDMLFunction test) {
        LOGGER.fine(() -> String.format("Running Mapper.%s on server", testName));
        try {
            String idStr = param(request, QUERY_ID_PARAM);
            int id = Integer.parseInt(idStr);
            Pokemon pokemon = Pokemon.POKEMONS.get(id);
            test.apply(pokemon)
                    .thenAccept(
                            result -> response.send(
                                    AppResponse.okStatus(Json.createValue(result))))
                    .exceptionally(t -> {
                        response.send(exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.fine(() -> String.format("Error in Mapper.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
    }

    // Verify insertion of PoJo instance using indexed mapping.
    private void testInsertWithOrderMapping(final ServerRequest request, final ServerResponse response) {
        executeInsertTest(
                request,
                response,
                "testInsertWithOrderMapping",
                "Articuno",
                Pokemon.typesList(TYPES.get(3), TYPES.get(15)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedInsert("insert-pokemon-order-arg-rev")
                                .indexedParam(pokemon)
                                .execute()
                ));
    }

    // Verify insertion of PoJo instance using named mapping.
    private void testInsertWithNamedMapping(final ServerRequest request, final ServerResponse response) {
        executeInsertTest(
                request,
                response,
                "testInsertWithNamedMapping",
                "Zapdos",
                Pokemon.typesList(TYPES.get(3), TYPES.get(13)),
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedInsert("insert-pokemon-named-arg")
                                .namedParam(pokemon)
                                .execute()
                ));
    }

    // Verify update of PoJo instance using indexed mapping.
    private void testUpdateWithOrderMapping(final ServerRequest request, final ServerResponse response) {
        executeUpdateTest(request, response, "testUpdateWithOrderMapping",
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedUpdate("update-pokemon-order-arg")
                                .indexedParam(pokemon)
                                .execute()
                ));
    }

    // Verify update of PoJo instance using named mapping.
    private void testUpdateWithNamedMapping(final ServerRequest request, final ServerResponse response) {
        executeUpdateTest(request, response, "testUpdateWithNamedMapping",
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedUpdate("update-pokemon-named-arg")
                                .namedParam(pokemon)
                                .execute()
                ));
    }

    // Verify delete of PoJo instance using indexed mapping.
    private void testDeleteWithOrderMapping(final ServerRequest request, final ServerResponse response) {
        executeDeleteTest(request, response, "testDeleteWithOrderMapping",
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDelete("delete-pokemon-full-order-arg")
                                .indexedParam(pokemon)
                                .execute()
                ));
    }

    // Verify delete of PoJo instance using named mapping.
    private void testDeleteWithNamedMapping(final ServerRequest request, final ServerResponse response) {
        executeDeleteTest(request, response, "testDeleteWithNamedMapping",
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDelete("delete-pokemon-full-named-arg")
                                .namedParam(pokemon)
                                .execute()
                ));
    }

    // Query and Get calls are here just once so no common executor code is needed.

    // Verify query of PoJo instance as a result using mapping.
    private void testQueryWithMapping(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running Mapper.testQueryWithMapping on server");
        try {
            String name = param(request, QUERY_NAME_PARAM);
            Multi<DbRow> rows = dbClient().execute(exec -> exec
                .createNamedQuery("select-pokemon-named-arg")
                    .addParam("name", name).execute());
            // DbRow PoJo mapping check
            final List<Pokemon> pokemons = new LinkedList<>();
            rows.forEach(dbRow -> pokemons.add(dbRow.as(Pokemon.class)))
                    .onComplete(() -> {
                        // Convert List of PoJo to Json
                        final JsonArrayBuilder jab = Json.createArrayBuilder();
                        pokemons.forEach(pokemon -> {
                            jab.add(pokemon.toJsonObject());
                        });
                        response.send(AppResponse.okStatus(jab.build()));
                    })
                    .exceptionally(t -> {
                        response.send(exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException ex) {
            LOGGER.fine(() -> "Error in Mapper.testQueryWithMapping on server");
            response.send(exceptionStatus(ex));
        }
    }

    // Verify get of PoJo instance as a result using mapping.
    private void testGetWithMapping(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running Mapper.testGetWithMapping on server");
        try {
            String name = param(request, QUERY_NAME_PARAM);
            Single<Optional<DbRow>> future = dbClient().execute(exec -> exec
                    .createNamedGet("select-pokemon-named-arg")
                    .addParam("name", name).execute());
            future.thenAccept(
                    maybeRow -> maybeRow.ifPresentOrElse(
                            row -> response.send(
                                    // Use PoJo mapping to build response
                                    AppResponse.okStatus(row.as(Pokemon.class).toJsonObject())),
                            () -> response.send(
                                    AppResponse.okStatus(JsonObject.EMPTY_JSON_OBJECT))))
                    .exceptionally(t -> {
                        response.send(exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException ex) {
            LOGGER.fine(() -> "Error in Mapper.testGetWithMapping on server");
            response.send(exceptionStatus(ex));
        }
    }

}
