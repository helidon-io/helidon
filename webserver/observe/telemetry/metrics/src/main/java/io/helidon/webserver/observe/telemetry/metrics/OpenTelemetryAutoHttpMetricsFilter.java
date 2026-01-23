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

package io.helidon.webserver.observe.telemetry.metrics;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import io.helidon.http.Status;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig;
import io.helidon.webserver.observe.metrics.spi.HttpMetricsFilter;

/**
 * Filter for registering and updating meters to measure HTTP traffic according to OpenTelemetry semantic conventions.
 */
@Service.PerLookup
class OpenTelemetryAutoHttpMetricsFilter implements HttpMetricsFilter {

    // Bucket boundaries as recommended by the OTel spec.
    // https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#metric-httpserverrequestduration
    private static final Duration[] BUCKET_BOUNDARIES =
            Stream.of(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0)
                    .map(d -> d * 1000.0) // seconds to milliseconds
                    .map(Double::longValue)
                    .map(Duration::ofMillis)
                    .toList()
                    .toArray(new Duration[0]);

    static final String HTTP_METHOD = "http.method";
    static final String HTTP_SCHEME = "http.scheme";
    static final String ERROR_TYPE = "error.type";
    static final String STATUS_CODE = "http.response.status_code";
    static final String HTTP_ROUTE = "http.route";
    static final String NETWORK_PROTOCOL_NAME = "network.protocol.name";
    static final String NETWORK_PROTOCOL_VERSION = "network.protocol.version";
    static final String SERVER_ADDRESS = "server.address";
    static final String SERVER_PORT = "server.port";
    static final String SOCKET_NAME = "socket.name";

    static final String TIMER_NAME = "http.server.request.duration";

    private final MeterRegistry meterRegistry;

    private AutoHttpMetricsConfig autoHttpMetricsConfig;

    OpenTelemetryAutoHttpMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Filter prepare(MetricsConfig metricsConfig, AutoHttpMetricsConfig autoHttpMetricsConfig) {
        this.autoHttpMetricsConfig = autoHttpMetricsConfig;
        return this;
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        long start = System.nanoTime();
        AtomicReference<Exception> exception = new AtomicReference<>();
        try {
            chain.proceed();
        } catch (Exception e) {
            exception.set(e);
            throw e;
        } finally {
            /*
            By default, update the meter asynchronously to minimize the performance impact on request handling.
             */
            if (autoHttpMetricsConfig.synchronous()) {
                updateTimer(req, res, start, exception);
            } else {
                Thread.ofVirtual().name("AutoMetricsUpdate", start)
                        .start(() -> updateTimer(req, res, start, exception));
            }
        }
    }

    private void updateTimer(RoutingRequest req, RoutingResponse res, long start, AtomicReference<Exception> exception) {
        var now = System.nanoTime();
        var tags = List.of(Tag.create(HTTP_METHOD, req.prologue().method().text()),
                Tag.create(HTTP_SCHEME, req.prologue().protocol()),
                Tag.create(ERROR_TYPE, errorType(res.status(), exception)),
                Tag.create(STATUS_CODE, Integer.toString(res.status().code())),
                Tag.create(HTTP_ROUTE, req.matchingPattern().orElse("")),
                Tag.create(NETWORK_PROTOCOL_NAME, req.prologue().protocol()),
                Tag.create(NETWORK_PROTOCOL_VERSION, req.prologue().protocolVersion()),
                Tag.create(SERVER_ADDRESS, req.requestedUri().host()),
                Tag.create(SERVER_PORT, Integer.toString(req.requestedUri().port())),
                Tag.create(SOCKET_NAME, req.listenerContext().config().name()));


        meterRegistry.timer(TIMER_NAME, tags)
                .orElse(meterRegistry.getOrCreate(Timer.builder(TIMER_NAME)
                                                          .tags(tags)
                                                          .buckets(BUCKET_BOUNDARIES)))
                .record(now - start, TimeUnit.NANOSECONDS);
    }

    private String errorType(Status status, AtomicReference<Exception> exception) {
        return exception == null || exception.get() == null
                ? status.family() == Status.Family.SUCCESSFUL
                        ? ""
                        : Integer.toString(status.code())
                : exception.get().getClass().getSimpleName();
    }
}
