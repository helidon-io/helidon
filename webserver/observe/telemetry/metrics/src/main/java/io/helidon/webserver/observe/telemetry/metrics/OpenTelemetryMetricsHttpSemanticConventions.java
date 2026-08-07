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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.config.Config;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.telemetry.otelconfig.HelidonOpenTelemetry;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig;
import io.helidon.webserver.observe.metrics.MetricsObserverConfig;
import io.helidon.webserver.observe.metrics.spi.AutoHttpMetricsProvider;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;

import static java.lang.System.Logger.Level.WARNING;

/**
 * Provider of automatic metrics for HTTP requests which implements the OpenTelemetry server HTTP semantic conventions.
 * <p>
 * By using the OpenTelemetry API directly, rather than the Helidon metrics API, we can support the semantic conventions
 * even in apps that use MicroProfile Metrics. Using the Helidon metrics API in an MP app would trigger errors because
 * MicroProfile metrics prohibits tag names containing dots.
 */
@Service.Singleton
class OpenTelemetryMetricsHttpSemanticConventions implements AutoHttpMetricsProvider {
    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryMetricsHttpSemanticConventions.class.getName());
    private static final int MAX_NONSTANDARD_METHODS = 16;

    // OpenTelemetry
    static final String HTTP_METHOD = HttpAttributes.HTTP_REQUEST_METHOD.getKey();
    static final String URL_SCHEME = UrlAttributes.URL_SCHEME.getKey();
    static final String ERROR_TYPE = ErrorAttributes.ERROR_TYPE.getKey();
    static final String STATUS_CODE = HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey();
    static final String HTTP_ROUTE = HttpAttributes.HTTP_ROUTE.getKey();
    static final String SERVER_ADDRESS = ServerAttributes.SERVER_ADDRESS.getKey();
    static final String SERVER_PORT = ServerAttributes.SERVER_PORT.getKey();
    // Helidon
    static final String SOCKET_NAME = "socket.name";
    static final String TIMER_NAME = "http.server.request.duration";
    private static final String UNKNOWN_METHOD = "UNKNOWN";
    /*
    Bucket boundaries as recommended by the OpenTelemetry spec.
    https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#metric-httpserverrequestduration
     */
    private static final List<Double> BUCKET_BOUNDARIES =
            List.of(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0);

    private final DoubleHistogram httpRequestDuration;
    private final NonstandardHttpMethods nonstandardHttpMethods = new NonstandardHttpMethods();

    @Service.Inject
    OpenTelemetryMetricsHttpSemanticConventions(OpenTelemetry openTelemetry, Config config) {
        HelidonOpenTelemetry helidonOpenTelemetry = HelidonOpenTelemetry.builder()
                .config(config.get(HelidonOpenTelemetry.CONFIG_KEY))
                .global(false) // we just need the service name; we do not need to assign the global instance
                .build();

        httpRequestDuration = httpRequestDuration(openTelemetry.meterBuilder(helidonOpenTelemetry.prototype().service())
                .build());
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
                : Optional.of(MetricsRecordingFilter.create(httpRequestDuration,
                                                            config.autoHttpMetrics().orElse(AutoHttpMetricsConfig.create()),
                                                            nonstandardHttpMethods));
    }

    private static DoubleHistogram httpRequestDuration(Meter meter) {
        return meter
                .histogramBuilder(TIMER_NAME)
                .setDescription("HTTP request dureation")
                .setUnit("s") // seconds
                .setExplicitBucketBoundariesAdvice(BUCKET_BOUNDARIES)
                .build();
    }

    static class MetricsRecordingFilter implements Filter {
        private static final System.Logger LOGGER = System.getLogger(MetricsRecordingFilter.class.getName());

        private final DoubleHistogram httpRequestDuration;
        private final AutoHttpMetricsConfig config;
        private final NonstandardHttpMethods nonstandardHttpMethods;

        private MetricsRecordingFilter(DoubleHistogram httpRequestDuration,
                                       AutoHttpMetricsConfig config,
                                       NonstandardHttpMethods nonstandardHttpMethods) {
            this.httpRequestDuration = httpRequestDuration;
            this.config = config;
            this.nonstandardHttpMethods = nonstandardHttpMethods;
        }

        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            var startTime = System.nanoTime();
            if (config.useUpdatedHttpMetrics()) {
                filterUpdated(chain, req, res, startTime);
            } else {
                filterLegacy(chain, req, res, startTime);
            }
        }

        private void filterUpdated(FilterChain chain, RoutingRequest req, RoutingResponse res, long startTime) {
            var exception = new AtomicReference<Exception>();
            var chainComplete = new AtomicBoolean();
            var responseSent = new AtomicBoolean();
            var recorded = new AtomicBoolean();
            var measured = config.isMeasured(req.prologue().method(), req.prologue().uriPath());
            /*
            Update the timer in whenSent rather than here in this filter. That way we include time spent in running succeeding
            filters and in preparing the response entity, to more accurately capture as much as possible the full time the
            server spent responding to the request.
             */
            Runnable recordMetrics = () -> {
                if (recorded.compareAndSet(false, true)) {
                    try {
                        updateMetricsIfMeasured(req,
                                                res,
                                                measured,
                                                startTime,
                                                System.nanoTime(),
                                                exception.get());
                    } catch (Throwable e) {
                        LOGGER.log(WARNING, "Failed to record HTTP request metrics", e);
                    }
                }
            };
            res.whenSent(() -> {
                responseSent.set(true);
                if (chainComplete.get()) {
                    recordMetrics.run();
                }
            });

            try {
                chain.proceed();
                chainComplete.set(true);
                if (responseSent.get()) {
                    recordMetrics.run();
                }
            } catch (Exception e) {
                exception.set(e);
                chainComplete.set(true);
                if (responseSent.get()) {
                    recordMetrics.run();
                }
                throw e;
            }
        }

        private void filterLegacy(FilterChain chain, RoutingRequest req, RoutingResponse res, long startTime) {
            try {
                chain.proceed();
                Thread.ofVirtual().start(() -> updateLegacyMetricsIfMeasured(req, res, startTime, System.nanoTime(), null));
            } catch (Exception e) {
                Thread.ofVirtual().start(() -> updateLegacyMetricsIfMeasured(req, res, startTime, System.nanoTime(), e));
                throw e;
            }
        }

        private static MetricsRecordingFilter create(DoubleHistogram httpRequestDuration,
                                                     AutoHttpMetricsConfig config,
                                                     NonstandardHttpMethods nonstandardHttpMethods) {
            return new MetricsRecordingFilter(httpRequestDuration, config, nonstandardHttpMethods);
        }

        private static boolean isOptedIn(AutoHttpMetricsConfig config, String attributeName) {
            return config.optIn().stream()
                    .anyMatch(optIn -> optIn.equals(TIMER_NAME) || optIn.equals(TIMER_NAME + ":" + attributeName));
        }

        private void updateMetricsIfMeasured(RoutingRequest req,
                                             RoutingResponse resp,
                                             boolean measured,
                                             Long startTime,
                                             long endTime,
                                             Exception exception) {
            if (!measured) {
                return;
            }
            AttributesBuilder attrBuilder = Attributes.builder();

            attrBuilder.put(AttributeKey.stringKey(HTTP_METHOD), nonstandardHttpMethods.normalize(req.prologue().method()))
                    .put(AttributeKey.stringKey(URL_SCHEME), req.prologue().protocol())
                    .put(AttributeKey.stringKey(ERROR_TYPE), errorType(resp, exception))
                    .put(AttributeKey.longKey(STATUS_CODE), resp.status().code())
                    .put(AttributeKey.stringKey(SOCKET_NAME), req.listenerContext().config().name());

            req.matchingPattern()
                    .filter(route -> !route.isBlank())
                    .ifPresent(route -> attrBuilder.put(AttributeKey.stringKey(HTTP_ROUTE), route));

            if (isOptedIn(config, SERVER_ADDRESS)) {
                attrBuilder.put(AttributeKey.stringKey(SERVER_ADDRESS), req.localPeer().host());
            }
            if (isOptedIn(config, SERVER_PORT)) {
                attrBuilder.put(AttributeKey.longKey(SERVER_PORT), (long) req.localPeer().port());
            }

            /*
            The OpenTelemetry semantic conventions describe network.protocol.name and version, but these are
            required only if the protocol is not http, and it always is http in this particular class. Further, we
            don't currently have a way to get the HTTP version at runtime from a request.
             */

            httpRequestDuration.record((endTime - startTime) / 1_000_000_000.0, attrBuilder.build());
        }

        private void updateLegacyMetricsIfMeasured(RoutingRequest req,
                                                   RoutingResponse resp,
                                                   long startTime,
                                                   long endTime,
                                                   Exception exception) {
            if (!config.isMeasured(req.prologue().method(), req.prologue().uriPath())) {
                return;
            }
            AttributesBuilder attrBuilder = Attributes.builder();

            attrBuilder.put(AttributeKey.stringKey(HTTP_METHOD), nonstandardHttpMethods.normalize(req.prologue().method()))
                    .put(AttributeKey.stringKey(URL_SCHEME), req.prologue().protocol())
                    .put(AttributeKey.stringKey(ERROR_TYPE), errorType(resp, exception))
                    .put(AttributeKey.longKey(STATUS_CODE), legacyStatusCode(resp, exception))
                    .put(AttributeKey.stringKey(HTTP_ROUTE), req.matchingPattern().orElse(""))
                    .put(AttributeKey.stringKey(SOCKET_NAME), req.listenerContext().config().name());

            if (isOptedIn(config, SERVER_ADDRESS)) {
                attrBuilder.put(AttributeKey.stringKey(SERVER_ADDRESS), req.localPeer().host());
            }
            if (isOptedIn(config, SERVER_PORT)) {
                attrBuilder.put(AttributeKey.longKey(SERVER_PORT), (long) req.localPeer().port());
            }

            httpRequestDuration.record((endTime - startTime) / 1_000_000.0, attrBuilder.build());
        }

        private String errorType(RoutingResponse resp, Exception exception) {
            return (exception != null)
                    ? exception.getClass().getSimpleName()
                    : resp.status().equals(Status.OK_200)
                            ? ""
                            : resp.status().codeText();
        }

        private long legacyStatusCode(RoutingResponse resp, Exception exception) {
            return (exception != null)
                    ? 0L
                    : resp.status().code();
        }

    }

    private static final class NonstandardHttpMethods {
        private final Set<String> methods = new HashSet<>();
        private final Lock lock = new ReentrantLock();
        private final AtomicBoolean limitWarningLogged = new AtomicBoolean();

        private String normalize(Method method) {
            if (method == Method.GET
                    || method == Method.HEAD
                    || method == Method.POST
                    || method == Method.PUT
                    || method == Method.DELETE
                    || method == Method.CONNECT
                    || method == Method.OPTIONS
                    || method == Method.TRACE
                    || method == Method.PATCH) {
                return method.text();
            }

            String methodName = method.text();
            lock.lock();
            try {
                if (methods.contains(methodName)) {
                    return methodName;
                }
                if (methods.size() < MAX_NONSTANDARD_METHODS) {
                    methods.add(methodName);
                    return methodName;
                }
            } finally {
                lock.unlock();
            }

            if (limitWarningLogged.compareAndSet(false, true)) {
                LOGGER.log(WARNING,
                           "Maximum of {0} non-standard HTTP methods has been reached; {1} and subsequent "
                                   + "previously unseen non-standard methods will be recorded as UNKNOWN",
                           MAX_NONSTANDARD_METHODS,
                           methodName);
            }
            return UNKNOWN_METHOD;
        }
    }
}
