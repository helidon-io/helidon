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
package io.helidon.tests.integration.dbclient.app.tests;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.http.NotFoundException;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.common.model.RangePoJo;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;

import jakarta.json.JsonObject;

import static io.helidon.tests.integration.harness.AppResponse.okStatus;

/**
 * Service to test get statements.
 */
public class StatementGetService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(StatementGetService.class.getName());

    /**
     * Create a new instance
     *
     * @param dbClient   dbclient
     * @param statements statements
     */
    public StatementGetService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{testName}", this::executeTest);
    }

    private void executeTest(ServerRequest request, ServerResponse response) {
        String testName = pathParam(request, "testName");
        int fromId = Integer.parseInt(queryParam(request, QueryParams.FROM_ID));
        int toId = Integer.parseInt(queryParam(request, QueryParams.TO_ID));
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on server", getClass().getSimpleName(), testName));
        Optional<DbRow> result = switch (testName) {
            case "testGetArrayParams" -> testGetArrayParams(fromId, toId);
            case "testGetListParams" -> testGetListParams(fromId, toId);
            case "testGetMapParams" -> testGetMapParams(fromId, toId);
            case "testGetOrderParam" -> testGetOrderParam(fromId, toId);
            case "testGetNamedParam" -> testGetNamedParam(fromId, toId);
            case "testGetMappedNamedParam" -> testGetMappedNamedParam(fromId, toId);
            case "testGetMappedOrderParam" -> testGetMappedOrderParam(fromId, toId);
            default -> throw new NotFoundException("test not found: " + testName);
        };
        JsonObject jsonObject = result
                .map(row -> row.as(JsonObject.class))
                .orElse(JsonObject.EMPTY_JSON_OBJECT);
        response.send(okStatus(jsonObject));
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return row
     */
    private Optional<DbRow> testGetArrayParams(int fromId, int toId) {
        return dbClient().execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .params(fromId, toId)
                .execute();
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return row
     */
    private Optional<DbRow> testGetListParams(int fromId, int toId) {
        return dbClient().execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .params(List.of(fromId, toId))
                .execute();
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return row
     */
    private Optional<DbRow> testGetMapParams(int fromId, int toId) {
        return dbClient().execute()
                .createNamedGet("select-pokemons-idrng-named-arg")
                .params(Map.of("idmin", fromId, "idmax", toId))
                .execute();
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return row
     */
    private Optional<DbRow> testGetOrderParam(int fromId, int toId) {
        return dbClient().execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .addParam(fromId)
                .addParam(toId)
                .execute();
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return row
     */
    private Optional<DbRow> testGetNamedParam(int fromId, int toId) {
        return dbClient().execute()
                .createNamedGet("select-pokemons-idrng-named-arg")
                .addParam("idmin", fromId)
                .addParam("idmax", toId)
                .execute();
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return row
     */
    private Optional<DbRow> testGetMappedNamedParam(int fromId, int toId) {
        RangePoJo range = new RangePoJo(fromId, toId);
        return dbClient().execute()
                .createNamedGet("select-pokemons-idrng-named-arg")
                .namedParam(range)
                .execute();
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return row
     */
    private Optional<DbRow> testGetMappedOrderParam(int fromId, int toId) {
        RangePoJo range = new RangePoJo(fromId, toId);
        return dbClient().execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .indexedParam(range)
                .execute();
    }
}
