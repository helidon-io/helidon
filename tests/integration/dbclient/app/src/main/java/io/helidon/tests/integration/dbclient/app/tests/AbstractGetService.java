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

import io.helidon.http.NotFoundException;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;

import jakarta.json.JsonObject;

import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.Optional;

import static io.helidon.tests.integration.harness.AppResponse.okStatus;

/**
 * Base service to test get statements.
 */
public abstract class AbstractGetService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(AbstractGetService.class.getName());

    /**
     * Creates a new instance.
     *
     * @param dbClient   DbClient instance
     * @param statements statements from configuration file
     */
    public AbstractGetService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{testName}", this::executeTest);
    }

    private void executeTest(ServerRequest request, ServerResponse response) {
        String testName = pathParam(request, "testName");
        String name = queryParam(request, QueryParams.NAME);
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on server", getClass().getSimpleName(), testName));
        Optional<DbRow> result = switch (testName) {
            case "testCreateNamedGetStrStrNamedArgs" -> testCreateNamedGetStrStrNamedArgs(name);
            case "testCreateNamedGetStrNamedArgs" -> testCreateNamedGetStrNamedArgs(name);
            case "testCreateNamedGetStrOrderArgs" -> testCreateNamedGetStrOrderArgs(name);
            case "testCreateGetNamedArgs" -> testCreateGetNamedArgs(name);
            case "testCreateGetOrderArgs" -> testCreateGetOrderArgs(name);
            case "testNamedGetStrOrderArgs" -> testNamedGetStrOrderArgs(name);
            case "testGetStrOrderArgs" -> testGetStrOrderArgs(name);
            default -> throw new NotFoundException("test not found: " + testName);
        };
        response.send(okStatus(result.map(row -> row.as(JsonObject.class))
                .orElse(JsonObject.EMPTY_JSON_OBJECT)));
    }

    /**
     * Verify {@code createNamedGet(String, String)} API method with named parameters.
     *
     * @param name parameter
     * @return row
     */
    protected abstract Optional<DbRow> testCreateNamedGetStrStrNamedArgs(String name);

    /**
     * Verify {@code createNamedGet(String)} API method with named parameters.
     *
     * @param name parameter
     * @return row
     */
    protected abstract Optional<DbRow> testCreateNamedGetStrNamedArgs(String name);

    /**
     * Verify {@code createNamedGet(String)} API method with ordered parameters.
     *
     * @param name parameter
     * @return row
     */
    protected abstract Optional<DbRow> testCreateNamedGetStrOrderArgs(String name);

    /**
     * Verify {@code createGet(String)} API method with named parameters.
     *
     * @param name parameter
     * @return row
     */
    protected abstract Optional<DbRow> testCreateGetNamedArgs(String name);

    /**
     * Verify {@code createGet(String)} API method with ordered parameters.
     *
     * @param name parameter
     * @return row
     */
    protected abstract Optional<DbRow> testCreateGetOrderArgs(String name);

    /**
     * Verify {@code namedGet(String)} API method with ordered parameters passed directly to the {@code query} method.
     *
     * @param name parameter
     * @return row
     */
    protected abstract Optional<DbRow> testNamedGetStrOrderArgs(String name);

    /**
     * Verify {@code get(String)} API method with ordered parameters passed directly to the {@code query} method.
     *
     * @param name parameter
     * @return row
     */
    protected abstract Optional<DbRow> testGetStrOrderArgs(String name);

}
