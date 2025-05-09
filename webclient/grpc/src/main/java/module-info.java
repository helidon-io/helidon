/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
 * Helidon WebClient gRPC Support.
 */
@Feature(value = "gRPC",
         description = "WebClient gRPC support",
         in = HelidonFlavor.SE,
         path = {"WebClient", "gRPC"}
)
module io.helidon.webclient.grpc {

    requires static io.helidon.common.features.api;

    requires transitive io.grpc;
    requires transitive io.grpc.stub;
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.webclient.http2;
    requires transitive io.helidon.webclient;

    requires io.helidon.metrics.api;
    requires io.helidon.grpc.core;

    exports io.helidon.webclient.grpc;

    provides io.helidon.webclient.spi.ClientProtocolProvider
            with io.helidon.webclient.grpc.GrpcProtocolProvider;
}