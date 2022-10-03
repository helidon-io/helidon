/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
 * Helidon Metrics implementation.
 */
module io.helidon.metrics {
    requires java.logging;

    requires io.helidon.common;
    requires io.helidon.reactive.webserver.cors;
    requires transitive io.helidon.metrics.api;
    requires transitive io.helidon.metrics.serviceapi;

    requires transitive microprofile.metrics.api;
    requires java.management;
    requires transitive io.helidon.reactive.webserver; // webserver/webserver/Context is a public return value
    requires io.helidon.reactive.media.jsonp;
    requires jakarta.json;
    requires io.helidon.reactive.servicecommon;

    exports io.helidon.metrics;

    provides io.helidon.metrics.api.spi.RegistryFactoryProvider with io.helidon.metrics.RegistryFactoryProviderImpl;
    provides io.helidon.metrics.serviceapi.spi.MetricsSupportProvider with io.helidon.metrics.MetricsSupportProviderImpl;
    provides io.helidon.common.configurable.spi.ExecutorServiceSupplierObserver
            with io.helidon.metrics.ExecutorServiceMetricsObserver;

    uses io.helidon.metrics.ExemplarService;
    uses io.helidon.metrics.serviceapi.spi.MetricsSupportProvider;
}
