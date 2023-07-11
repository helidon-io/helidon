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
package io.helidon.tests.integration.dbclient.appl.transaction;

import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbTransaction;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.JsonObject;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource to test set of basic DbClient get calls in transaction.
 */
public class TransactionGetService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(TransactionGetService.class.getName());

    private interface TestFunction extends Function<String, Optional<DbRow>> {
    }

    public TransactionGetService(final DbClient dbClient, final Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/testCreateNamedGetStrStrNamedArgs", this::testCreateNamedGetStrStrNamedArgs)
                .get("/testCreateNamedGetStrNamedArgs", this::testCreateNamedGetStrNamedArgs)
                .get("/testCreateNamedGetStrOrderArgs", this::testCreateNamedGetStrOrderArgs)
                .get("/testCreateGetNamedArgs", this::testCreateGetNamedArgs)
                .get("/testCreateGetOrderArgs", this::testCreateGetOrderArgs)
                .get("/testNamedGetStrOrderArgs", this::testNamedGetStrOrderArgs)
                .get("/testGetStrOrderArgs", this::testGetStrOrderArgs);
    }

    // Common test execution code
    private void executeTest(ServerRequest request,
                             ServerResponse response,
                             String testName,
                             TestFunction test) {

        LOGGER.log(Level.DEBUG, () -> String.format("Running SimpleGetService.%s on server", testName));
        try {
            String name = param(request, QUERY_NAME_PARAM);
            JsonObject jsonObject = test.apply(name)
                    .map(row -> row.as(JsonObject.class))
                    .orElse(JsonObject.EMPTY_JSON_OBJECT);
            response.send(okStatus(jsonObject));
        } catch (RemoteTestException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Error in SimpleGetService.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedGet(String, String)} API method with named
    private void testCreateNamedGetStrStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedGetStrStrNamedArgs",
                name -> {
                    DbTransaction tx = dbClient().transaction();
                    Optional<DbRow> result = tx
                            .createNamedGet("select-pikachu", statement("select-pokemon-named-arg"))
                            .addParam("name", name)
                            .execute();
                    tx.commit();
                    return result;
                });
    }

    // Verify {@code createNamedGet(String)} API method with named parameters.
    private void testCreateNamedGetStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedGetStrNamedArgs",
                name -> {
                    DbTransaction tx = dbClient().transaction();
                    Optional<DbRow> result = tx
                            .createNamedGet("select-pokemon-named-arg")
                            .addParam("name", name)
                            .execute();
                    tx.commit();
                    return result;
                });
    }

    // Verify {@code createNamedGet(String)} API method with ordered parameters.
    private void testCreateNamedGetStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedGetStrOrderArgs",
                name -> {
                    DbTransaction tx = dbClient().transaction();
                    Optional<DbRow> result = tx
                            .createNamedGet("select-pokemon-order-arg")
                            .addParam(name)
                            .execute();
                    tx.commit();
                    return result;
                });
    }

    // Verify {@code createGet(String)} API method with named parameters.
    private void testCreateGetNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateGetNamedArgs",
                name -> {
                    DbTransaction tx = dbClient().transaction();
                    Optional<DbRow> result = tx
                            .createGet(statement("select-pokemon-named-arg"))
                            .addParam("name", name)
                            .execute();
                    tx.commit();
                    return result;
                });
    }

    // Verify {@code createGet(String)} API method with ordered parameters.
    private void testCreateGetOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateGetOrderArgs",
                name -> {
                    DbTransaction tx = dbClient().transaction();
                    Optional<DbRow> result = tx
                            .createGet(statement("select-pokemon-order-arg"))
                            .addParam(name)
                            .execute();
                    tx.commit();
                    return result;
                });
    }

    // Verify {@code namedGet(String)} API method with ordered parameters passed
    private void testNamedGetStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testNamedGetStrOrderArgs",
                name -> {
                    DbTransaction tx = dbClient().transaction();
                    Optional<DbRow> result = tx
                            .namedGet("select-pokemon-order-arg", name);
                    tx.commit();
                    return result;
                });
    }

    // Verify {@code get(String)} API method with ordered parameters passed
    private void testGetStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testGetStrOrderArgs",
                name -> {
                    DbTransaction tx = dbClient().transaction();
                    Optional<DbRow> result = tx
                            .get(statement("select-pokemon-order-arg"), name);
                    tx.commit();
                    return result;
                });
    }
}
