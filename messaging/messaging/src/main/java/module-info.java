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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon Reactive Messaging.
 */
@Features.Name("Messaging")
@Features.Description("Reactive messaging support")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path("Messaging")
@Features.Aot
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.messaging {

    requires io.helidon.common.configurable;
    requires io.helidon.common.context;
    requires io.helidon.config.mp;
    requires java.logging;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.config;
    requires transitive microprofile.config.api;
    requires transitive microprofile.reactive.messaging.api;
    requires transitive microprofile.reactive.streams.operators.api;
    requires transitive org.reactivestreams;

    exports io.helidon.messaging;

}
