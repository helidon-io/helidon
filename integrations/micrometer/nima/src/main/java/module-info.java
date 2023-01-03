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
import io.helidon.common.features.api.Preview;

/**
 * Support for Micrometer Nima in Helidon SE.
 */
@Preview
@Feature(value = "Micrometer",
        description = "Micrometer integration",
        in = HelidonFlavor.MP,
        path = "Micrometer"
)
module io.helidon.integrations.micrometer.nima {
    requires static io.helidon.common.features.api;

    requires java.logging;

    requires static jakarta.annotation;

    requires io.helidon.config;
    requires io.helidon.common.http;
    requires io.helidon.integrations.micrometer;
    requires io.helidon.nima.servicecommon;
    requires io.helidon.nima.webserver.cors;

    requires static io.helidon.config.metadata;

    requires micrometer.core;
    requires micrometer.registry.prometheus;
    requires simpleclient;

    exports io.helidon.integrations.micrometer.nima;

}
