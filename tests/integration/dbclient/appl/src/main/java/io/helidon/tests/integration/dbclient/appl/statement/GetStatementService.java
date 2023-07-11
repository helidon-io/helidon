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
import java.util.Optional;
import java.util.function.BiFunction;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.RangePoJo;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.JsonObject;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource to test DbStatementGet methods.
 */
@SuppressWarnings("SpellCheckingInspection")
public class GetStatementService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(GetStatementService.class.getName());

    private interface TestFunction extends BiFunction<Integer, Integer, Optional<DbRow>> {
    }

    public GetStatementService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/testGetArrayParams", this::testGetArrayParams)
                .get("/testGetListParams", this::testGetListParams)
                .get("/testGetMapParams", this::testGetMapParams)
                .get("/testGetOrderParam", this::testGetOrderParam)
                .get("/testGetNamedParam", this::testGetNamedParam)
                .get("/testGetMappedNamedParam", this::testGetMappedNamedParam)
                .get("/testGetMappedOrderParam", this::testGetMappedOrderParam);
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
            JsonObject jsonObject = test.apply(fromId, toId)
                    .map(row -> row.as(JsonObject.class))
                    .orElse(JsonObject.EMPTY_JSON_OBJECT);
            response.send(okStatus(jsonObject));
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.log(Level.WARNING, String.format("Error in SimpleGetService.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code params(Object... parameters)} parameters setting method.
    private void testGetArrayParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetArrayParams",
                (fromId, toId) -> dbClient().execute()
                        .createNamedGet("select-pokemons-idrng-order-arg")
                        .params(fromId, toId)
                        .execute());
    }

    // Verify {@code params(List<?>)} parameters setting method.
    private void testGetListParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetListParams",
                (fromId, toId) -> dbClient().execute()
                        .createNamedGet("select-pokemons-idrng-order-arg")
                        .params(List.of(fromId, toId))
                        .execute());
    }

    // Verify {@code params(Map<?>)} parameters setting method.
    private void testGetMapParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetMapParams",
                (fromId, toId) -> dbClient().execute()
                        .createNamedGet("select-pokemons-idrng-named-arg")
                        .params(Map.of("idmin", fromId, "idmax", toId))
                        .execute());
    }

    // Verify {@code addParam(Object parameter)} parameters setting method.
    private void testGetOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetOrderParam",
                (fromId, toId) -> dbClient().execute()
                        .createNamedGet("select-pokemons-idrng-order-arg")
                        .addParam(fromId)
                        .addParam(toId)
                        .execute());
    }

    // Verify {@code addParam(String name, Object parameter)} parameters setting method.
    private void testGetNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetNamedParam",
                (fromId, toId) -> dbClient().execute()
                        .createNamedGet("select-pokemons-idrng-named-arg")
                        .addParam("idmin", fromId)
                        .addParam("idmax", toId)
                        .execute());
    }

    // Verify {@code namedParam(Object parameters)} mapped parameters setting method.
    private void testGetMappedNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetMappedNamedParam",
                (fromId, toId) -> {
                    RangePoJo range = new RangePoJo(fromId, toId);
                    return dbClient().execute()
                            .createNamedGet("select-pokemons-idrng-named-arg")
                            .namedParam(range)
                            .execute();
                });
    }

    // Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
    private void testGetMappedOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetMappedOrderParam",
                (fromId, toId) -> {
                    RangePoJo range = new RangePoJo(fromId, toId);
                    return dbClient().execute()
                            .createNamedGet("select-pokemons-idrng-order-arg")
                            .indexedParam(range)
                            .execute();
                });
    }
}
