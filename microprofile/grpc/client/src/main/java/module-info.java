/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

/**
 * gRPC client implementation for Helidon MP.
 */
@Feature(value = "gRPC Client",
        description = "Client for gRPC services",
        in = HelidonFlavor.MP,
        path = "grpcClient"
)
@Aot(false)
module io.helidon.microprofile.grpc.client {
    requires static io.helidon.common.features.api;

    requires jakarta.cdi;
    requires jakarta.inject;

    requires transitive io.helidon.microprofile.grpc.core;

    exports io.helidon.microprofile.grpc.client;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.microprofile.grpc.client.GrpcClientCdiExtension;

    // needed when running with modules - to make private methods accessible
    opens io.helidon.microprofile.grpc.client to weld.core.impl, io.helidon.microprofile.cdi;
}