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

package io.helidon.webclient.metrics;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.webclient.spi.WebClientServiceProvider;

/**
 * Configuration of HTTP transport metrics for WebClient.
 */
@Prototype.Blueprint
@Prototype.Configured("http-metrics")
@Prototype.Provides(WebClientServiceProvider.class)
interface WebClientTransportMetricsConfigBlueprint extends Prototype.Factory<WebClientTransportMetrics> {
    /**
     * Whether to observe client connections, handshakes, and HTTP exchanges.
     * A disabled service does not resolve the meter registry or acquire a metrics lease.
     *
     * @return whether transport metrics are enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Meter registry to use. When absent, a service created from WebClient configuration uses the client's
     * service registry; a service created programmatically uses the global service registry.
     *
     * @return explicit meter registry, or empty to use the service registry
     */
    Optional<MeterRegistry> meterRegistry();

    /**
     * Name of this service instance.
     *
     * @return service name
     */
    @Option.Configured
    @Option.Default("http-metrics")
    String name();
}
