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
import java.util.function.Function;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import io.helidon.common.reactive.Multi;
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
 * Web resource to test set of basic DbClient queries in transaction.
 */
public class TransactionQueriesService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(TransactionQueriesService.class.getName());

    private interface TestFunction extends Function<String, Multi<DbRow>> {}

    public TransactionQueriesService(final DbClient dbClient, final Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testCreateNamedQueryStrStrOrderArgs", this::testCreateNamedQueryStrStrOrderArgs)
                .get("/testCreateNamedQueryStrNamedArgs", this::testCreateNamedQueryStrNamedArgs)
                .get("/testCreateNamedQueryStrOrderArgs", this::testCreateNamedQueryStrOrderArgs)
                .get("/testCreateQueryNamedArgs", this::testCreateQueryNamedArgs)
                .get("/testCreateQueryOrderArgs", this::testCreateQueryOrderArgs)
                .get("/testNamedQueryOrderArgs", this::testNamedQueryOrderArgs)
                .get("/testQueryOrderArgs", this::testQueryOrderArgs);
    }

    // Common test execution code
    private void executeTest(
            final ServerRequest request,
            final ServerResponse response,
            final String testName,
            final TestFunction test
    ) {
        LOGGER.fine(() -> String.format("Running SimpleQueryService.%s on server", testName));
        try {
            String name = param(request, QUERY_NAME_PARAM);
            Multi<DbRow> future = test.apply(name);
            final JsonArrayBuilder jab = Json.createArrayBuilder();
            future.forEach(dbRow -> jab.add(dbRow.as(JsonObject.class)))
                    .onComplete(() -> response.send(AppResponse.okStatus(jab.build())))
                    .exceptionally(t -> {
                        response.send(exceptionStatus(t));
                        return null;
                    });
        } catch (RemoteTestException ex) {
            LOGGER.fine(() -> String.format("Error in SimpleQueryService.%s on server", testName));
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedQuery(String, String)} API method with ordered
    private JsonObject testCreateNamedQueryStrStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedQueryStrStrOrderArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedQuery("select-pikachu", statement("select-pokemon-order-arg"))
                                .addParam(name)
                                .execute())
        );
        return null;
    }

    // Verify {@code createNamedQuery(String)} API method with named parameters.
    private JsonObject testCreateNamedQueryStrNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedQueryStrNamedArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedQuery("select-pokemon-named-arg")
                                .addParam("name", name)
                                .execute())
        );
        return null;
    }

    // Verify {@code createNamedQuery(String)} API method with ordered
    private JsonObject testCreateNamedQueryStrOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateNamedQueryStrOrderArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createNamedQuery("select-pokemon-order-arg")
                                .addParam(name)
                                .execute())
        );
        return null;
    }

    // Verify {@code createQuery(String)} API method with named parameters.
    private JsonObject testCreateQueryNamedArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateQueryNamedArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createQuery(statement("select-pokemon-named-arg"))
                                .addParam("name", name)
                                .execute())
        );
        return null;
    }

    // Verify {@code createQuery(String)} API method with ordered parameters.
    private JsonObject testCreateQueryOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testCreateQueryOrderArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .createQuery(statement("select-pokemon-order-arg"))
                                .addParam(name)
                                .execute()));
        return null;
    }

    // Verify {@code namedQuery(String)} API method with ordered parameters
    private JsonObject testNamedQueryOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testNamedQueryOrderArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .namedQuery("select-pokemon-order-arg", name))
        );
        return null;
    }

    // Verify {@code query(String)} API method with ordered parameters passed
    private JsonObject testQueryOrderArgs(final ServerRequest request, final ServerResponse response) {
        executeTest(request, response, "testQueryOrderArgs",
                name -> dbClient().inTransaction(
                        exec -> exec
                                .query(statement("select-pokemon-order-arg"), name))
        );
        return null;
    }

}
