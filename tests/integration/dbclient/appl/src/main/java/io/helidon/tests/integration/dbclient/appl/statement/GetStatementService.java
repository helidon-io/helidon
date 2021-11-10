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
package io.helidon.tests.integration.dbclient.appl.statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import jakarta.json.JsonObject;

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.RangePoJo;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to test DbStatementGet methods.
 */
public class GetStatementService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(GetStatementService.class.getName());

    private interface TestFunction extends BiFunction<Integer, Integer, Single<Optional<DbRow>>> {
    }

    public GetStatementService(final DbClient dbClient, final Map<String, String> statements) {
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
    private JsonObject executeTest(
            final ServerRequest request,
            final ServerResponse response,
            final String testName,
            final TestFunction test
    ) {
        LOGGER.fine(() -> String.format("Running SimpleGetService.%s on server", testName));
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
            LOGGER.fine(() -> String.format("Error in SimpleGetService.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
        return null;
    }

    // Verify {@code params(Object... parameters)} parameters setting method.
    private JsonObject testGetArrayParams(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedGetStrStrNamedArgs",
                (fromId, toId) -> dbClient().execute(
                        exec -> exec
                .createNamedGet("select-pokemons-idrng-order-arg")
                .params(fromId, toId)
                .execute()
        ));
    }

    // Verify {@code params(List<?>)} parameters setting method.
    private JsonObject testGetListParams(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedGetStrStrNamedArgs",
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
    private JsonObject testGetMapParams(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedGetStrStrNamedArgs",
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
    private JsonObject testGetOrderParam(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedGetStrStrNamedArgs",
                (fromId, toId) -> dbClient().execute(
                        exec -> exec
                                .createNamedGet("select-pokemons-idrng-order-arg")
                                .addParam(fromId)
                                .addParam(toId)
                                .execute()
                ));
    }

    // Verify {@code addParam(String name, Object parameter)} parameters setting method.
    private JsonObject testGetNamedParam(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedGetStrStrNamedArgs",
                (fromId, toId) -> dbClient().execute(
                        exec -> exec
                                .createNamedGet("select-pokemons-idrng-named-arg")
                                .addParam("idmin", fromId)
                                .addParam("idmax", toId)
                                .execute()
                ));
    }

    // Verify {@code namedParam(Object parameters)} mapped parameters setting method.
    private JsonObject testGetMappedNamedParam(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedGetStrStrNamedArgs",
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
    private JsonObject testGetMappedOrderParam(final ServerRequest request, final ServerResponse response) {
        return executeTest(request, response, "testCreateNamedGetStrStrNamedArgs",
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
