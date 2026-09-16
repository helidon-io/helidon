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
 * Standalone QUIC transport and application-stream API.
 */
@SuppressWarnings("module")
@Features.Name("QUIC")
@Features.Description("QUIC transport support")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path("QUIC")
@Features.Incubating
module io.helidon.quic {
    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.builder.api;
    requires io.helidon.common;
    requires transitive io.helidon.common.buffers;
    requires transitive io.helidon.common.socket;
    requires transitive io.helidon.config;
    requires java.management;
    requires java.naming;
    requires transitive io.helidon.common.tls;

    exports io.helidon.quic;
    exports io.helidon.quic.stream to
            io.helidon.http.http3,
            io.helidon.webclient.http3,
            io.helidon.webserver.http3,
            io.helidon.webserver.quic,
            io.helidon.webserver.testing.junit5.http3;
}
