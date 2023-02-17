/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
 * Fault tolerance module for Helidon reactive implementation.
 */
@Feature(value = "Fault Tolerance",
        description = "Fault Tolerance",
        in = HelidonFlavor.SE,
        invalidIn = HelidonFlavor.NIMA,
        path = "Fault Tolerance"
)
module io.helidon.reactive.faulttolerance {
    requires static io.helidon.common.features.api;

    requires io.helidon.config;
    requires io.helidon.common.configurable;
    requires transitive io.helidon.common.reactive;
    requires static io.helidon.config.metadata;

    exports io.helidon.reactive.faulttolerance;
}
