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
 * Time attribute validator.
 */
@Features.Name("Time")
@Features.Description("ABAC Time based attribute validator")
@Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
@Features.Path({"Security", "Provider", "ABAC", "Time"})
module io.helidon.security.abac.time {

    requires io.helidon.security.providers.abac;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.common.config;
    requires transitive io.helidon.security;

    exports io.helidon.security.abac.time;

    provides io.helidon.security.providers.abac.spi.AbacValidatorService
            with io.helidon.security.abac.time.TimeValidatorService;

}
