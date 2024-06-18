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

/**
 * gRPC microprofile core module
 */
module io.helidon.microprofile.grpc.core {
    exports io.helidon.microprofile.grpc.core;

    requires io.helidon.common;
    requires transitive io.helidon.grpc.core;
    requires transitive io.helidon.microprofile.config;

    requires transitive jakarta.cdi;

    requires java.logging;
    requires jakarta.inject;

    uses io.helidon.microprofile.grpc.core.MethodHandlerSupplier;
    uses io.helidon.grpc.core.MarshallerSupplier;

    provides io.helidon.microprofile.grpc.core.MethodHandlerSupplier
            with io.helidon.microprofile.grpc.core.BidirectionalMethodHandlerSupplier,
            io.helidon.microprofile.grpc.core.ClientStreamingMethodHandlerSupplier,
            io.helidon.microprofile.grpc.core.ServerStreamingMethodHandlerSupplier,
            io.helidon.microprofile.grpc.core.UnaryMethodHandlerSupplier;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.microprofile.grpc.core.GrpcCdiExtension;
}