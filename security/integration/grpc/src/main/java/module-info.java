/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
 * Security integration with Helidon gRPC.
 */
@Feature(value = "gRPC",
        description = "Security integration with gRPC",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Security", "Integration", "gRPC"}
)
module io.helidon.security.integration.grpc {
    requires static io.helidon.common.features.api;

    exports io.helidon.security.integration.grpc;

    requires io.helidon.common;
    requires io.helidon.common.context;
    requires transitive io.helidon.grpc.core;
    requires static io.helidon.grpc.server;
    requires transitive io.helidon.security;
    requires transitive io.helidon.security.integration.common;
    requires io.helidon.tracing;
    requires java.logging;
    requires io.helidon.reactive.webserver;
    requires io.grpc;
}
