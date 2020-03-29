/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 * gRPC Metrics Module.
 */
module io.helidon.grpc.metrics {
    exports io.helidon.grpc.metrics;

    requires transitive io.helidon.grpc.core;
    requires static io.helidon.grpc.client;
    requires static io.helidon.grpc.server;
    requires transitive io.helidon.metrics;

    requires microprofile.metrics.api;
    requires io.helidon.common.metrics;
}
