/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
import io.helidon.reactive.webserver.spi.UpgradeCodecProvider;

/**
 * Reactive web server.
 */
@Feature(value = "WebServer",
         description = "Reactive Web Server",
         in = HelidonFlavor.SE,
         invalidIn = {HelidonFlavor.NIMA, HelidonFlavor.MP}
)
module io.helidon.reactive.webserver {
    requires io.helidon.common;
    requires transitive io.helidon.reactive.media.common;
    requires transitive io.helidon.common.http;
    requires io.helidon.common.mapper;
    requires transitive io.helidon.common.pki;
    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.config;
    requires transitive io.helidon.tracing.config;
    requires transitive io.helidon.tracing;
    requires io.helidon.logging.common;
    requires io.helidon.common.features;
    requires io.helidon.common.features.api;
    requires static io.helidon.config.metadata;


    requires java.logging;
    requires io.netty.handler;
    requires io.netty.codec.http;
    requires io.netty.codec;
    requires io.netty.transport;
    requires io.netty.common;
    requires io.netty.buffer;

    exports io.helidon.reactive.webserver;
    exports io.helidon.reactive.webserver.spi;

    uses UpgradeCodecProvider;
}
