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
package io.helidon.tests.integration.dbclient.appl.simple;

import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to test set of basic JDBC deletes.
 */
public class SimpleDeleteService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(SimpleUpdateService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Integer, Single<Long>> {}

    /**
     * Creates an instance of web resource to test set of basic DbClient deletes.
     *
     * @param dbClient DbClient instance
     * @param statements statements from configuration file
     */
    public SimpleDeleteService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testCreateNamedDeleteStrStrOrderArgs", this::testCreateNamedDeleteStrStrOrderArgs)
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
    private void executeTest(ServerRequest request, ServerResponse response, String testName, TestFunction test) {
        try {
            String idStr = param(request, QUERY_ID_PARAM);
            int id = Integer.parseInt(idStr);
            test.apply(id)
                    .thenAccept(
                            result -> response.send(
                                    AppResponse.okStatus(Json.createValue(result))))
                    .exceptionally(t -> {
                        response.send(exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException | NumberFormatException ex) {
            LOGGER.log(Level.WARNING, String.format("Error in SimpleDeleteService.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
    private void testCreateNamedDeleteStrStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDeleteStrStrOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createNamedDelete("delete-rayquaza", statement("delete-pokemon-order-arg"))
                .addParam(id).execute()
        ));
    }

    // Verify {@code createNamedDelete(String)} API method with named parameters.
    private void testCreateNamedDeleteStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDeleteStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", id).execute()
        ));
    }

    // Verify {@code createNamedDelete(String)} API method with ordered parameters.
    private void testCreateNamedDeleteStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDeleteStrOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createNamedDelete("delete-pokemon-order-arg")
                .addParam(id).execute()
        ));
    }

    // Verify {@code createDelete(String)} API method with named parameters.
    private void testCreateDeleteNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateDeleteNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createDelete(statement("delete-pokemon-named-arg"))
                .addParam("id", id).execute()
        ));
    }

    // Verify {@code createDelete(String)} API method with ordered parameters.
    private void testCreateDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateDeleteOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createDelete(statement("delete-pokemon-order-arg"))
                .addParam(id).execute()
        ));
    }

    // Verify {@code namedDelete(String)} API method with ordered parameters.
    private void testNamedDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testNamedDeleteOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .namedDelete("delete-pokemon-order-arg", id)
        ));
    }

    // Verify {@code delete(String)} API method with ordered parameters.
    private void testDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDeleteOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .delete(statement("delete-pokemon-order-arg"), id)
        ));
    }

    // DML delete

    // Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
    private void testCreateNamedDmlWithDeleteStrStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDmlWithDeleteStrStrOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("delete-mudkip", statement("delete-pokemon-order-arg"))
                                .addParam(id)
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
    private void testCreateNamedDmlWithDeleteStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDmlWithDeleteStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("delete-pokemon-named-arg")
                                .addParam("id", id)
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
    private void testCreateNamedDmlWithDeleteStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedDmlWithDeleteStrOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("delete-pokemon-order-arg")
                                .addParam(id)
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with delete with named parameters.
    private void testCreateDmlWithDeleteNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateDmlWithDeleteNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createDmlStatement(statement("delete-pokemon-named-arg"))
                                .addParam("id", id)
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
    private void testCreateDmlWithDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateDmlWithDeleteOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createDmlStatement(statement("delete-pokemon-order-arg"))
                                .addParam(id)
                                .execute()
                ));
    }

    // Verify {@code namedDml(String)} API method with delete with ordered parameters.
    private void testNamedDmlWithDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testNamedDmlWithDeleteOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .namedDml("delete-pokemon-order-arg", id)
                ));
    }

    // Verify {@code dml(String)} API method with delete with ordered parameters.
    private void testDmlWithDeleteOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testDmlWithDeleteOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .dml(statement("delete-pokemon-order-arg"), id)
                ));
    }

}
