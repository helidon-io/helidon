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
package io.helidon.tests.integration.dbclient.app.tests;

import java.util.Map;

import io.helidon.dbclient.DbClient;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.tests.integration.harness.RemoteTestException;

/**
 * Common web service code for testing application.
 */
public abstract class AbstractService implements HttpService {

    private final DbClient dbClient;
    private final Map<String, String> statements;

    /**
     * Creates an instance of common web service code for testing application.
     *
     * @param dbClient   DbClient instance
     * @param statements statements from configuration file
     */
    public AbstractService(DbClient dbClient, Map<String, String> statements) {
        this.dbClient = dbClient;
        this.statements = statements;
    }

    /**
     * Returns stored DbClient instance.
     *
     * @return DbClient instance
     */
    public DbClient dbClient() {
        return dbClient;
    }

    /**
     * Returns stored statement from configuration file.
     *
     * @param name statement configuration property name
     * @return statement from configuration file
     */
    public String statement(String name) {
        return statements.get(name);
    }

    /**
     * Retrieve HTTP query parameter value from request.
     *
     * @param request HTTP request context
     * @param name    query parameter name
     * @return query parameter value
     * @throws RemoteTestException when no parameter with given name exists in request
     */
    public static String queryParam(ServerRequest request, String name) {
        return request.query().first(name)
                .orElseThrow(() -> new RemoteTestException("Query parameter %s is missing.", name));
    }

    /**
     * Retrieve HTTP path parameter value from request.
     *
     * @param request HTTP request context
     * @param name    path parameter name
     * @return path parameter value
     * @throws RemoteTestException when no parameter with given name exists in request
     */
    public static String pathParam(ServerRequest request, String name) {
        return request.path().pathParameters().first(name)
                .orElseThrow(() -> new RemoteTestException("Path parameter %s is missing.", name));
    }
}
