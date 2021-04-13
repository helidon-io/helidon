/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
 * gRPC microprofile metrics module
 */
module io.helidon.microprofile.grpc.metrics {
    exports io.helidon.microprofile.grpc.metrics;

    requires transitive io.helidon.grpc.metrics;
    requires transitive io.helidon.microprofile.grpc.server;
    requires transitive io.helidon.microprofile.metrics;
    requires transitive io.helidon.microprofile.server;

    requires io.helidon.servicecommon.restcdi;

    requires java.logging;
    requires jakarta.interceptor.api;

    provides io.helidon.microprofile.grpc.server.AnnotatedServiceConfigurer
            with io.helidon.microprofile.grpc.metrics.MetricsConfigurer;

    provides javax.enterprise.inject.spi.Extension
            with io.helidon.microprofile.grpc.metrics.GrpcMetricsCdiExtension;
}