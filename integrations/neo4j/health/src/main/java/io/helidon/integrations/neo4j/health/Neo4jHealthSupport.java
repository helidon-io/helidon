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

import io.helidon.health.HealthSupport;
import io.helidon.integrations.neo4j.Neo4jHelper;
import io.helidon.webserver.Routing;

import org.neo4j.driver.Driver;

/**
 * Health support module for Neo4j.
 *
 * Implements {@link io.helidon.integrations.neo4j.Neo4jHelper}
 *
 * Created by Dmitry Alexandrov on 18.11.20.
 */
public class Neo4jHealthSupport implements Neo4jHelper {

    private Neo4jHealthSupport() {
        //private constructor
    }

    public static Neo4jHealthSupport create() {
        return new Neo4jHealthSupport();
    }

    @Override
    public void init(Driver driver) {

        //create health for neo4j
        HealthSupport health = HealthSupport.builder()
                .addLiveness(Neo4jHealthCheck.create(driver))
                .build();

        //register
        Routing.builder()
                .register(health)
                .build();
    }
}
