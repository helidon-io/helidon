/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
 * Micrometer adapter for Helidon metrics API.
 */
module io.helidon.metrics.providers.micrometer {
    requires io.helidon.metrics.api;
    requires micrometer.core;
    requires static micrometer.registry.prometheus;
    requires static micrometer.registry.prometheus.simpleclient;
    requires io.helidon.common;
    requires io.helidon.common.media.type;
    requires io.helidon.config;
    requires simpleclient.common;
    requires simpleclient.tracer.common;
    requires simpleclient;
    requires io.helidon.service.registry;

    exports io.helidon.metrics.providers.micrometer.spi;

    provides io.helidon.metrics.spi.MetricsFactoryProvider with
            io.helidon.metrics.providers.micrometer.MicrometerMetricsFactoryProvider;
    provides io.helidon.metrics.spi.MeterRegistryFormatterProvider
            with io.helidon.metrics.providers.micrometer.MicrometerPrometheusFormatterProvider;

    uses io.helidon.metrics.spi.MeterRegistryLifeCycleListener;
    uses io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider;

}