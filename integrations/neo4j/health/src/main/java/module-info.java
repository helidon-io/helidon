/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Neo4j health checks module.
 */
module io.helidon.integrations.neo4j.health {
    requires java.logging;
    requires java.management;

    requires io.helidon.common;
    requires io.helidon.common.serviceloader;
    requires io.helidon.health;

    requires org.neo4j.driver;

    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject.api;
    requires java.json;
    requires microprofile.config.api;
    requires io.helidon.config.mp;

    exports io.helidon.integrations.neo4j.health;
}
