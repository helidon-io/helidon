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
 * gRPC Server Module.
 */
module io.helidon.grpc.server {
    exports io.helidon.grpc.server;

    requires io.helidon.common;
    requires io.helidon.common.context;
    requires io.helidon.common.pki;
    requires io.helidon.config;
    requires transitive io.helidon.grpc.core;
    requires transitive io.helidon.health;
    requires io.helidon.tracing;

    requires transitive grpc.services;
    requires transitive microprofile.health.api;
    requires transitive io.opentracing.api;
    requires transitive opentracing.grpc;

    requires java.annotation;
    requires java.logging;

    requires jakarta.inject.api;
}
