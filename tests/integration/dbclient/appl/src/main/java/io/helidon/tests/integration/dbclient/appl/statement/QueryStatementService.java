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
package io.helidon.tests.integration.dbclient.appl.statement;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.RangePoJo;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource to test DbStatementQuery methods.
 */
@SuppressWarnings("SpellCheckingInspection")
public class QueryStatementService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(GetStatementService.class.getName());

    private interface TestFunction extends BiFunction<Integer, Integer, Stream<DbRow>> {
    }

    public QueryStatementService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/testQueryArrayParams", this::testQueryArrayParams)
                .get("/testQueryListParams", this::testQueryListParams)
                .get("/testQueryMapParams", this::testQueryMapParams)
                .get("/testQueryOrderParam", this::testQueryOrderParam)
                .get("/testQueryNamedParam", this::testQueryNamedParam)
                .get("/testQueryMappedNamedParam", this::testQueryMappedNamedParam)
                .get("/testQueryMappedOrderParam", this::testQueryMappedOrderParam);
    }

    // Common test execution code
    private void executeTest(ServerRequest request,
                             ServerResponse response,
                             String testName,
                             TestFunction test) {
        try {
            String fromIdStr = param(request, QUERY_FROM_ID_PARAM);
            int fromId = Integer.parseInt(fromIdStr);
            String toIdStr = param(request, QUERY_TO_ID_PARAM);
            int toId = Integer.parseInt(toIdStr);
            JsonArray jsonArray = test.apply(fromId, toId)
                    .map(row -> row.as(JsonObject.class))
                    .collect(Json::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::add)
                    .build();
            response.send(okStatus(jsonArray));
        } catch (NumberFormatException | RemoteTestException ex) {
            LOGGER.log(Level.WARNING, String.format("Error in SimpleQueryService.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code params(Object... parameters)} parameters setting method.
    private void testQueryArrayParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryArrayParams",
                (fromId, toId) -> dbClient().execute()
                        .createNamedQuery("select-pokemons-idrng-order-arg")
                        .params(fromId, toId)
                        .execute());
    }

    // Verify {@code params(List<?>)} parameters setting method.
    private void testQueryListParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryListParams",
                (fromId, toId) -> dbClient().execute()
                        .createNamedQuery("select-pokemons-idrng-order-arg")
                        .params(List.of(fromId, toId))
                        .execute());
    }

    // Verify {@code params(Map<?>)} parameters setting method.
    private void testQueryMapParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryMapParams",
                (fromId, toId) -> dbClient().execute()
                        .createNamedQuery("select-pokemons-idrng-named-arg")
                        .params(Map.of("idmin", fromId, "idmax", toId))
                        .execute());
    }

    // Verify {@code addParam(Object parameter)} parameters setting method.
    private void testQueryOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryOrderParam",
                (fromId, toId) -> dbClient().execute()
                        .createNamedQuery("select-pokemons-idrng-order-arg")
                        .addParam(fromId)
                        .addParam(toId)
                        .execute());
    }

    // Verify {@code addParam(String name, Object parameter)} parameters setting method.
    private void testQueryNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryNamedParam",
                (fromId, toId) -> dbClient().execute()
                        .createNamedQuery("select-pokemons-idrng-named-arg")
                        .addParam("idmin", fromId)
                        .addParam("idmax", toId)
                        .execute());
    }

    // Verify {@code namedParam(Object parameters)} mapped parameters setting method.
    private void testQueryMappedNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryMappedNamedParam",
                (fromId, toId) -> {
                    RangePoJo range = new RangePoJo(fromId, toId);
                    return dbClient().execute().createNamedQuery("select-pokemons-idrng-named-arg")
                            .namedParam(range)
                            .execute();
                });
    }

    // Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
    private void testQueryMappedOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryMappedOrderParam",
                (fromId, toId) -> {
                    RangePoJo range = new RangePoJo(fromId, toId);
                    return dbClient().execute().createNamedQuery("select-pokemons-idrng-order-arg")
                            .indexedParam(range)
                            .execute();
                });
    }

}
