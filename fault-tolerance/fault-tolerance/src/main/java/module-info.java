/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
 * Helidon Fault Tolerance Support.
 */
@Feature(value = "Fault Tolerance",
         description = "Fault Tolerance support",
         in = HelidonFlavor.SE,
         path = "FT"
)
module io.helidon.faulttolerance {

    requires io.helidon.common;
    requires io.helidon.common.configurable;
    requires io.helidon.common.config;
    requires io.helidon.builder.api;
    requires io.helidon.metrics.api;
    requires io.helidon.service.registry;

    requires static io.helidon.common.features.api;

    exports io.helidon.faulttolerance;
}
