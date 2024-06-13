/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.microprofile.grpc.server.GrpcMpCdiExtension;

/**
 * gRPC microprofile server module
 */
@Feature(value = "gRPC",
        description = "Helidon MP gRPC implementation",
        in = HelidonFlavor.MP,
        path = "gRPC"
)
@Aot(false)
module io.helidon.microprofile.grpc.server {
    exports io.helidon.microprofile.grpc.server;
    exports io.helidon.microprofile.grpc.server.spi;

    requires transitive io.helidon.grpc.core;
    requires transitive io.helidon.webserver.grpc;
    requires transitive io.helidon.microprofile.grpc.core;

    requires io.helidon.common;
    requires io.helidon.common.configurable;
    requires io.helidon.common.context;
    requires io.helidon.common.features.api;
    requires io.helidon.config.mp;
    requires io.helidon.config.objectmapping;
    requires io.helidon.config;
    requires io.helidon.config.metadata;
    requires io.helidon.microprofile.server;

    requires io.grpc;
    requires io.grpc.inprocess;
    requires io.grpc.protobuf.lite;
    requires com.google.protobuf;

    requires java.logging;

    requires microprofile.health.api;

    uses io.helidon.microprofile.grpc.server.spi.GrpcMpExtension;

    provides jakarta.enterprise.inject.spi.Extension with GrpcMpCdiExtension;

    // needed when running with modules - to make private methods accessible
    opens io.helidon.microprofile.grpc.server to weld.core.impl, io.helidon.microprofile.cdi;
}
