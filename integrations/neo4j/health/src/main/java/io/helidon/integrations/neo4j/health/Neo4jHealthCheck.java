/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.integrations.neo4j.health;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.SessionExpiredException;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.ServerInfo;

/**
 * Health support module for Neo4j. Follows the standard MicroProfile HealthCheck pattern.
 *
 * Implements {@link org.eclipse.microprofile.health.HealthCheck}
 * Created by Dmitry Alexandrov on 18.11.20.
 */
@Readiness
@ApplicationScoped
public class Neo4jHealthCheck implements HealthCheck {

    /**
     * The Cypher statement used to verify Neo4j is up.
     */
    private static final String CYPHER = "RETURN 1 AS result";
    /**
     * Message indicating that the health check failed.
     */
    private static final String MESSAGE_HEALTH_CHECK_FAILED = "Neo4j health check failed";
    /**
     * Message logged before retrying a health check.
     */
    private static final String MESSAGE_SESSION_EXPIRED = "Neo4j session has expired, retrying one single time to retrieve "
            + "server health.";
    /**
     * The default session config to use while connecting.
     */
    private static final SessionConfig DEFAULT_SESSION_CONFIG = SessionConfig.builder()
            .withDefaultAccessMode(AccessMode.WRITE)
            .build();

    private Driver driver;

    @Inject
        //will be ignored outside of CDI
    Neo4jHealthCheck(Driver driver) {
        this.driver = driver;
    }

    /**
     * To be used in SE context
     * @param driver
     * @return
     */
    public static Neo4jHealthCheck create(Driver driver) {
        return new Neo4jHealthCheck(driver);
    }

    /**
     * Applies the given {@link ResultSummary} to the {@link HealthCheckResponseBuilder builder} and calls {@code build}
     * afterwards.
     *
     * @param resultSummary the result summary returned by the server
     * @param builder the health builder to be modified
     * @return the final {@link HealthCheckResponse health check response}
     */
    private static HealthCheckResponse buildStatusUp(ResultSummary resultSummary, HealthCheckResponseBuilder builder) {
        ServerInfo serverInfo = resultSummary.server();

        builder.withData("server", serverInfo.version() + "@" + serverInfo.address());

        String databaseName = resultSummary.database().name();
        if (!(databaseName == null || databaseName.trim().isEmpty())) {
            builder.withData("database", databaseName.trim());
        }

        return builder.build();
    }

    @Override
    public HealthCheckResponse call() {

        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Neo4j connection health check").up();
        try {
            ResultSummary resultSummary;
            // Retry one time when the session has been expired
            try {
                resultSummary = runHealthCheckQuery();
            } catch (SessionExpiredException sessionExpiredException) {
                resultSummary = runHealthCheckQuery();
            }
            return buildStatusUp(resultSummary, builder);
        } catch (Exception e) {
            return builder.down().withData("reason", e.getMessage()).build();
        }
    }

    private ResultSummary runHealthCheckQuery() {
        // We use WRITE here to make sure UP is returned for a server that supports
        // all possible workloads
        try (Session session = this.driver.session(DEFAULT_SESSION_CONFIG)) {
            ResultSummary resultSummary = session.run(CYPHER).consume();
            return resultSummary;
        }
    }
}