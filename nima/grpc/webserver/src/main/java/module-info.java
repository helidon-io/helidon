/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import io.helidon.nima.grpc.webserver.GrpcProtocolProvider;

/**
 * Helidon Níma gRPC server.
 */
@Preview
@Feature(value = "GRPC",
        description = "gRPC Support",
        in = HelidonFlavor.NIMA,
        invalidIn = HelidonFlavor.SE,
        path = {"GRPC", "WebServer"}
)
module io.helidon.nima.grpc.server {
    requires static io.helidon.common.features.api;

    requires java.logging;

    requires io.helidon.nima.http2.webserver;

    requires transitive grpc.stub;
    requires transitive com.google.protobuf;
    requires transitive io.grpc;
    requires grpc.protobuf.lite;

    exports io.helidon.nima.grpc.webserver;

    provides io.helidon.nima.http2.webserver.spi.Http2SubProtocolProvider with GrpcProtocolProvider;
}
