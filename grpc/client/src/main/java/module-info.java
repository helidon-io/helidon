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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * gRPC Client Module.
 */
@Feature(value = "gRPC Client", description = "Client for gRPC services",
        in = HelidonFlavor.SE,
        path = "grpcClient")
@Aot(description = "Experimental support in native image")
module io.helidon.grpc.client {
    requires static io.helidon.common.features.api;

    exports io.helidon.grpc.client;

    requires transitive io.helidon.grpc.core;

    requires io.helidon.tracing;

    requires static io.helidon.config.metadata;
}
