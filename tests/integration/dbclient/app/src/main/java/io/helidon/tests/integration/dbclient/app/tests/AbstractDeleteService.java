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

import io.helidon.common.http.NotFoundException;
import io.helidon.dbclient.DbClient;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.tools.QueryParams;

import jakarta.json.Json;

import static io.helidon.tests.integration.harness.AppResponse.okStatus;

/**
 * Base service to test delete statements.
 */
public abstract class AbstractDeleteService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(AbstractDeleteService.class.getName());

    /**
     * Creates a new instance.
     *
     * @param dbClient   DbClient instance
     * @param statements statements from configuration file
     */
    protected AbstractDeleteService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{testName}", this::executeTest);
    }

    private void executeTest(ServerRequest request, ServerResponse response) {
        String testName = pathParam(request, "testName");
        int id = Integer.parseInt(queryParam(request, QueryParams.ID));
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on server", getClass().getSimpleName(), testName));
        long count = switch (testName) {
            case "testCreateNamedDeleteStrStrOrderArgs" -> testCreateNamedDeleteStrStrOrderArgs(id);
            case "testCreateDmlWithDeleteNamedArgs" -> testCreateDmlWithDeleteNamedArgs(id);
            case "testCreateNamedDeleteStrNamedArgs" -> testCreateNamedDeleteStrNamedArgs(id);
            case "testCreateNamedDeleteStrOrderArgs" -> testCreateNamedDeleteStrOrderArgs(id);
            case "testCreateDeleteNamedArgs" -> testCreateDeleteNamedArgs(id);
            case "testCreateDeleteOrderArgs" -> testCreateDeleteOrderArgs(id);
            case "testNamedDeleteOrderArgs" -> testNamedDeleteOrderArgs(id);
            case "testDeleteOrderArgs" -> testDeleteOrderArgs(id);
            case "testCreateNamedDmlWithDeleteStrStrOrderArgs" -> testCreateNamedDmlWithDeleteStrStrOrderArgs(id);
            case "testCreateNamedDmlWithDeleteStrNamedArgs" -> testCreateNamedDmlWithDeleteStrNamedArgs(id);
            case "testCreateNamedDmlWithDeleteStrOrderArgs" -> testCreateNamedDmlWithDeleteStrOrderArgs(id);
            case "testCreateDmlWithDeleteOrderArgs" -> testCreateDmlWithDeleteOrderArgs(id);
            case "testNamedDmlWithDeleteOrderArgs" -> testNamedDmlWithDeleteOrderArgs(id);
            case "testDmlWithDeleteOrderArgs" -> testDmlWithDeleteOrderArgs(id);
            default -> throw new NotFoundException("test not found: " + testName);
        };
        response.send(okStatus(Json.createValue(count)));
    }

    /**
     * Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateNamedDeleteStrStrOrderArgs(int id);

    /**
     * Verify {@code createNamedDelete(String)} API method with named parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateNamedDeleteStrNamedArgs(int id);

    /**
     * Verify {@code createNamedDelete(String)} API method with ordered parameters
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateNamedDeleteStrOrderArgs(int id);

    /**
     * Verify {@code createDelete(String)} API method with named parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateDeleteNamedArgs(int id);

    /**
     * Verify {@code createDelete(String)} API method with ordered parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateDeleteOrderArgs(int id);

    /**
     * Verify {@code namedDelete(String)} API method with ordered parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testNamedDeleteOrderArgs(int id);

    /**
     * Verify {@code delete(String)} API method with ordered parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testDeleteOrderArgs(int id);

    /**
     * Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateNamedDmlWithDeleteStrStrOrderArgs(int id);

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateNamedDmlWithDeleteStrNamedArgs(int id);

    /**
     * Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateNamedDmlWithDeleteStrOrderArgs(int id);

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with named parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateDmlWithDeleteNamedArgs(int id);

    /**
     * Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testCreateDmlWithDeleteOrderArgs(int id);

    /**
     * Verify {@code namedDml(String)} API method with delete with ordered parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testNamedDmlWithDeleteOrderArgs(int id);

    /**
     * Verify {@code dml(String)} API method with delete with ordered parameters.
     *
     * @param id parameter
     * @return count
     */
    protected abstract long testDmlWithDeleteOrderArgs(int id);
}
