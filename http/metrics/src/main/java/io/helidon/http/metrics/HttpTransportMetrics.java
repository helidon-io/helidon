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

package io.helidon.http.metrics;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

import io.helidon.common.Api;
import io.helidon.http.HttpTransportObserver;
import io.helidon.metrics.api.MeterRegistry;

/**
 * Metrics API integration for the HTTP transport lifecycle across HTTP versions.
 *
 * @see io.helidon.http.metrics
 */
@Api.Internal
public final class HttpTransportMetrics {
    static final String CONNECTIONS_OPENED = "helidon.http.connections.opened";
    static final String CONNECTIONS_ESTABLISHED = "helidon.http.connections.established";
    static final String CONNECTIONS_ACTIVE = "helidon.http.connections.active";
    static final String CONNECTIONS_CLOSED = "helidon.http.connections.closed";
    static final String CONNECTIONS_DURATION = "helidon.http.connections.duration";
    static final String HANDSHAKES = "helidon.http.handshakes";
    static final String HANDSHAKES_DURATION = "helidon.http.handshakes.duration";
    static final String STREAMS_OPENED = "helidon.http.streams.opened";
    static final String STREAMS_ACTIVE = "helidon.http.streams.active";
    static final String STREAMS_CLOSED = "helidon.http.streams.closed";
    static final String STREAMS_DURATION = "helidon.http.streams.duration";

    private HttpTransportMetrics() {
    }

    /**
     * Acquires an HTTP transport metrics lease for a meter registry.
     *
     * <p>The caller closes the lease after all transports which publish through it have stopped. Closing is
     * non-blocking. If the caller owns the registry, it must prevent later acquisition and await the final owning
     * lease's {@link Lease#completion()} before closing the registry.
     *
     * @param registry meter registry
     * @return HTTP transport metrics lease
     */
    public static Lease acquire(MeterRegistry registry) {
        return HttpTransportMetricsState.acquire(Objects.requireNonNull(registry, "registry"));
    }

    /**
     * Owned lease for observing HTTP transport lifecycle.
     */
    public interface Lease extends HttpTransportObserver, AutoCloseable {
        /**
         * Starts the non-blocking release of this lease.
         */
        @Override
        void close();

        /**
         * Completion of this lease's asynchronous release.
         *
         * <p>The stage completes only after {@link #close()}, after every connection opened through this lease closes,
         * and after either ownership passes to another lease for the same configured registry or final native-registry
         * meter cleanup completes or pending provider work is permanently abandoned because asynchronous execution is
         * unavailable. The registry must remain usable until the final owning lease completes.
         *
         * @return release completion
         */
        CompletionStage<Void> completion();
    }
}
