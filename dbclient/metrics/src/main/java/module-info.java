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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.dbclient.metrics.DbClientMetricsProvider;
import io.helidon.dbclient.spi.DbClientServiceProvider;

/**
 * Helidon DB Client Metrics.
 */
@Feature(value = "Metrics",
         description = "Database client metrics support",
         in = HelidonFlavor.SE,
         path = {"DbClient", "Metrics"}
)
module io.helidon.dbclient.metrics {

    requires static io.helidon.common.features.api;
    requires io.helidon.dbclient;
    requires io.helidon.dbclient.common;
    requires io.helidon.metrics.api;

    exports io.helidon.dbclient.metrics;

    provides DbClientServiceProvider with DbClientMetricsProvider;

}