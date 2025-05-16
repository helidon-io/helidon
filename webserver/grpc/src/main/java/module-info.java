/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import io.helidon.common.features.api.Preview;

/**
 * Helidon WebServer gRPC Support.
 */
@Preview
@Feature(value = "GRPC",
         description = "WebServer gRPC Support",
         in = HelidonFlavor.SE,
         path = {"WebServer", "GRPC"}
)
@SuppressWarnings({ "requires-automatic"})
module io.helidon.webserver.grpc {

    requires io.helidon.builder.api;
    requires io.helidon.webserver.http2;
    requires io.helidon.tracing;
    requires io.helidon.common.config;

    requires io.grpc;
    requires io.grpc.stub;
    requires com.google.protobuf;

    requires transitive io.helidon.grpc.core;

    requires static io.helidon.common.features.api;

    exports io.helidon.webserver.grpc;

    provides io.helidon.webserver.http2.spi.Http2SubProtocolProvider
            with io.helidon.webserver.grpc.GrpcProtocolProvider;
    provides io.helidon.webserver.spi.ProtocolConfigProvider
            with io.helidon.webserver.grpc.GrpcProtocolConfigProvider;
}
