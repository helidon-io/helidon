/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
 * Helidon WebClient gRPC Tracing Support.
 */
module io.helidon.webserver.grpc.tracing {

    requires io.helidon.common.config;
    requires io.helidon.webserver.grpc;
    requires io.helidon.grpc.core;
    requires io.helidon.tracing;

    exports io.helidon.webserver.grpc.tracing;

    uses io.helidon.webserver.grpc.spi.GrpcServerService;

    provides io.helidon.webserver.grpc.spi.GrpcServerServiceProvider
            with io.helidon.webserver.grpc.tracing.GrpcTracingServiceProvider;
}
