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
package io.helidon.integrations.neo4j.health;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.microprofile.health.BuiltInHealthCheck;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Readiness;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

@Readiness
@ApplicationScoped
@BuiltInHealthCheck
public class Neo4jHelidonHealthCheck implements HealthCheck {

    private final String name = "Neo4j connection health check";
    private final Driver driver;
    /**
     * The Cypher statement used to verify Neo4j is up.
     */
    static final String CYPHER = "CALL dbms.components() YIELD name, edition WHERE name = 'Neo4j Kernel' RETURN edition";

    /**
     * Constructor for Health checks.
     *
     * @param driver Neo4j.
     */
    @Inject
    public Neo4jHelidonHealthCheck(Driver driver) {
        this.driver = driver;
    }

    /**
     * Creates the Neo4j driver.
     *
     * @param driver Neo4j.
     * @return Neo4jHelidonHealthCheck.
     */
    public static Neo4jHelidonHealthCheck create(Driver driver) {
        return new Neo4jHelidonHealthCheck(driver);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public HealthCheckResponse call() {
        try (Session session = this.driver.session()) {
            HealthCheckResponse.Builder builder = HealthCheckResponse.builder();
            return session.writeTransaction(tx -> {
                var result = tx.run(CYPHER);

                var edition = result.single().get("edition").asString();
                var resultSummary = result.consume();
                var serverInfo = resultSummary.server();

                var responseBuilder = builder
                        .detail("server", serverInfo.version() + "@" + serverInfo.address())
                        .detail("edition", edition);

                var databaseInfo = resultSummary.database();
                if (!databaseInfo.name().trim().isBlank()) {
                    responseBuilder.detail("database", databaseInfo.name().trim());
                }

                return responseBuilder.status(HealthCheckResponse.Status.UP).build();
            });
        }
    }

}
