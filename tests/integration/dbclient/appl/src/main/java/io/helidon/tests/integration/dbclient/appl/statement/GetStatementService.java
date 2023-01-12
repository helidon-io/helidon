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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import io.helidon.common.reactive.Single;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.DbRow;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.RangePoJo;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.JsonObject;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to test DbStatementGet methods.
 */
public class GetStatementService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(GetStatementService.class.getName());

    private interface TestFunction extends BiFunction<Integer, Integer, Single<Optional<DbRow>>> {
    }

    public GetStatementService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testGetArrayParams", this::testGetArrayParams)
                .get("/testGetListParams", this::testGetListParams)
                .get("/testGetMapParams", this::testGetMapParams)
                .get("/testGetOrderParam", this::testGetOrderParam)
                .get("/testGetNamedParam", this::testGetNamedParam)
                .get("/testGetMappedNamedParam", this::testGetMappedNamedParam)
                .get("/testGetMappedOrderParam", this::testGetMappedOrderParam);
    }

    // Common test execution code
    private void executeTest(
            ServerRequest request,
            ServerResponse response,
            String testName,
            TestFunction test
    ) {
        try {
            String fromIdStr = param(request, QUERY_FROM_ID_PARAM);
            int fromId = Integer.parseInt(fromIdStr);
            String toIdStr = param(request, QUERY_TO_ID_PARAM);
            int toId = Integer.parseInt(toIdStr);
            test.apply(fromId, toId)
                    .thenAccept(maybeRow -> maybeRow
                        .ifPresentOrElse(row -> response
                            .send(
                                    AppResponse.okStatus(row.as(JsonObject.class))),
                                    () -> response.send(
                                            AppResponse.okStatus(JsonObject.EMPTY_JSON_OBJECT))))
                    .exceptionally(t -> {
                        response.send(AppResponse.exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.log(Level.WARNING, String.format("Error in SimpleGetService.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code params(Object... parameters)} parameters setting method.
    private void testGetArrayParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetArrayParams",
                    (fromId, toId) -> dbClient().execute(
                            exec -> exec
                                    .createNamedGet("select-pokemons-idrng-order-arg")
                                    .params(fromId, toId)
                                    .execute()
                    ));
    }

    // Verify {@code params(List<?>)} parameters setting method.
    private void testGetListParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetListParams",
                    (fromId, toId) -> dbClient().execute(
                            exec -> {
                                List<Integer> params = new ArrayList<>(2);
                                params.add(fromId);
                                params.add(toId);
                                return exec
                                        .createNamedGet("select-pokemons-idrng-order-arg")
                                        .params(params)
                                        .execute();
                            }
                    ));
    }

    // Verify {@code params(Map<?>)} parameters setting method.
    private void testGetMapParams(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetMapParams",
                    (fromId, toId) -> dbClient().execute(
                            exec -> {
                                Map<String, Integer> params = new HashMap<>(2);
                                params.put("idmin", fromId);
                                params.put("idmax", toId);
                                return exec
                                        .createNamedGet("select-pokemons-idrng-named-arg")
                                        .params(params)
                                        .execute();
                            }
                    ));
    }

    // Verify {@code addParam(Object parameter)} parameters setting method.
    private void testGetOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetOrderParam",
                    (fromId, toId) -> dbClient().execute(
                            exec -> exec
                                    .createNamedGet("select-pokemons-idrng-order-arg")
                                    .addParam(fromId)
                                    .addParam(toId)
                                    .execute()
                    ));
    }

    // Verify {@code addParam(String name, Object parameter)} parameters setting method.
    private void testGetNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetNamedParam",
                    (fromId, toId) -> dbClient().execute(
                            exec -> exec
                                    .createNamedGet("select-pokemons-idrng-named-arg")
                                    .addParam("idmin", fromId)
                                    .addParam("idmax", toId)
                                    .execute()
                    ));
    }

    // Verify {@code namedParam(Object parameters)} mapped parameters setting method.
    private void testGetMappedNamedParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetMappedNamedParam",
                    (fromId, toId) -> dbClient().execute(
                            exec -> {
                                RangePoJo range = new RangePoJo(fromId, toId);
                                return exec
                                        .createNamedGet("select-pokemons-idrng-named-arg")
                                        .namedParam(range)
                                        .execute();
                            }
                    ));
    }

    // Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
    private void testGetMappedOrderParam(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetMappedOrderParam",
                    (fromId, toId) -> dbClient().execute(
                            exec -> {
                                RangePoJo range = new RangePoJo(fromId, toId);
                                return exec
                                        .createNamedGet("select-pokemons-idrng-order-arg")
                                        .indexedParam(range)
                                        .execute();
                            }
                    ));
    }


}
