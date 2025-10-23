/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * Neo4j health checks module.
 */
@Features.Preview
@Features.Name("Neo4j Health")
@Features.Description("Health check for Neo4j integration")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"Neo4j", "Health"})
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.neo4j.health {
    requires static io.helidon.common.features.api;

    requires transitive io.helidon.health;
    requires transitive org.neo4j.driver;

    exports io.helidon.integrations.neo4j.health;

    opens io.helidon.integrations.neo4j.health;

}
