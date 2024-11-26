/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * MP Rest client metrics.
 *
 * @see org.eclipse.microprofile.rest.client
 */
@Feature(value = "REST Client Metrics",
         description = "MicroProfile REST client spec implementation",
         in = HelidonFlavor.MP,
         path = "REST Client Metrics"
)
@SuppressWarnings({"requires-automatic", "requires-transitive-automatic"})
module io.helidon.microprofile.restclient.metrics {

    requires io.helidon.microprofile.metrics;

    requires transitive jakarta.ws.rs;
    requires jakarta.inject;
    requires transitive jersey.common;
    requires microprofile.metrics.api;
    requires jakarta.cdi;
    requires io.helidon.metrics.api;
    requires microprofile.rest.client.api;
    requires io.helidon.webserver;
    requires java.xml;

    requires static io.helidon.common.features.api;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.microprofile.restclientmetrics.RestClientMetricsCdiExtension;

    provides org.glassfish.jersey.internal.spi.AutoDiscoverable
            with io.helidon.microprofile.restclientmetrics.RestClientMetricsAutoDiscoverable;

    provides org.eclipse.microprofile.rest.client.spi.RestClientListener
            with io.helidon.microprofile.restclientmetrics.RestClientMetricsClientListener;
}