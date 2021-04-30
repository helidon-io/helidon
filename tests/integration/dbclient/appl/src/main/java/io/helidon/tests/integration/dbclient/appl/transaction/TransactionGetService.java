/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.appl.transaction;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to test set of basic DbClient get calls in transaction.
 */
public class TransactionGetService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(TransactionGetService.class.getName());

    private interface TestFunction extends Function<String, Single<Optional<DbRow>>> {
    }

    public TransactionGetService(final DbClient dbClient, final Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testCreateNamedGetStrStrNamedArgs", this::testCreateNamedGetStrStrNamedArgs)
                .get("/testCreateNamedGetStrNamedArgs", this::testCreateNamedGetStrNamedArgs)
                .get("/testCreateNamedGetStrOrderArgs", this::testCreateNamedGetStrOrderArgs)
                .get("/testCreateGetNamedArgs", this::testCreateGetNamedArgs)
                .get("/testCreateGetOrderArgs", this::testCreateGetOrderArgs)
                .get("/testNamedGetStrOrderArgs", this::testNamedGetStrOrderArgs)
                .get("/testGetStrOrderArgs", this::testGetStrOrderArgs);
    }

    // Common test execution code
    private void executeTest(
            final ServerRequest request,
            final ServerResponse response,
            final String testName,
            final TestFunction test
    ) {
        LOGGER.fine(() -> String.format("Running SimpleGetService.%s on server", testName));
        try {
            String name = param(request, QUERY_NAME_PARAM);
            test.apply(name)
                    .thenAccept(
                            data -> data.ifPresentOrElse(
                                    row -> response.send(
                                            AppResponse.okStatus(row.as(JsonObject.class))),
                                    () -> response.send(
                                            AppResponse.okStatus(JsonObject.EMPTY_JSON_OBJECT))))
                    .exceptionally(t -> {
                        response.send(exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException ex) {
            LOGGER.fine(() -> String.format("Error in SimpleGetService.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedGet(String, String)} API method with named
    private JsonObject testCreateNamedGetStrStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedGetStrStrNamedArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedGet("select-pikachu", statement("select-pokemon-named-arg"))
                                .addParam("name", name).execute()
                ));
        return null;
    }

    // Verify {@code createNamedGet(String)} API method with named parameters.
    private JsonObject testCreateNamedGetStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedGetStrNamedArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedGet("select-pokemon-named-arg")
                                .addParam("name", name).execute()
                ));
        return null;
    }

    // Verify {@code createNamedGet(String)} API method with ordered parameters.
    private JsonObject testCreateNamedGetStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedGetStrOrderArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedGet("select-pokemon-order-arg")
                                .addParam(name).execute()
                ));
        return null;
    }

    // Verify {@code createGet(String)} API method with named parameters.
    private JsonObject testCreateGetNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateGetNamedArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createGet(statement("select-pokemon-named-arg"))
                                .addParam("name", name).execute()
                ));
        return null;
    }

    // Verify {@code createGet(String)} API method with ordered parameters.
    private JsonObject testCreateGetOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateGetOrderArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createGet(statement("select-pokemon-order-arg"))
                                .addParam(name).execute()
                ));
        return null;
    }

    // Verify {@code namedGet(String)} API method with ordered parameters passed
    private JsonObject testNamedGetStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testNamedGetStrOrderArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .namedGet("select-pokemon-order-arg", name)
                ));
        return null;
    }

    // Verify {@code get(String)} API method with ordered parameters passed
    private JsonObject testGetStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testGetStrOrderArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .get(statement("select-pokemon-order-arg"), name)
                ));
        return null;
    }

}
