/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
 * Microprofile metrics implementation.
 */
module io.helidon.microprofile.metrics {
    requires java.logging;

    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;
    requires static jakarta.interceptor.api;
    requires static java.annotation;
    requires static jakarta.activation;

    requires io.helidon.common.servicesupport;
    requires io.helidon.common.servicesupport.cdi;
    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.config;
    requires transitive io.helidon.metrics;

    requires transitive microprofile.config.api;
    requires microprofile.metrics.api;


    exports io.helidon.microprofile.metrics;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.metrics to weld.core.impl, io.helidon.microprofile.cdi;

    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.metrics.MetricsCdiExtension;
}
