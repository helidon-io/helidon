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

package io.helidon.webserver.observe.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.http.Status;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

class AutoMetricsFilter implements Filter {

    /*
    Some OpenTelemetry attribute names are declared as constants, but not all.
     */
    static final String HTTP_METHOD = SemanticAttributes.HTTP_METHOD.getKey();
    static final String HTTP_SCHEME = SemanticAttributes.HTTP_SCHEME.getKey();
    static final String ERROR_TYPE = "error.type";
    static final String STATUS_CODE = "http.response.status_code";
    static final String HTTP_ROUTE = "http.route";
    static final String NETWORK_PROTOCOL_NAME = "network.protocol.name";
    static final String NETWORK_PROTOCOL_VERSION = "network.protocol.version";
    static final String SERVER_ADDRESS = "server.address";
    static final String SERVER_PORT = "server.port";



    static final String TIMER_NAME = "http.server.request.duration";

    private final AutoHttpMetricsConfig config;

    private final LazyValue<MeterRegistry> meterRegistry = LazyValue.create(() -> Services.get(MeterRegistry.class));

    private AutoMetricsFilter(AutoHttpMetricsConfig config) {
        this.config = config;
    }

    static AutoMetricsFilter create(AutoHttpMetricsConfig autoHttpMetricsConfig) {
        return new AutoMetricsFilter(autoHttpMetricsConfig);
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
            if (config.synchronous()) {
                updateTimer(req, res, start, exception);
            } else {
                Thread.ofVirtual().name("AutoMetricsUpdate", start)
                        .start(() -> updateTimer(req, res, start, exception));
            }
        }
    }

    private void updateTimer(RoutingRequest req, RoutingResponse res, long start, AtomicReference<Exception> exception) {
        var now = System.nanoTime();
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.create(HTTP_METHOD, req.prologue().method().text()));
        tags.add(Tag.create(HTTP_SCHEME, req.prologue().protocol()));
        tags.add(Tag.create(ERROR_TYPE, errorType(res.status(), exception)));
        tags.add(Tag.create(STATUS_CODE, Integer.toString(res.status().code())));
        tags.add(Tag.create(HTTP_ROUTE, req.matchingPattern().orElse("")));
        tags.add(Tag.create(NETWORK_PROTOCOL_NAME, req.prologue().protocol()));
        tags.add(Tag.create(NETWORK_PROTOCOL_VERSION, req.prologue().protocolVersion()));
        tags.add(Tag.create(SERVER_ADDRESS, req.requestedUri().host()));
        tags.add(Tag.create(SERVER_PORT, Integer.toString(req.requestedUri().port())));

        meterRegistry.get().timer(TIMER_NAME, tags)
                .orElse(meterRegistry.get().getOrCreate(Timer.builder(TIMER_NAME).tags(tags)))
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
