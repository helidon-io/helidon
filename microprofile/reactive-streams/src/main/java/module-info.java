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
 * MicroProfile Reactive Streams Operators implementation.
 *
 * @see org.eclipse.microprofile.reactive.streams.operators
 */
@Feature(value = "Reactive",
        description = "MicroProfile Reactive Stream operators",
        in = HelidonFlavor.MP,
        path = "Reactive"
)
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.microprofile.reactive {

    requires io.helidon.common.reactive;
    requires java.logging;

    requires static io.helidon.common.features.api;

    requires transitive microprofile.reactive.streams.operators.api;
    requires transitive microprofile.reactive.streams.operators.core;
    requires transitive org.reactivestreams;

    exports io.helidon.microprofile.reactive;

    provides org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine
            with io.helidon.microprofile.reactive.HelidonReactiveStreamsEngine;

}