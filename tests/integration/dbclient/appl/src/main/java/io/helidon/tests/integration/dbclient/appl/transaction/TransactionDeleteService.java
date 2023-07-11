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
import java.util.function.Function;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbTransaction;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;
import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Web resource to test set of basic DbClient delete calls in transaction.
 */
@SuppressWarnings("SpellCheckingInspection")
public class TransactionDeleteService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(TransactionDeleteService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Integer, Long> {
    }

    public TransactionDeleteService(final DbClient dbClient, final Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/testCreateNamedDeleteStrStrOrderArgs", this::testCreateNamedDeleteStrStrOrderArgs)
                .get("/testCreateNamedDeleteStrNamedArgs", this::testCreateNamedDeleteStrNamedArgs)
                .get("/testCreateNamedDeleteStrOrderArgs", this::testCreateNamedDeleteStrOrderArgs)
                .get("/testCreateDeleteNamedArgs", this::testCreateDeleteNamedArgs)
                .get("/testCreateDeleteOrderArgs", this::testCreateDeleteOrderArgs)
                .get("/testNamedDeleteOrderArgs", this::testNamedDeleteOrderArgs)
                .get("/testDeleteOrderArgs", this::testDeleteOrderArgs)
                .get("/testCreateNamedDmlWithDeleteStrStrOrderArgs", this::testCreateNamedDmlWithDeleteStrStrOrderArgs)
                .get("/testCreateNamedDmlWithDeleteStrNamedArgs", this::testCreateNamedDmlWithDeleteStrNamedArgs)
                .get("/testCreateNamedDmlWithDeleteStrOrderArgs", this::testCreateNamedDmlWithDeleteStrOrderArgs)
                .get("/testCreateDmlWithDeleteNamedArgs", this::testCreateDmlWithDeleteNamedArgs)
                .get("/testCreateDmlWithDeleteOrderArgs", this::testCreateDmlWithDeleteOrderArgs)
                .get("/testNamedDmlWithDeleteOrderArgs", this::testNamedDmlWithDeleteOrderArgs)
                .get("/testDmlWithDeleteOrderArgs", this::testDmlWithDeleteOrderArgs);
    }

    // Common test execution code
    private void executeTest(ServerRequest request,
                             ServerResponse response,
                             String testName,
                             TestFunction test) {

        LOGGER.log(Level.DEBUG, () -> String.format("Running SimpleDeleteService.%s on server", testName));
        try {
            String idStr = param(request, QUERY_ID_PARAM);
            int id = Integer.parseInt(idStr);
            long count = test.apply(id);
            response.send(okStatus(Json.createValue(count)));
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.log(Level.DEBUG, () -> String.format("Error in SimpleDeleteService.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
    private void testCreateNamedDeleteStrStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDeleteStrStrOrderArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createNamedDelete("delete-rayquaza", statement("delete-pokemon-order-arg"))
                            .addParam(id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedDelete(String)} API method with named parameters.
    private void testCreateNamedDeleteStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDeleteStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createNamedDelete("delete-pokemon-named-arg")
                            .addParam("id", id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedDelete(String)} API method with ordered parameters.
    private void testCreateNamedDeleteStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDeleteStrOrderArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createNamedDelete("delete-pokemon-order-arg")
                            .addParam(id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createDelete(String)} API method with named parameters.
    private void testCreateDeleteNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createDelete(statement("delete-pokemon-named-arg"))
                            .addParam("id", id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createDelete(String)} API method with ordered parameters.
    private void testCreateDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateDeleteOrderArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createDelete(statement("delete-pokemon-order-arg"))
                            .addParam(id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code namedDelete(String)} API method with ordered parameters.
    private void testNamedDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.namedDelete("delete-pokemon-order-arg", id);
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code delete(String)} API method with ordered parameters.
    private void testDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.delete(statement("delete-pokemon-order-arg"), id);
                    tx.commit();
                    return count;
                });
    }

    // DML delete

    // Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
    private void testCreateNamedDmlWithDeleteStrStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createNamedDmlStatement("delete-mudkip", statement("delete-pokemon-order-arg"))
                            .addParam(id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
    private void testCreateNamedDmlWithDeleteStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createNamedDmlStatement("delete-pokemon-named-arg")
                            .addParam("id", id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
    private void testCreateNamedDmlWithDeleteStrOrderArgs(ServerRequest request,
                                                          ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createNamedDmlStatement("delete-pokemon-order-arg")
                            .addParam(id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createDmlStatement(String)} API method with delete with named parameters.
    private void testCreateDmlWithDeleteNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createDmlStatement(statement("delete-pokemon-named-arg"))
                            .addParam("id", id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
    private void testCreateDmlWithDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.createDmlStatement(statement("delete-pokemon-order-arg"))
                            .addParam(id)
                            .execute();
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code namedDml(String)} API method with delete with ordered parameters.
    private void testNamedDmlWithDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.namedDml("delete-pokemon-order-arg", id);
                    tx.commit();
                    return count;
                });
    }

    // Verify {@code dml(String)} API method with delete with ordered parameters.
    private void testDmlWithDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> {
                    DbTransaction tx = dbClient().transaction();
                    long count = tx.dml(statement("delete-pokemon-order-arg"), id);
                    tx.commit();
                    return count;
                });
    }

}
