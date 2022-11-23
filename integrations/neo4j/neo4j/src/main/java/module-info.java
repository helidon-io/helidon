/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Incubating;

/**
 * Neo4j support module.
 */
@Incubating
@Feature(value = "Neo4j integration",
        description = "Integration with Neo4j driver",
        in = {HelidonFlavor.MP, HelidonFlavor.SE, HelidonFlavor.NIMA},
        path = "Neo4j"
)
module io.helidon.integrations.neo4j {
    requires static io.helidon.common.features.api;

    requires java.logging;

    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires static jakarta.interceptor.api;
    requires static io.helidon.config;
    requires static io.helidon.config.mp;

    requires org.neo4j.driver;

    exports io.helidon.integrations.neo4j;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.integrations.neo4j.Neo4jCdiExtension;
}
