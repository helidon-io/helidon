/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

/**
 * Helidon health checks.
 */
@Features.Name("Built-ins")
@Features.Description("Built in health checks")
@Features.Flavor({HelidonFlavor.MP, HelidonFlavor.SE})
@Features.Path({"Health", "Builtins"})
module io.helidon.health.checks {
    requires static io.helidon.common.features.api;

    requires java.management;

    requires static jakarta.cdi;
    requires static jakarta.inject;

    requires io.helidon.common;
    requires io.helidon.common.config;
    requires transitive io.helidon.health;

    exports io.helidon.health.checks;

    provides io.helidon.health.spi.HealthCheckProvider with io.helidon.health.checks.BuiltInHealthCheckProvider;
	
}
