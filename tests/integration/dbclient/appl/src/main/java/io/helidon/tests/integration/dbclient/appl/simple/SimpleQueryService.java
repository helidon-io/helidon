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

import io.helidon.common.reactive.Multi;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.DbRow;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to test set of basic DbClient queries.
 */
public class SimpleQueryService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(SimpleQueryService.class.getName());

    private interface TestFunction extends Function<String, Multi<DbRow>> {}

    public SimpleQueryService(DbClient dbClient, Map<String, String> statements) {
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
            ServerRequest request,
            ServerResponse response,
            String testName,
            TestFunction test
    ) {
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
            LOGGER.log(Level.WARNING, String.format("Error in SimpleQueryService.%s on server", testName), ex);
            response.send(exceptionStatus(ex));
        }
    }

    // Verify {@code createNamedQuery(String, String)} API method with ordered
    private void testCreateNamedQueryStrStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedQueryStrStrOrderArgs",
                name -> dbClient().execute(
                        exec -> exec
                                .createNamedQuery("select-pikachu", statement("select-pokemon-order-arg"))
                                .addParam(name)
                                .execute())
        );
    }

    // Verify {@code createNamedQuery(String)} API method with named parameters.
    private void testCreateNamedQueryStrNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedQueryStrNamedArgs",
                name -> dbClient().execute(
                        exec -> exec
                                .createNamedQuery("select-pokemon-named-arg")
                                .addParam("name", name)
                                .execute())
        );
    }

    // Verify {@code createNamedQuery(String)} API method with ordered
    private void testCreateNamedQueryStrOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateNamedQueryStrOrderArgs",
                name -> dbClient().execute(
                        exec -> exec
                                .createNamedQuery("select-pokemon-order-arg")
                                .addParam(name)
                                .execute())
        );
    }

    // Verify {@code createQuery(String)} API method with named parameters.
    private void testCreateQueryNamedArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateQueryNamedArgs",
                name -> dbClient().execute(
                        exec -> exec
                                .createQuery(statement("select-pokemon-named-arg"))
                                .addParam("name", name)
                                .execute())
        );
    }

    // Verify {@code createQuery(String)} API method with ordered parameters.
    private void testCreateQueryOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testCreateQueryOrderArgs",
                name -> dbClient().execute(
                        exec -> exec
                                .createQuery(statement("select-pokemon-order-arg"))
                                .addParam(name)
                                .execute()));
    }

    // Verify {@code namedQuery(String)} API method with ordered parameters
    private void testNamedQueryOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testNamedQueryOrderArgs",
                name -> dbClient().execute(
                        exec -> exec
                                .namedQuery("select-pokemon-order-arg", name))
        );
    }

    // Verify {@code query(String)} API method with ordered parameters passed
    private void testQueryOrderArgs(ServerRequest request, ServerResponse response) {
        executeTest(request, response, "testQueryOrderArgs",
                name -> dbClient().execute(
                        exec -> exec
                                .query(statement("select-pokemon-order-arg"), name))
        );
    }

}
