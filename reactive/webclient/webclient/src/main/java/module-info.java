/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
 * Helidon WebClient.
 */
@Feature(value = "Web Client",
        description = "Reactive web client",
        in = HelidonFlavor.SE,
        path = "Web Client"
)
module io.helidon.reactive.webclient {
    requires static io.helidon.common.features.api;

    requires java.logging;
    
    requires transitive io.helidon.common;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.common.http;
    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.config;
    requires transitive io.helidon.reactive.media.common;
    requires transitive io.helidon.common.parameters;

    requires io.helidon.common.pki;

    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.codec.http;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.handler.proxy;
    requires io.netty.transport;

    requires static io.helidon.config.metadata;

    exports io.helidon.reactive.webclient;
    exports io.helidon.reactive.webclient.spi;

    uses io.helidon.reactive.webclient.spi.WebClientServiceProvider;
    uses io.helidon.common.context.spi.DataPropagationProvider;

}
