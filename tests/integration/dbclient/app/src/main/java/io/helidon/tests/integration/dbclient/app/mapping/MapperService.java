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
package io.helidon.tests.integration.dbclient.app.mapping;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.http.NotFoundException;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.AbstractService;
import io.helidon.tests.integration.dbclient.app.model.Pokemon;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import static io.helidon.tests.integration.dbclient.app.model.Type.TYPES;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Service to test mapping interface.
 */
public class MapperService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(MapperService.class.getName());

    /**
     * Create a new instance.
     *
     * @param dbClient   dbclient
     * @param statements statements
     */
    public MapperService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{testName}", this::executeTest);
    }

    private void executeTest(ServerRequest req, ServerResponse res) {
        String testName = pathParam(req, "testName");
        LOGGER.log(System.Logger.Level.DEBUG, () -> String.format("Running %s.%s on server", getClass().getSimpleName(), testName));
        switch (testName) {
            case "testInsertWithOrderMapping" -> execInsert(req, res, this::testInsertWithOrderMapping);
            case "testInsertWithNamedMapping" -> execInsert(req, res, this::testInsertWithNamedMapping);
            case "testUpdateWithOrderMapping" -> execUpdate(req, res, this::testUpdateWithOrderMapping);
            case "testUpdateWithNamedMapping" -> execUpdate(req, res, this::testUpdateWithNamedMapping);
            case "testDeleteWithOrderMapping" -> execDelete(req, res, this::testDeleteWithOrderMapping);
            case "testDeleteWithNamedMapping" -> execDelete(req, res, this::testDeleteWithNamedMapping);
            case "testQueryWithMapping" -> execQueryMapping(req, res, this::testQueryWithMapping);
            case "testGetWithMapping" -> execGetMapping(req, res, this::testGetWithMapping);
            default -> throw new NotFoundException("test not found: " + testName);
        }
    }

    private void execInsert(ServerRequest req, ServerResponse res, Function<Integer, Pokemon> test) {
        int id = Integer.parseInt(queryParam(req, QUERY_ID_PARAM));
        Pokemon pokemon = test.apply(id);
        res.send(okStatus(pokemon.toJsonObject()));
    }

    private void execUpdate(ServerRequest req, ServerResponse res, Function<Pokemon, Long> test) {
        String name = queryParam(req, QUERY_NAME_PARAM);
        int id = Integer.parseInt(queryParam(req, QUERY_ID_PARAM));
        Pokemon srcPokemon = Pokemon.POKEMONS.get(id);
        Pokemon updatedPokemon = new Pokemon(id, name, srcPokemon.getTypesArray());
        long count = test.apply(updatedPokemon);
        res.send(okStatus(Json.createValue(count)));
    }

    private void execDelete(ServerRequest req, ServerResponse res, Function<Pokemon, Long> test) {
        int id = Integer.parseInt(queryParam(req, QUERY_ID_PARAM));
        Pokemon pokemon = Pokemon.POKEMONS.get(id);
        long count = test.apply(pokemon);
        res.send(okStatus(Json.createValue(count)));
    }

    private void execGetMapping(ServerRequest req, ServerResponse res, Function<String, Optional<DbRow>> test) {
        String name = queryParam(req, QUERY_NAME_PARAM);
        JsonObject jsonObject = test.apply(name)
                .map(row -> row.as(Pokemon.class).toJsonObject())
                .orElse(JsonObject.EMPTY_JSON_OBJECT);
        res.send(okStatus(jsonObject));
    }

    private void execQueryMapping(ServerRequest req, ServerResponse res, Function<String, Stream<DbRow>> test) {
        String name = queryParam(req, QUERY_NAME_PARAM);
        JsonArray jsonArray = test.apply(name)
                .map(row -> row.as(Pokemon.class).toJsonObject())
                .collect(Json::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::add)
                .build();
        res.send(okStatus(jsonArray));
    }

    /**
     * Verify insertion of PoJo instance using indexed mapping.
     *
     * @param id parameter
     * @return pokemon
     */
    private Pokemon testInsertWithOrderMapping(int id) {
        Pokemon pokemon = new Pokemon(id, "Articuno", Pokemon.typesList(TYPES.get(3), TYPES.get(15)));
        dbClient().execute()
                .createNamedInsert("insert-pokemon-order-arg-rev")
                .indexedParam(pokemon)
                .execute();
        return pokemon;
    }

    /**
     * Verify insertion of PoJo instance using named mapping.
     *
     * @param id parameter
     * @return pokemon
     */
    private Pokemon testInsertWithNamedMapping(int id) {
        Pokemon pokemon = new Pokemon(id, "Zapdos", Pokemon.typesList(TYPES.get(3), TYPES.get(13)));
        dbClient().execute()
                .createNamedInsert("insert-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
        return pokemon;
    }

    /**
     * Verify update of PoJo instance using indexed mapping.
     *
     * @param pokemon pokemon
     * @return count
     */
    private long testUpdateWithOrderMapping(Pokemon pokemon) {
        return dbClient().execute()
                .createNamedUpdate("update-pokemon-order-arg")
                .indexedParam(pokemon)
                .execute();
    }

    /**
     * Verify update of PoJo instance using named mapping.
     *
     * @param pokemon pokemon
     * @return count
     */
    private long testUpdateWithNamedMapping(Pokemon pokemon) {
        return dbClient().execute()
                .createNamedUpdate("update-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
    }

    /**
     * Verify delete of PoJo instance using indexed mapping.
     *
     * @param pokemon pokemon
     * @return count
     */
    private long testDeleteWithOrderMapping(Pokemon pokemon) {
        return dbClient().execute()
                .createNamedDelete("delete-pokemon-full-order-arg")
                .indexedParam(pokemon)
                .execute();
    }

    /**
     * Verify delete of PoJo instance using named mapping.
     *
     * @param pokemon pokemon
     * @return count
     */
    private long testDeleteWithNamedMapping(Pokemon pokemon) {
        return dbClient().execute()
                .createNamedDelete("delete-pokemon-full-named-arg")
                .namedParam(pokemon)
                .execute();
    }

    /**
     * Verify query of PoJo instance as a result using mapping.
     *
     * @param name parameter
     * @return rows
     */
    private Stream<DbRow> testQueryWithMapping(String name) {
        return dbClient().execute()
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", name)
                .execute();
    }

    /**
     * Verify get of PoJo instance as a result using mapping.
     *
     * @param name parameter
     * @return row
     */
    private Optional<DbRow> testGetWithMapping(String name) {
        return dbClient().execute().createNamedGet("select-pokemon-named-arg")
                .addParam("name", name)
                .execute();
    }
}
