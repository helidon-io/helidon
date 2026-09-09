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
 * Custom connector used to verify messaging integration behavior.
 */
@SuppressWarnings("helidon:api:preview")
module io.helidon.messaging.tests.custom.connector {
    requires io.helidon.builder.api;
    requires io.helidon.common;
    requires io.helidon.common.mapper;
    requires io.helidon.common.types;
    requires io.helidon.config;
    requires io.helidon.faulttolerance;
    requires io.helidon.http;
    requires io.helidon.messaging;
    requires io.helidon.metrics.api;
    requires io.helidon.service.registry;
    requires static io.helidon.config.metadata;

    exports io.helidon.messaging.tests.custom.connector;
}
