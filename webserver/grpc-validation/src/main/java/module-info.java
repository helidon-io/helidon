/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
 * Helidon WebServer gRPC Validation Support.
 */
module io.helidon.webserver.grpc.validation {
    requires static io.helidon.config.metadata;

    requires io.helidon.grpc.core;
    requires io.helidon.validation;

    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.config;
    requires transitive io.grpc;
    requires transitive io.helidon.webserver.grpc;

    exports io.helidon.webserver.grpc.validation;

    provides io.helidon.webserver.grpc.spi.GrpcServerServiceProvider
            with io.helidon.webserver.grpc.validation.GrpcValidationProvider;
}
