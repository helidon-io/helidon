/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
 * Helidon WebServer HTTP/3 support.
 */
@Features.Name("HTTP/3")
@Features.Description("WebServer HTTP/3 support")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"WebServer", "HTTP/3"})
@Features.Incubating
module io.helidon.webserver.http3 {
    requires static io.helidon.common.features.api;
    requires transitive io.helidon.builder.api;

    requires static io.helidon.config.metadata;

    requires io.helidon.common;
    requires io.helidon.common.buffers;
    requires io.helidon.common.uri;
    requires io.helidon.http.http3;
    requires transitive io.helidon.webserver.quic;
    requires transitive io.helidon.webserver;

    exports io.helidon.webserver.http3;

    provides io.helidon.webserver.spi.ProtocolConfigProvider
            with io.helidon.webserver.http3.Http3QuicProtocolProvider;
    provides io.helidon.webserver.quic.spi.QuicSubProtocolProvider
            with io.helidon.webserver.http3.Http3QuicProtocolProvider;

    uses io.helidon.webserver.http.spi.SinkProvider;
}
