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
package io.helidon.tests.integration.dbclient.app;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.NotFoundException;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.tools.*;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import static io.helidon.tests.integration.tools.service.AppResponse.okStatus;

/**
 * Base service to test query statements.
 */
public abstract class AbstractQueryService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(AbstractQueryService.class.getName());

    /**
     * Create a new instance.
     *
     * @param dbClient   dbclient
     * @param statements statements
     */
    public AbstractQueryService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{testName}", this::executeTest);
    }

    private void executeTest(ServerRequest request, ServerResponse response) {
        String testName = pathParam(request, "testName");
        String name = queryParam(request, QueryParams.NAME);
        LOGGER.log(Level.DEBUG, () -> String.format("Running %s.%s on server",
                getClass().getSimpleName(), testName));
        List<DbRow> result = switch (testName) {
            case "testCreateNamedQueryStrStrOrderArgs" -> testCreateNamedQueryStrStrOrderArgs(name);
            case "testCreateNamedQueryStrNamedArgs" -> testCreateNamedQueryStrNamedArgs(name);
            case "testCreateNamedQueryStrOrderArgs" -> testCreateNamedQueryStrOrderArgs(name);
            case "testCreateQueryNamedArgs" -> testCreateQueryNamedArgs(name);
            case "testCreateQueryOrderArgs" -> testCreateQueryOrderArgs(name);
            case "testNamedQueryOrderArgs" -> testNamedQueryOrderArgs(name);
            case "testQueryOrderArgs" -> testQueryOrderArgs(name);
            default -> throw new NotFoundException("test not found: " + testName);
        };
        JsonArray jsonArray = result.stream()
                .map(row -> row.as(JsonObject.class))
                .collect(Json::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::add)
                .build();
        response.send(okStatus(jsonArray));
    }

    /**
     * Verify {@code createNamedQuery(String, String)} API method with ordered
     *
     * @param name parameter
     * @return rows
     */
    protected abstract List<DbRow> testCreateNamedQueryStrStrOrderArgs(String name);

    /**
     * Verify {@code createNamedQuery(String)} API method with named parameters.
     *
     * @param name parameter
     * @return rows
     */
    protected abstract List<DbRow> testCreateNamedQueryStrNamedArgs(String name);

    /**
     * Verify {@code createNamedQuery(String)} API method with ordered
     *
     * @param name parameter
     * @return rows
     */
    protected abstract List<DbRow> testCreateNamedQueryStrOrderArgs(String name);

    /**
     * Verify {@code createQuery(String)} API method with named parameters.
     *
     * @param name parameter
     * @return rows
     */
    protected abstract List<DbRow> testCreateQueryNamedArgs(String name);

    /**
     * Verify {@code createQuery(String)} API method with ordered parameters.
     *
     * @param name parameter
     * @return rows
     */
    protected abstract List<DbRow> testCreateQueryOrderArgs(String name);

    /**
     * Verify {@code namedQuery(String)} API method with ordered parameters
     *
     * @param name parameter
     * @return rows
     */
    protected abstract List<DbRow> testNamedQueryOrderArgs(String name);

    /**
     * Verify {@code query(String)} API method with ordered parameters passed
     *
     * @param name parameter
     * @return rows
     */
    protected abstract List<DbRow> testQueryOrderArgs(String name);
}
