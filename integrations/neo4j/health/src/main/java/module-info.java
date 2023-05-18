/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
import io.helidon.common.features.api.Preview;

/**
 * Neo4j health checks module.
 */
@Preview
@Feature(value = "Neo4j Health",
        description = "Health check for Neo4j integration",
        in = {HelidonFlavor.SE, HelidonFlavor.MP, HelidonFlavor.NIMA},
        path = {"Neo4j", "Health"}
)
module io.helidon.integrations.neo4j.health {
    requires static io.helidon.common.features.api;

    requires microprofile.health.api;
    requires org.neo4j.driver;

    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires static io.helidon.microprofile.health;
    requires io.helidon.health;

    exports io.helidon.integrations.neo4j.health;

    opens io.helidon.integrations.neo4j.health to weld.core.impl, io.helidon.microprofile.cdi;
}
