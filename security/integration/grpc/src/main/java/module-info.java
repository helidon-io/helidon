/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
 * Security integration with Helidon gRPC.
 */
module io.helidon.security.integration.grpc {
    exports io.helidon.security.integration.grpc;

    requires io.helidon.common;
    requires io.helidon.common.context;
    requires transitive io.helidon.grpc.core;
    requires static io.helidon.grpc.server;
    requires transitive io.helidon.security;
    requires transitive io.helidon.security.integration.common;
    requires java.logging;
    requires io.helidon.webserver;
    requires io.grpc;
}
