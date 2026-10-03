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
 * OTLP HTTP/JSON publisher for Helidon metrics.
 */
module io.helidon.metrics.publishers.otlp {
    requires transitive io.helidon.metrics.providers.helidon;
    requires transitive io.helidon.metrics.api;
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common;
    requires transitive io.helidon.config;

    requires io.helidon.common.buffers;
    requires io.helidon.common.media.type;
    requires io.helidon.common.task;
    requires io.helidon.http;
    requires io.helidon.json;
    requires io.helidon.json.binding;
    requires io.helidon.service.registry;
    requires io.helidon.webclient.api;
    requires io.helidon.webclient.http1;
    requires static io.helidon.config.metadata;

    exports io.helidon.metrics.publishers.otlp;

    provides io.helidon.metrics.spi.MetricsPublisherProvider with
            io.helidon.metrics.publishers.otlp.OtlpPublisherProvider;
}
