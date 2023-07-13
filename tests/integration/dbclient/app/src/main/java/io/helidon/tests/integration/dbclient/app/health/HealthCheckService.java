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
package io.helidon.tests.integration.dbclient.app.health;

import java.lang.System.Logger.Level;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.app.AbstractService;
import io.helidon.tests.integration.dbclient.app.tools.*;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

/**
 * Service to verify that health check works.
 */
public class HealthCheckService extends AbstractService {

    private static final System.Logger LOGGER = System.getLogger(HealthCheckService.class.getName());

    private final Config dbConfig;

    /**
     * Creates an instance of web resource to verify that health check works.
     *
     * @param dbClient   DbClient instance
     * @param dbConfig   testing application configuration
     * @param statements statements from configuration file
     */
    public HealthCheckService(DbClient dbClient, Config dbConfig, Map<String, String> statements) {
        super(dbClient, statements);
        this.dbConfig = dbConfig;
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/testHealthCheck", this::testHealthCheck)
                .get("/testHealthCheckWithName", this::testHealthCheckWithName)
                .get("/testHealthCheckWithCustomNamedDML", this::testHealthCheckWithCustomNamedDML)
                .get("/testHealthCheckWithCustomDML", this::testHealthCheckWithCustomDML)
                .get("/testHealthCheckWithCustomNamedQuery", this::testHealthCheckWithCustomNamedQuery)
                .get("/testHealthCheckWithCustomQuery", this::testHealthCheckWithCustomQuery);
    }

    private void executeTest(ServerResponse response, String testName, HealthCheck check) {
        LOGGER.log(Level.DEBUG, () -> String.format("Running HealthCheckService.%s on server", testName));
        JsonObjectBuilder job = Json.createObjectBuilder();
        HealthCheckResponse hcResponse = check.call();
        HealthCheckResponse.Status state = hcResponse.status();
        job.add("name", check.name());
        job.add("status", state.name());
        response.send(AppResponse.okStatus(job.build()));
    }

    // Verify health check implementation with default settings.
    private void testHealthCheck(ServerRequest request, ServerResponse response) {
        executeTest(response, "testHealthCheck",
                DbClientHealthCheck.create(
                        dbClient(),
                        dbConfig.get("health-check")));
    }

    // Verify health check implementation with builder and custom name.
    private void testHealthCheckWithName(ServerRequest request, ServerResponse response) {
        String name = queryParam(request, QueryParams.NAME);
        executeTest(response, "testHealthCheckWithName",
                DbClientHealthCheck.builder(dbClient())
                        .config(dbConfig.get("health-check"))
                        .name(name)
                        .build());
    }

    // Verify health check implementation using custom DML named statement.
    private void testHealthCheckWithCustomNamedDML(ServerRequest request, ServerResponse response) {
        Config cfgStatement = dbConfig.get("statements.ping-dml");
        if (!cfgStatement.exists()) {
            throw new RemoteTestException("Missing statements.ping-dml configuration parameter.");
        }
        executeTest(response, "testHealthCheckWithCustomNamedDML",
                DbClientHealthCheck.builder(dbClient())
                        .dml()
                        .statementName("ping-dml")
                        .build());
    }

    // Verify health check implementation using custom DML statement.
    private void testHealthCheckWithCustomDML(ServerRequest request, ServerResponse response) {
        Config cfgStatement = dbConfig.get("statements.ping-dml");
        if (!cfgStatement.exists()) {
            throw new RemoteTestException("Missing statements.ping-dml configuration parameter.");
        }
        executeTest(response, "testHealthCheckWithCustomDML",
                DbClientHealthCheck.builder(dbClient())
                        .dml()
                        .statement(cfgStatement.as(String.class).get())
                        .build());
    }

    // Verify health check implementation using custom query named statement.
    private void testHealthCheckWithCustomNamedQuery(ServerRequest request, ServerResponse response) {
        Config cfgStatement = dbConfig.get("statements.ping-query");
        if (!cfgStatement.exists()) {
            throw new RemoteTestException("Missing statements.ping-query configuration parameter.");
        }
        executeTest(response, "testHealthCheckWithCustomNamedQuery",
                DbClientHealthCheck.builder(dbClient())
                        .query()
                        .statementName("ping-query")
                        .build());
    }

    // Verify health check implementation using custom query statement.
    private void testHealthCheckWithCustomQuery(ServerRequest request, ServerResponse response) {
        Config cfgStatement = dbConfig.get("statements.ping-query");
        if (!cfgStatement.exists()) {
            throw new RemoteTestException("Missing statements.ping-query configuration parameter.");
        }
        executeTest(response, "testHealthCheckWithCustomQuery",
                DbClientHealthCheck.builder(dbClient())
                        .query()
                        .statement(cfgStatement.as(String.class).get())
                        .build());
    }
}
