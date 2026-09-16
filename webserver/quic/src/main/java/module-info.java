/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
 * Helidon WebServer QUIC support.
 */
@Features.Name("QUIC")
@Features.Description("WebServer QUIC support")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"WebServer", "QUIC"})
@Features.Incubating
module io.helidon.webserver.quic {
    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires io.helidon.builder.api;
    requires io.helidon.common;
    requires transitive io.helidon.common.tls;
    requires io.helidon.config;
    requires io.helidon.http;
    requires transitive io.helidon.quic;
    requires transitive io.helidon.webserver;

    exports io.helidon.webserver.quic;
    exports io.helidon.webserver.quic.spi;

    uses io.helidon.webserver.quic.spi.QuicSubProtocolProvider;

    provides io.helidon.webserver.spi.TransportBindingFactoryProvider
            with io.helidon.webserver.quic.QuicTransportBindingProvider;
}
