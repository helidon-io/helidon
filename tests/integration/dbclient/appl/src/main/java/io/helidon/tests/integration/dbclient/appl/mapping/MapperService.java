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
package io.helidon.tests.integration.dbclient.appl.mapping;

import java.lang.System.Logger.Level;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.DbRow;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.dbclient.appl.model.Type;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import static io.helidon.tests.integration.dbclient.appl.model.Type.TYPES;
import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to verify mapping interface.
 */
public class MapperService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(MapperService.class.getName());

    private interface TestDMLFunction extends Function<Pokemon, Single<Long>> {}

    /**
     * Creates an instance of web resource to verify mapping interface.
     *
     * @param dbClient DbClient instance
     * @param statements statements from configuration file
     */
    public MapperService(DbClient dbClient, Map<String, String> statements) {
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
            ServerRequest request,
            ServerResponse response,
            String testName,
            String pokemonName,
            List<Type> pokemonTypes,
            TestDMLFunction test) {
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
            LOGGER.log(Level.WARNING, String.format("Error in Mapper.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    private void executeUpdateTest(
            ServerRequest request,
            ServerResponse response,
            String testName,
            TestDMLFunction test) {
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
            LOGGER.log(Level.WARNING, String.format("Error in Mapper.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    private void executeDeleteTest(
            ServerRequest request,
            ServerResponse response,
            String testName,
            TestDMLFunction test) {
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
            LOGGER.log(Level.WARNING, String.format("Error in Mapper.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify insertion of PoJo instance using indexed mapping.
    private void testInsertWithOrderMapping(ServerRequest request, ServerResponse response) {
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
    private void testInsertWithNamedMapping(ServerRequest request, ServerResponse response) {
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
    private void testUpdateWithOrderMapping(ServerRequest request, ServerResponse response) {
        executeUpdateTest(request, response, "testUpdateWithOrderMapping",
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedUpdate("update-pokemon-order-arg")
                                .indexedParam(pokemon)
                                .execute()
                ));
    }

    // Verify update of PoJo instance using named mapping.
    private void testUpdateWithNamedMapping(ServerRequest request, ServerResponse response) {
        executeUpdateTest(request, response, "testUpdateWithNamedMapping",
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedUpdate("update-pokemon-named-arg")
                                .namedParam(pokemon)
                                .execute()
                ));
    }

    // Verify delete of PoJo instance using indexed mapping.
    private void testDeleteWithOrderMapping(ServerRequest request, ServerResponse response) {
        executeDeleteTest(request, response, "testDeleteWithOrderMapping",
                (pokemon) -> dbClient().execute(
                        exec -> exec
                                .createNamedDelete("delete-pokemon-full-order-arg")
                                .indexedParam(pokemon)
                                .execute()
                ));
    }

    // Verify delete of PoJo instance using named mapping.
    private void testDeleteWithNamedMapping(ServerRequest request, ServerResponse response) {
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
    private void testQueryWithMapping(ServerRequest request, ServerResponse response) {
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
            LOGGER.log(Level.WARNING, "Error in Mapper.testQueryWithMapping on server", ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify get of PoJo instance as a result using mapping.
    private void testGetWithMapping(ServerRequest request, ServerResponse response) {
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
            LOGGER.log(Level.WARNING, "Error in Mapper.testGetWithMapping on server", ex);
            response.send(exceptionStatus(ex));
        }
    }

}
