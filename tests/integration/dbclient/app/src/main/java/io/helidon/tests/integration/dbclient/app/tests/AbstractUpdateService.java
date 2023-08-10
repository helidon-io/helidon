/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.app.tests;

import java.lang.System.Logger.Level;
import java.util.Map;

import io.helidon.http.NotFoundException;
import io.helidon.dbclient.DbClient;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;

import jakarta.json.Json;

import static io.helidon.tests.integration.harness.AppResponse.okStatus;

/**
 * Base service to test update statements.
 */
public abstract class AbstractUpdateService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(AbstractUpdateService.class.getName());

    /**
     * Create a new instance.
     *
     * @param dbClient   dbclient
     * @param statements statements
     */
    public AbstractUpdateService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{testName}", this::executeTest);
    }

    private void executeTest(ServerRequest request, ServerResponse response) {
        String testName = pathParam(request, "testName");
        int id = Integer.parseInt(queryParam(request, QueryParams.ID));
        String name = queryParam(request, QueryParams.NAME);
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on server", getClass().getSimpleName(), testName));
        long count = switch (testName) {
            case "testCreateNamedUpdateStrStrNamedArgs" -> testCreateNamedUpdateStrStrNamedArgs(id, name);
            case "testCreateNamedUpdateStrNamedArgs" -> testCreateNamedUpdateStrNamedArgs(id, name);
            case "testCreateNamedUpdateStrOrderArgs" -> testCreateNamedUpdateStrOrderArgs(id, name);
            case "testCreateUpdateNamedArgs" -> testCreateUpdateNamedArgs(id, name);
            case "testCreateUpdateOrderArgs" -> testCreateUpdateOrderArgs(id, name);
            case "testNamedUpdateNamedArgs" -> testNamedUpdateNamedArgs(id, name);
            case "testUpdateOrderArgs" -> testUpdateOrderArgs(id, name);
            case "testCreateNamedDmlWithUpdateStrStrNamedArgs" -> testCreateNamedDmlWithUpdateStrStrNamedArgs(id, name);
            case "testCreateNamedDmlWithUpdateStrNamedArgs" -> testCreateNamedDmlWithUpdateStrNamedArgs(id, name);
            case "testCreateNamedDmlWithUpdateStrOrderArgs" -> testCreateNamedDmlWithUpdateStrOrderArgs(id, name);
            case "testCreateDmlWithUpdateNamedArgs" -> testCreateDmlWithUpdateNamedArgs(id, name);
            case "testCreateDmlWithUpdateOrderArgs" -> testCreateDmlWithUpdateOrderArgs(id, name);
            case "testNamedDmlWithUpdateOrderArgs" -> testNamedDmlWithUpdateOrderArgs(id, name);
            case "testDmlWithUpdateOrderArgs" -> testDmlWithUpdateOrderArgs(id, name);
            default -> throw new NotFoundException("test not found: " + testName);
        };
        response.send(okStatus(Json.createValue(count)));
    }

    /**
     * Verify {@code createNamedUpdate(String, String)} API method with ordered parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedUpdateStrStrNamedArgs(int id, String name);

    /**
     * Verify {@code createNamedUpdate(String)} API method with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedUpdateStrNamedArgs(int id, String name);

    /**
     * Verify {@code createNamedUpdate(String)} API method with ordered parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedUpdateStrOrderArgs(int id, String name);

    /**
     * Verify {@code createUpdate(String)} API method with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateUpdateNamedArgs(int id, String name);

    /**
     * Verify {@code createUpdate(String)} API method with ordered parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateUpdateOrderArgs(int id, String name);

    /**
     * Verify {@code namedUpdate(String)} API method with ordered parameters passed directly to the
     * {@code namedQuery} method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testNamedUpdateNamedArgs(int id, String name);

    /**
     * Verify {@code update(String)} API method with ordered parameters passed directly to the {@code query} method.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testUpdateOrderArgs(int id, String name);

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedDmlWithUpdateStrStrNamedArgs(int id, String name);

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedDmlWithUpdateStrNamedArgs(int id, String name);

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateNamedDmlWithUpdateStrOrderArgs(int id, String name);

    /**
     * Verify {@code createDmlStatement(String)} API method with update with named parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateDmlWithUpdateNamedArgs(int id, String name);

    /**
     * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testCreateDmlWithUpdateOrderArgs(int id, String name);

    /**
     * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testNamedDmlWithUpdateOrderArgs(int id, String name);

    /**
     * Verify {@code dml(String)} API method with update with ordered parameters passed directly.
     *
     * @param id   parameter
     * @param name parameter
     * @return count
     */
    protected abstract long testDmlWithUpdateOrderArgs(int id, String name);

}
