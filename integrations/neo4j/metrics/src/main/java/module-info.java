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
 * Neo4j metrics support module.
 */
@Preview
@Feature(value = "Neo4j Metrics",
        description = "Metrics for Neo4j integration",
        in = {HelidonFlavor.MP, HelidonFlavor.SE},
        path = {"Neo4j", "Metrics"}
)
module io.helidon.integrations.neo4j.metrics {
    requires static io.helidon.common.features.api;

    requires io.helidon.common;
    requires io.helidon.integrations.neo4j;
    requires io.helidon.metrics;

    requires org.neo4j.driver;
    requires microprofile.metrics.api;

    exports io.helidon.integrations.neo4j.metrics;
}