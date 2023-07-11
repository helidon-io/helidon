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
 * Fault tolerance module for Helidon Níma.
 */
@Feature(value = "Fault Tolerance",
         description = "Fault Tolerance support",
         in = HelidonFlavor.NIMA,
         path = "FT"
)
module io.helidon.nima.faulttolerance {
    requires io.helidon.common;
    requires io.helidon.common.types;
    requires io.helidon.common.configurable;
    requires io.helidon.config;
    requires io.helidon.inject.api;
    requires io.helidon.builder.api;

    requires static jakarta.inject;
    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;
    requires static io.helidon.inject.configdriven.api;
    // needed to compile generated types
    requires static io.helidon.inject.configdriven.runtime;
    requires static io.helidon.inject.runtime;


    exports io.helidon.nima.faulttolerance;

    // inject module
    provides io.helidon.inject.api.ModuleComponent with io.helidon.nima.faulttolerance.Injection$$Module;
}
