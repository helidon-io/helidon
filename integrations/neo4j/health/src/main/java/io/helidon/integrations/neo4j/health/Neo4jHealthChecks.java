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

import org.eclipse.microprofile.health.HealthCheck;
import org.neo4j.driver.Driver;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Health support module for Neo4j.
 *
 * @author Dmitry Aleksandrov
 * @author Tim Quinn
 */
public class Neo4jHealthChecks {

    private final Driver driver;
    private final Collection<HealthCheck> readinessChecks;
    private final Collection<HealthCheck> livenessChecks = Collections.emptySet();

    public static Builder builder() {
        return new Builder();
    }

    private Neo4jHealthChecks(Builder builder) {
        this.driver = builder.driver;
        readinessChecks = Set.of(Neo4jHealthCheck.create(driver));
    }

    public Collection<HealthCheck> readinessChecks() {
        return readinessChecks;
    }

    public Collection<HealthCheck> livenessChecks() {
        return livenessChecks;
    }

    public static class Builder implements io.helidon.common.Builder<Neo4jHealthChecks> {

        private Driver driver;

        private Builder() {
        }

        public Neo4jHealthChecks build() {
            Objects.requireNonNull(driver, "Must set driver before building");
            return new Neo4jHealthChecks(this);
        }

        public Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }
    }
}
