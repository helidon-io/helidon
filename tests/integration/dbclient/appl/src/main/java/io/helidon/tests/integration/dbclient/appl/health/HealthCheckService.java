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
package io.helidon.tests.integration.dbclient.appl.health;

import java.util.Map;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to verify that health check works.
 */
public class HealthCheckService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(HealthCheckService.class.getName());

    private final Config dbConfig;

    /**
     * Creates an instance of web resource to verify that health check works.
     *
     * @param dbClient DbClient instance
     * @param dbConfig testing application configuration
     * @param statements statements from configuration file
     */
    public HealthCheckService(final DbClient dbClient, final Config dbConfig, final Map<String, String> statements) {
        super(dbClient, statements);
        this.dbConfig = dbConfig;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testHealthCheck", this::testHealthCheck)
                .get("/testHealthCheckWithName", this::testHealthCheckWithName)
                .get("/testHealthCheckWithCustomNamedDML", this::testHealthCheckWithCustomNamedDML)
                .get("/testHealthCheckWithCustomDML", this::testHealthCheckWithCustomDML)
                .get("/testHealthCheckWithCustomNamedQuery", this::testHealthCheckWithCustomNamedQuery)
                .get("/testHealthCheckWithCustomQuery", this::testHealthCheckWithCustomQuery);
    }

    /**
     * Background thread with health check verification, which is blocking operation.
     */
    private static final class HealthCheckThread implements Runnable {

        private final ServerRequest request;
        private final ServerResponse response;
        private final HealthCheck check;

        private HealthCheckThread(
                final ServerRequest request,
                final ServerResponse response,
                final HealthCheck check) {
            this.request = request;
            this.response = response;
            this.check = check;
        }

        @Override
        public void run() {
            try {
                JsonObjectBuilder job = Json.createObjectBuilder();
                HealthCheckResponse hcResponse = check.call();
                HealthCheckResponse.State state = hcResponse.getState();
                job.add("name", hcResponse.getName());
                job.add("status", state.name());
                response.send(AppResponse.okStatus(job.build()));
            } catch (Throwable t) {
                LOGGER.warning(() -> String.format(
                        "Error in HealthCheckService.testHealthCheck on server: %s",
                        t.getMessage()));
                response.send(exceptionStatus(t));
            }

        }
    }

    // Verify health check implementation with default settings.
    private void testHealthCheck(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running test HealthCheckService.testHealthCheck on server");
        Thread thread = new Thread(
                new HealthCheckThread(
                        request,
                        response,
                        DbClientHealthCheck.create(
                                dbClient(),
                                dbConfig.get("health-check"))
                ));
        thread.start();
    }

    // Verify health check implementation with builder and custom name.
    private void testHealthCheckWithName(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running test HealthCheckService.testHealthCheckWithName on server");
        final String name;
        try {
            name = param(request, QUERY_NAME_PARAM);
        } catch (RemoteTestException ex) {
            LOGGER.fine(() -> String.format(
                    "Error in HealthCheckService.testHealthCheckWithName on server: %s",
                    ex.getMessage()));
            response.send(exceptionStatus(ex));
            return;
        }
        Thread thread = new Thread(
                new HealthCheckThread(
                        request,
                        response,
                        DbClientHealthCheck.builder(dbClient())
                                .config(dbConfig.get("health-check"))
                                .name(name)
                                .build()
                ));
        thread.start();
    }

    // Verify health check implementation using custom DML named statement.
    private void testHealthCheckWithCustomNamedDML(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running test HealthCheckService.testHealthCheckWithCustomNamedDML on server");
        Config cfgStatement = dbConfig.get("statements.ping-dml");
        if (!cfgStatement.exists()) {
            response.send(exceptionStatus(new RemoteTestException("Missing statements.ping-dml configuration parameter.")));
            return;
        }
        Thread thread = new Thread(
                new HealthCheckThread(
                        request,
                        response,
                        DbClientHealthCheck.builder(dbClient())
                                .dml()
                                .statementName("ping-dml")
                                .build()
                ));
        thread.start();
    }

    // Verify health check implementation using custom DML statement.
    private void testHealthCheckWithCustomDML(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running test HealthCheckService.testHealthCheckWithCustomDML on server");
        Config cfgStatement = dbConfig.get("statements.ping-dml");
        if (!cfgStatement.exists()) {
            response.send(exceptionStatus(new RemoteTestException("Missing statements.ping-dml configuration parameter.")));
            return;
        }
        Thread thread = new Thread(
                new HealthCheckThread(
                        request,
                        response,
                        DbClientHealthCheck.builder(dbClient())
                                .dml()
                                .statement(cfgStatement.as(String.class).get())
                                .build()
                ));
        thread.start();
    }

    // Verify health check implementation using custom query named statement.
    private void testHealthCheckWithCustomNamedQuery(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running test HealthCheckService.testHealthCheckWithCustomNamedQuery on server");
        Config cfgStatement = dbConfig.get("statements.ping-query");
        if (!cfgStatement.exists()) {
            response.send(exceptionStatus(new RemoteTestException("Missing statements.ping-query configuration parameter.")));
            return;
        }
        Thread thread = new Thread(
                new HealthCheckThread(
                        request,
                        response,
                        DbClientHealthCheck.builder(dbClient())
                                .query()
                                .statementName("ping-query")
                                .build()
                ));
        thread.start();
    }

    // Verify health check implementation using custom query statement.
    private void testHealthCheckWithCustomQuery(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running test HealthCheckService.testHealthCheckWithCustomQuery on server");
        Config cfgStatement = dbConfig.get("statements.ping-query");
        if (!cfgStatement.exists()) {
            response.send(exceptionStatus(new RemoteTestException("Missing statements.ping-query configuration parameter.")));
            return;
        }
        Thread thread = new Thread(
                new HealthCheckThread(
                        request,
                        response,
                        DbClientHealthCheck.builder(dbClient())
                                .query()
                                .statement(cfgStatement.as(String.class).get())
                                .build()
                ));
        thread.start();
    }

}
