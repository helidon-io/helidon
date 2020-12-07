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
 */

/**
 * Neo4j health checks module.
 */
module io.helidon.integrations.neo4j.health {
    requires microprofile.health.api;
    requires org.neo4j.driver;

    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;

    exports io.helidon.integrations.neo4j.health;

    opens io.helidon.integrations.neo4j.health to weld.core.impl, io.helidon.microprofile.cdi;
}
