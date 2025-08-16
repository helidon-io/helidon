/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

/**
 * Helidon WebServer Tracing Support.
 */
@Feature(value = "Tracing",
         description = "WebServer Tracing support",
         in = HelidonFlavor.SE
)
module io.helidon.webserver.observe.tracing {

    requires io.helidon.http;
    requires io.helidon.tracing.config;
    requires io.helidon.tracing;
    requires io.helidon.webserver;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.webserver.observe;
    requires transitive io.helidon.config;
    requires io.helidon.service.registry;

    provides io.helidon.webserver.observe.spi.ObserveProvider
            with io.helidon.webserver.observe.tracing.TracingObserveProvider;

    exports io.helidon.webserver.observe.tracing;
    exports io.helidon.webserver.observe.tracing.spi;
}