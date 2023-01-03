/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.nima.observe.health.HealthObserveProvider;
import io.helidon.nima.observe.spi.ObserveProvider;

/**
 * Health checks for Níma observability.
 */
@Feature(value = "Health", description = "Health check support", in = HelidonFlavor.NIMA)
module io.helidon.nima.observe.health {
    requires java.management;

    requires transitive io.helidon.health;
    requires transitive io.helidon.nima.observe;
    requires io.helidon.nima.webserver;
    requires io.helidon.nima.http.media.jsonp;
    requires io.helidon.nima.servicecommon;
    requires static io.helidon.common.features.api;

    exports io.helidon.nima.observe.health;

    provides ObserveProvider with HealthObserveProvider;

    uses HealthCheckProvider;
}