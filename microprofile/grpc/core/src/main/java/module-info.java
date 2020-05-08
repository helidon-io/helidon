/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

/**
 * gRPC microprofile core module
 */
module io.helidon.microprofile.grpc.core {
    exports io.helidon.microprofile.grpc.core;

    requires transitive io.helidon.grpc.core;
    requires transitive io.helidon.grpc.client;
    requires transitive io.helidon.microprofile.config;
    requires io.helidon.common.serviceloader;

    requires transitive jakarta.enterprise.cdi.api;

    requires java.logging;
    requires jakarta.inject.api;

    uses io.helidon.microprofile.grpc.core.MethodHandlerSupplier;

    provides io.helidon.microprofile.grpc.core.MethodHandlerSupplier
            with io.helidon.microprofile.grpc.core.BidirectionalMethodHandlerSupplier,
            io.helidon.microprofile.grpc.core.ClientStreamingMethodHandlerSupplier,
            io.helidon.microprofile.grpc.core.ServerStreamingMethodHandlerSupplier,
            io.helidon.microprofile.grpc.core.UnaryMethodHandlerSupplier;
}