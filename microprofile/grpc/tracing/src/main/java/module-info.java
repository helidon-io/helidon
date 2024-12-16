/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

@Feature(value = "gRPC Tracing",
         description = "Helidon gRPC MP Tracing",
         in = HelidonFlavor.MP,
         path = {"gRPC", "Tracing"}
)
module helidon.microprofile.grpc.tracing {

    requires io.helidon.config;
    requires io.helidon.config.mp;
    requires io.helidon.webserver.grpc;
    requires io.helidon.microprofile.grpc.server;
    requires io.helidon.tracing;
    requires io.helidon.common.features.api;

    provides io.helidon.microprofile.grpc.server.spi.GrpcMpExtension
            with io.helidon.microprofile.grpc.tracing.GrpcMpTracingExtension;
}