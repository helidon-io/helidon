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

import io.helidon.integrations.neo4j.Neo4jCdiExtension;

/**
 * Neo4j support module.
 */
module io.helidon.integrations.neo4j {
    requires java.logging;
    requires java.management;

    requires io.helidon.common;
    requires io.helidon.config;

    requires org.neo4j.driver;

    // MicroProfile dependencies are all optional (when used in SE)
    requires static jakarta.enterprise.cdi.api;
    requires static microprofile.config.api;
    requires static io.helidon.config.mp;

    exports io.helidon.integrations.neo4j;

    opens io.helidon.integrations.neo4j to weld.core.impl, io.helidon.microprofile.cdi;

    provides javax.enterprise.inject.spi.Extension with Neo4jCdiExtension;
}
