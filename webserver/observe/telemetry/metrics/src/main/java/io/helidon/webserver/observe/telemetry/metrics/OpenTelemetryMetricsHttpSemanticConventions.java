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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.http.Status;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig;
import io.helidon.webserver.observe.metrics.MetricsHttpSemanticConventions;
import io.helidon.webserver.observe.metrics.MetricsObserverConfig;

@Service.Singleton
class OpenTelemetryMetricsHttpSemanticConventions implements MetricsHttpSemanticConventions {

    private static final String HTTP_SERVER_REQUEST_DURATION = "http.server.request.duration";

    private final MeterRegistry meterRegistry;

    @Service.Inject
    OpenTelemetryMetricsHttpSemanticConventions(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<Filter> filter(MetricsObserverConfig config) {
        if (!config.enabled()) {
            return Optional.empty();
        }

        /*
        Unless explicitly disabled, collect the automatic metrics.
         */
        return (config.autoHttpMetrics().isPresent() && !config.autoHttpMetrics().get().enabled())
                ? Optional.empty()
                : Optional.of(MetricsRecordingFilter.create(meterRegistry, config.autoHttpMetrics().get()));
    }

    static class MetricsRecordingFilter implements Filter {

        private static MetricsRecordingFilter create(MeterRegistry meterRegistry, AutoHttpMetricsConfig config) {
            return new MetricsRecordingFilter(meterRegistry, config);
        }

        private final AutoHttpMetricsConfig config;
        private final MeterRegistry meterRegistry;

        private MetricsRecordingFilter(MeterRegistry meterRegistry, AutoHttpMetricsConfig config) {
            this.config = config;
            this.meterRegistry = meterRegistry;
        }

        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            var startTime = System.nanoTime();
            /*
            Duplicate the synch/async handling in the normal and exception case to avoid the overhead of using an Optional to hold
            the exception (if any) for use in a lambda.
             */
            try {
                chain.proceed();
                if (config.synchronous()) {
                    updateMetricsIfMeasured(req, res, startTime, null);
                } else {
                    Thread.ofVirtual().start(() -> updateMetricsIfMeasured(req, res, startTime, null));
                }
            } catch (Exception e) {
                if (config.synchronous()) {
                    updateMetricsIfMeasured(req, res, startTime, e);
                } else {
                    Thread.ofVirtual().start(() -> updateMetricsIfMeasured(req, res, startTime, e));
                }
            }
        }

        private void updateMetricsIfMeasured(RoutingRequest req, RoutingResponse resp, Long startTime, Exception exception) {
            if (!config.isMeasured(req.prologue().method(), req.path())) {
                return;
            }
            Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
            List<Tag> tags = new ArrayList<>(List.of(Tag.create("http.request.method", req.prologue().method().text()),
                                                     Tag.create("url.scheme", req.prologue().protocol()),
                                                     Tag.create("error.type", errorType(resp, exception)),
                                                     Tag.create("http.response.status.code", statusCode(resp, exception)),
                                                     Tag.create("http.route", req.matchingPattern().orElse(""))));

            /*
            Opt-in for these next two.
             */
            tags.add(Tag.create("server.address", req.listenerContext().config().host()));
            tags.add(Tag.create("server.port", Integer.toString(req.listenerContext().config().port())));

            Timer.Builder builder = Timer.builder(HTTP_SERVER_REQUEST_DURATION)
                    .description("HTTP server request duration")
                    .tags(tags);

            Timer timer = meterRegistry.getOrCreate(builder);
            timer.record(duration);
        }

        private String errorType(RoutingResponse resp, Exception exception) {
            return (exception != null)
                ? exception.getClass().getSimpleName()
                : resp.status().equals(Status.OK_200)
                    ? ""
                    : resp.status().codeText();
        }

        private String statusCode(RoutingResponse resp, Exception exception) {
            return (exception != null)
                    ? ""
                    : resp.status().codeText();
        }
    }
}
