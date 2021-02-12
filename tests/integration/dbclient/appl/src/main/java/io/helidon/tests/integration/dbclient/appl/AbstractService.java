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
package io.helidon.tests.integration.dbclient.appl;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.Service;

/**
 * Common web service code for testing application.
 */
public abstract class AbstractService implements Service {

    private static final Logger LOGGER = Logger.getLogger(AbstractService.class.getName());

    /** Query parameter for name of Pokemon, Type, etc.  */
    public static final String QUERY_NAME_PARAM = "name";
    /** Query parameter for ID of Pokemon, Type, etc.  */
    public static final String QUERY_ID_PARAM = "id";
    /** Query parameter for beginning of ID range.  */
    public static final String QUERY_FROM_ID_PARAM = "fromid";
    /** Query parameter for end of ID range.  */
    public static final String QUERY_TO_ID_PARAM = "toid";

    private final DbClient dbClient;

    private final Map<String, String> statements;

    /**
     * Creates an instance of common web service code for testing application.
     *
     * @param dbClient DbClient instance
     * @param statements statements from configuration file
     */
    public AbstractService(final DbClient dbClient, final Map<String, String> statements) {
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
    public String statement(final String name) {
        return statements.get(name);
    }

    /**
     * Retrieve HTTP query parameter value from request.
     *
     * @param request HTTP request context
     * @param name query parameter name
     * @return query parameter value
     * @throws RemoteTestException when no parameter with given name exists in request
     */
    public static final String param( final ServerRequest request, final String name) {
        Optional<String> maybeParam = request.queryParams().first(name);
        if (maybeParam.isPresent()) {
            return maybeParam.get();
        } else {
            throw new RemoteTestException(String.format("Query parameter %s is missing.", name));
        }
    }

}
