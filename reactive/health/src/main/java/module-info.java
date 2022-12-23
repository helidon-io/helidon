/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
 * Microprofile configuration module.
 */
@Feature(value = "Health",
        description = "Health check support",
        in = HelidonFlavor.SE
)
module io.helidon.reactive.health {
    requires java.logging;

    requires io.helidon.common;
    requires io.helidon.health;
    requires transitive microprofile.health.api;
    requires io.helidon.reactive.webserver;
    requires io.helidon.reactive.servicecommon;
    requires io.helidon.reactive.webserver.cors;
    requires io.helidon.reactive.media.jsonp;
    requires jakarta.json;
    requires io.helidon.reactive.faulttolerance;
    requires static io.helidon.config.metadata;
    requires static io.helidon.common.features.api;

    exports io.helidon.reactive.health;
    provides org.eclipse.microprofile.health.spi.HealthCheckResponseProvider
            with io.helidon.reactive.health.HealthCheckResponseProviderImpl;
}
