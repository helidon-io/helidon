/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
 * Limits feature for Helidon WebServer.
 */
module io.helidon.webserver.concurrency.limits {
    requires io.helidon.common;
    requires io.helidon.http;
    requires io.helidon.service.registry;
    requires io.helidon.tracing;
    requires io.helidon.webserver;

    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common.config;
    requires transitive io.helidon.common.concurrency.limits;

    exports io.helidon.webserver.concurrency.limits;

    provides io.helidon.webserver.spi.ServerFeatureProvider
            with io.helidon.webserver.concurrency.limits.LimitsFeatureProvider;

    uses io.helidon.common.concurrency.limits.spi.LimitProvider;
}