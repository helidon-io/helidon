/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import io.helidon.health.common.BuiltInHealthCheck;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;


/**
 * Health support module for Neo4j. Follows the standard MicroProfile HealthCheck pattern.
 */
@Readiness
@ApplicationScoped
@BuiltInHealthCheck
public class Neo4jHealthCheck implements HealthCheck {

    /**
     * The Cypher statement used to verify Neo4j is up.
     */
    static final String CYPHER = "CALL dbms.components() YIELD name, edition WHERE name = 'Neo4j Kernel' RETURN edition";

    private final Driver driver;

    /**
     * Constructor for Health checks.
     *
     * @param driver Neo4j.
     */
    @Inject //will be ignored out of CDI
    Neo4jHealthCheck(Driver driver) {
        this.driver = driver;
    }

    /**
     * Creates the Neo4j driver.
     *
     * @param driver Neo4j.
     * @return Neo4jHealthCheck.
     */
    public static Neo4jHealthCheck create(Driver driver) {
        return new Neo4jHealthCheck(driver);
    }

    private HealthCheckResponse runHealthCheckQuery(HealthCheckResponseBuilder builder) {

        try (Session session = this.driver.session()) {

            return session.writeTransaction(tx -> {
                var result = tx.run(CYPHER);

                var edition = result.single().get("edition").asString();
                var resultSummary = result.consume();
                var serverInfo = resultSummary.server();

                var responseBuilder = builder
                        .withData("server", serverInfo.version() + "@" + serverInfo.address())
                        .withData("edition", edition);

                var databaseInfo = resultSummary.database();
                if (!databaseInfo.name().trim().isBlank()) {
                    responseBuilder.withData("database", databaseInfo.name().trim());
                }

                return responseBuilder.up().build();
            });
        }
    }

    @Override
    public HealthCheckResponse call() {

        var builder = HealthCheckResponse.named("Neo4j connection health check");
        try {
            return runHealthCheckQuery(builder);
        } catch (Exception ex) {
            return builder.down().withData("reason", ex.getMessage()).build();
        }
    }
}
