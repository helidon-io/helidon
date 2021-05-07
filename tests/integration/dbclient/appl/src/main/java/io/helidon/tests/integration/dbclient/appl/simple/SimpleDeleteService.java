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
package io.helidon.tests.integration.dbclient.appl.simple;

import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.json.Json;

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to test set of basic JDBC deletes.
 */
public class SimpleDeleteService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(SimpleUpdateService.class.getName());

    // Internal functional interface used to implement testing code.
    // Method call: apply(srcPokemon, updatedPokemon)
    private interface TestFunction extends Function<Integer, Single<Long>> {}

    /**
     * Creates an instance of web resource to test set of basic DbClient deletes.
     *
     * @param dbClient DbClient instance
     * @param statements statements from configuration file
     */
    public SimpleDeleteService(final DbClient dbClient, final Map<String, String> statements) {
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
    private void executeTest(final ServerRequest request, final ServerResponse response, final String testName, final TestFunction test) {
        LOGGER.fine(() -> String.format("Running SimpleDeleteService.%s on server", testName));
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
            LOGGER.fine(() -> String.format("Error in SimpleDeleteService.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
    private void testCreateNamedDeleteStrStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedDeleteStrStrOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createNamedDelete("delete-rayquaza", statement("delete-pokemon-order-arg"))
                .addParam(id).execute()
        ));
    }

    // Verify {@code createNamedDelete(String)} API method with named parameters.
    private void testCreateNamedDeleteStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedDeleteStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", id).execute()
        ));
    }

    // Verify {@code createNamedDelete(String)} API method with ordered parameters.
    private void testCreateNamedDeleteStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedDeleteStrOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createNamedDelete("delete-pokemon-order-arg")
                .addParam(id).execute()
        ));
    }

    // Verify {@code createDelete(String)} API method with named parameters.
    private void testCreateDeleteNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createDelete(statement("delete-pokemon-named-arg"))
                .addParam("id", id).execute()
        ));
    }

    // Verify {@code createDelete(String)} API method with ordered parameters.
    private void testCreateDeleteOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateDeleteOrderArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .createDelete(statement("delete-pokemon-order-arg"))
                .addParam(id).execute()
        ));
    }

    // Verify {@code namedDelete(String)} API method with ordered parameters.
    private void testNamedDeleteOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .namedDelete("delete-pokemon-order-arg", id)
        ));
    }

    // Verify {@code delete(String)} API method with ordered parameters.
    private void testDeleteOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                .delete(statement("delete-pokemon-order-arg"), id)
        ));
    }

    // DML delete

    // Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
    private void testCreateNamedDmlWithDeleteStrStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("delete-mudkip", statement("delete-pokemon-order-arg"))
                                .addParam(id)
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
    private void testCreateNamedDmlWithDeleteStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("delete-pokemon-named-arg")
                                .addParam("id", id)
                                .execute()
                ));
    }

    // Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
    private void testCreateNamedDmlWithDeleteStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createNamedDmlStatement("delete-pokemon-order-arg")
                                .addParam(id)
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with delete with named parameters.
    private void testCreateDmlWithDeleteNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createDmlStatement(statement("delete-pokemon-named-arg"))
                                .addParam("id", id)
                                .execute()
                ));
    }

    // Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
    private void testCreateDmlWithDeleteOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .createDmlStatement(statement("delete-pokemon-order-arg"))
                                .addParam(id)
                                .execute()
                ));
    }

    // Verify {@code namedDml(String)} API method with delete with ordered parameters.
    private void testNamedDmlWithDeleteOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .namedDml("delete-pokemon-order-arg", id)
                ));
    }

    // Verify {@code dml(String)} API method with delete with ordered parameters.
    private void testDmlWithDeleteOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedUpdateStrStrNamedArgs",
                (id) -> dbClient().execute(
                        exec -> exec
                                .dml(statement("delete-pokemon-order-arg"), id)
                ));
    }

}
