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
package io.helidon.tests.integration.dbclient.app.statement;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.helidon.common.http.NotFoundException;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.AbstractService;
import io.helidon.tests.integration.dbclient.app.model.RangePoJo;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Service to test query statements.
 */
public class QueryStatementService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(GetStatementService.class.getName());

    /**
     * Create a new instance.
     *
     * @param dbClient   dbclient
     * @param statements statements
     */
    public QueryStatementService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{testName}", this::executeTest);
    }

    private void executeTest(ServerRequest request, ServerResponse response) {
        String testName = pathParam(request, "testName");
        int fromId = Integer.parseInt(queryParam(request, QUERY_FROM_ID_PARAM));
        int toId = Integer.parseInt(queryParam(request, QUERY_TO_ID_PARAM));
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on server", getClass().getSimpleName(), testName));
        Stream<DbRow> result = switch (testName) {
            case "testQueryArrayParams" -> testQueryArrayParams(fromId, toId);
            case "testQueryListParams" -> testQueryListParams(fromId, toId);
            case "testQueryMapParams" -> testQueryMapParams(fromId, toId);
            case "testQueryOrderParam" -> testQueryOrderParam(fromId, toId);
            case "testQueryNamedParam" -> testQueryNamedParam(fromId, toId);
            case "testQueryMappedNamedParam" -> testQueryMappedNamedParam(fromId, toId);
            case "testQueryMappedOrderParam" -> testQueryMappedOrderParam(fromId, toId);
            default -> throw new NotFoundException("test not found: " + testName);
        };
        JsonArray jsonArray = result
                .map(row -> row.as(JsonObject.class))
                .collect(Json::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::add)
                .build();
        response.send(okStatus(jsonArray));
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return rows
     */
    private Stream<DbRow> testQueryArrayParams(int fromId, int toId) {
        return dbClient().execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .params(fromId, toId)
                .execute();
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return rows
     */
    private Stream<DbRow> testQueryListParams(int fromId, int toId) {
        return dbClient().execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .params(List.of(fromId, toId))
                .execute();
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return rows
     */
    private Stream<DbRow> testQueryMapParams(int fromId, int toId) {
        return dbClient().execute()
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .params(Map.of("idmin", fromId, "idmax", toId))
                .execute();
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return rows
     */
    private Stream<DbRow> testQueryOrderParam(int fromId, int toId) {
        return dbClient().execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .addParam(fromId)
                .addParam(toId)
                .execute();
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return rows
     */
    private Stream<DbRow> testQueryNamedParam(int fromId, int toId) {
        return dbClient().execute()
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .addParam("idmin", fromId)
                .addParam("idmax", toId)
                .execute();
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return rows
     */
    private Stream<DbRow> testQueryMappedNamedParam(int fromId, int toId) {
        RangePoJo range = new RangePoJo(fromId, toId);
        return dbClient().execute().createNamedQuery("select-pokemons-idrng-named-arg")
                .namedParam(range)
                .execute();
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     *
     * @param fromId parameter
     * @param toId   parameter
     * @return rows
     */
    private Stream<DbRow> testQueryMappedOrderParam(int fromId, int toId) {
        RangePoJo range = new RangePoJo(fromId, toId);
        return dbClient().execute().createNamedQuery("select-pokemons-idrng-order-arg")
                .indexedParam(range)
                .execute();
    }
}
