/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 * Integration with OCI Metrics.
 */
module io.helidon.integrations.oci.metrics {
    requires java.logging;
    // requires java.json;

    /*
    requires io.helidon.common.reactive;
    requires io.helidon.integrations.common.rest;
    requires transitive io.helidon.integrations.oci.connect;
    requires io.helidon.common.http;
    requires io.helidon.common;
     */
    requires io.helidon.config;
    requires io.helidon.metrics.api;
    requires io.helidon.webserver;

    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;

    requires jakarta.enterprise.cdi.api;
    requires jakarta.interceptor.api;

    requires oci.java.sdk.monitoring;
    requires io.helidon.config.mp;

    exports io.helidon.integrations.oci.metrics;

    provides javax.enterprise.inject.spi.Extension with io.helidon.integrations.oci.metrics.OciMetricsCdiExtension;

    opens io.helidon.integrations.oci.metrics to weld.core.impl, io.helidon.microprofile.cdi;
}