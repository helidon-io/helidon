/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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
 * Opentracing support for helidon, with an abstraction API and SPI for tracing collectors.
 *
 * @see io.helidon.tracing.spi.TracerProvider
 * @see io.helidon.tracing.TracerBuilder
 */
@Feature(value = "Tracing",
        description = "Tracing support",
        in = HelidonFlavor.SE
)
module io.helidon.tracing {

    requires io.helidon.common;

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.common.config;

    exports io.helidon.tracing;
    exports io.helidon.tracing.spi;

    uses io.helidon.tracing.spi.TracerProvider;
    uses io.helidon.tracing.SpanListener;

}
