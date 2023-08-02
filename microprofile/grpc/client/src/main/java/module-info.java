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

/**
 * gRPC client implementation for Helidon MP.
 */
module io.helidon.microprofile.grpc.client {
    requires java.logging;

    requires jakarta.cdi;
    requires jakarta.inject;

    requires transitive grpc.core;
    requires transitive io.helidon.microprofile.grpc.core;

    exports io.helidon.microprofile.grpc.client;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.microprofile.grpc.client.GrpcClientCdiExtension;

    // needed when running with modules - to make private methods accessible
    opens io.helidon.microprofile.grpc.client to weld.core.impl, io.helidon.microprofile.cdi;
}
