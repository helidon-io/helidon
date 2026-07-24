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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.context.Context;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutePathSupport;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig;
import io.helidon.webserver.observe.metrics.MetricsObserverConfig;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenTelemetryMetricsHttpSemanticConventionsTest {

    @Test
    void measuredRequestUsesProvidedRouteSupplier() throws Exception {
        AtomicReference<Attributes> recordedAttributes = new AtomicReference<>();
        Filter filter = filter(recordedAttributes::set, true);
        Context context = Context.create();
        RoutingRequest request = request(context);
        FilterChain chain = mock(FilterChain.class);
        AtomicInteger routeInvocations = new AtomicInteger();
        AtomicReference<Runnable> whenSent = new AtomicReference<>();

        doAnswer(invocation -> {
            RoutePathSupport.provideRoute(context, () -> {
                routeInvocations.incrementAndGet();
                return "/providedRoute";
            });
            return null;
        }).when(chain).proceed();

        filter.filter(chain, request, response(whenSent));

        assertThat(routeInvocations.get(), is(0));

        whenSent.get().run();

        assertThat(recordedAttributes.get().get(AttributeKey.stringKey(OpenTelemetryMetricsHttpSemanticConventions.HTTP_ROUTE)),
                   is("/providedRoute"));
        assertThat(routeInvocations.get(), is(1));
    }

    @Test
    void unmeasuredRequestDoesNotUseProvidedRouteSupplier() throws Exception {
        AtomicInteger routeInvocations = new AtomicInteger();
        DoubleHistogram histogram = mock(DoubleHistogram.class);
        AutoHttpMetricsConfig autoConfig = mock(AutoHttpMetricsConfig.class);
        MetricsObserverConfig metricsConfig = mock(MetricsObserverConfig.class);
        Context context = Context.create();
        RoutingRequest request = request(context);
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<Runnable> whenSent = new AtomicReference<>();

        when(metricsConfig.enabled()).thenReturn(true);
        when(metricsConfig.autoHttpMetrics()).thenReturn(Optional.of(autoConfig));
        when(autoConfig.enabled()).thenReturn(true);
        when(autoConfig.isMeasured(any(), any())).thenReturn(false);
        when(autoConfig.optIn()).thenReturn(List.of());
        when(autoConfig.useUpdatedHttpMetrics()).thenReturn(true);
        doAnswer(invocation -> {
            RoutePathSupport.provideRoute(context, () -> {
                routeInvocations.incrementAndGet();
                return "/providedRoute";
            });
            return null;
        }).when(chain).proceed();

        filter(histogram, metricsConfig).filter(chain, request, response(whenSent));
        whenSent.get().run();

        assertThat(routeInvocations.get(), is(0));
        verify(histogram, never()).record(anyDouble(), any(Attributes.class));
    }

    @Test
    void filterLogsMetricsFailureAfterSuccessfulChain() throws Exception {
        AssertionError failure = new AssertionError("metrics failure");
        Filter filter = filter(failure, true);
        AtomicReference<Runnable> whenSent = new AtomicReference<>();

        try (TestLogHandler handler = TestLogHandler.install()) {
            filter.filter(mock(FilterChain.class), request(), response(whenSent));
            whenSent.get().run();

            LogRecord record = handler.await();
            assertThat(record.getMessage(), containsString("Failed to record HTTP request metrics"));
            assertThat(record.getLevel(), is(Level.WARNING));
            assertThat(record.getThrown(), sameInstance(failure));
        }
    }

    @Test
    void filterLogsMetricsFailureAfterExceptionalChain() throws Exception {
        AssertionError metricsFailure = new AssertionError("metrics failure");
        IllegalArgumentException chainFailure = new IllegalArgumentException("chain failure");
        Filter filter = filter(metricsFailure, true);
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<Runnable> whenSent = new AtomicReference<>();
        doThrow(chainFailure).when(chain).proceed();

        try (TestLogHandler handler = TestLogHandler.install()) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                             () -> filter.filter(chain, request(), response(whenSent)));
            whenSent.get().run();
            LogRecord record = handler.await();

            assertThat(exception, sameInstance(chainFailure));
            assertThat(record.getMessage(), containsString("Failed to record HTTP request metrics"));
            assertThat(record.getLevel(), is(Level.WARNING));
            assertThat(record.getThrown(), sameInstance(metricsFailure));
        }
    }

    @Test
    void recordsOnceWhenResponseIsSentBeforeChainFailure() throws Exception {
        AtomicReference<Attributes> recordedAttributes = new AtomicReference<>();
        AtomicInteger recorded = new AtomicInteger();
        Filter filter = filter(attributes -> {
            recordedAttributes.set(attributes);
            recorded.incrementAndGet();
        }, true);
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<Runnable> whenSent = new AtomicReference<>();
        IllegalArgumentException failure = new IllegalArgumentException("chain failure");
        doAnswer(invocation -> {
            whenSent.get().run();
            throw failure;
        }).when(chain).proceed();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                         () -> filter.filter(chain, request(), response(whenSent)));

        assertThat(exception, sameInstance(failure));
        assertThat(recorded.get(), is(1));
        assertThat(recordedAttributes.get().get(AttributeKey.stringKey(OpenTelemetryMetricsHttpSemanticConventions.ERROR_TYPE)),
                   is("IllegalArgumentException"));
    }

    @Test
    void recordsOnceWhenChainFailsBeforeResponseIsSent() throws Exception {
        AtomicInteger recorded = new AtomicInteger();
        Filter filter = filter(attributes -> recorded.incrementAndGet(), true);
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<Runnable> whenSent = new AtomicReference<>();
        IllegalArgumentException failure = new IllegalArgumentException("transport failure");
        doThrow(failure).when(chain).proceed();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                         () -> filter.filter(chain, request(), response(whenSent)));
        whenSent.get().run();

        assertThat(exception, sameInstance(failure));
        assertThat(recorded.get(), is(1));
    }

    @Test
    void legacyMetricsUseMatchingPatternWithoutWhenSent() throws Exception {
        AtomicReference<Attributes> recordedAttributes = new AtomicReference<>();
        CountDownLatch recorded = new CountDownLatch(1);
        Filter filter = filter(attributes -> {
            recordedAttributes.set(attributes);
            recorded.countDown();
        }, false);
        RoutingRequest request = request();
        RoutingResponse response = response(new AtomicReference<>());
        when(request.matchingPattern()).thenReturn(Optional.of("/matchingPattern"));

        filter.filter(mock(FilterChain.class), request, response);

        assertThat(recorded.await(5, TimeUnit.SECONDS), is(true));
        verify(response, never()).whenSent(any(Runnable.class));
        assertThat(recordedAttributes.get().get(AttributeKey.stringKey(OpenTelemetryMetricsHttpSemanticConventions.HTTP_ROUTE)),
                   is("/matchingPattern"));
    }

    @Test
    void legacyMetricsUseZeroStatusAfterChainFailure() throws Exception {
        AtomicReference<Attributes> recordedAttributes = new AtomicReference<>();
        CountDownLatch recorded = new CountDownLatch(1);
        Filter filter = filter(attributes -> {
            recordedAttributes.set(attributes);
            recorded.countDown();
        }, false);
        FilterChain chain = mock(FilterChain.class);
        IllegalArgumentException failure = new IllegalArgumentException("chain failure");
        doThrow(failure).when(chain).proceed();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                         () -> filter.filter(chain, request(), response(new AtomicReference<>())));

        assertThat(exception, sameInstance(failure));
        assertThat(recorded.await(5, TimeUnit.SECONDS), is(true));
        assertThat(recordedAttributes.get().get(AttributeKey.longKey(OpenTelemetryMetricsHttpSemanticConventions.STATUS_CODE)),
                   is(0L));
    }

    private static Filter filter(Throwable failure, boolean useUpdatedHttpMetrics) {
        return filter(attributes -> {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(failure);
        }, useUpdatedHttpMetrics);
    }

    private static Filter filter(Consumer<Attributes> recorder, boolean useUpdatedHttpMetrics) {
        DoubleHistogram histogram = mock(DoubleHistogram.class);
        doAnswer(invocation -> {
            recorder.accept(invocation.getArgument(1));
            return null;
        }).when(histogram).record(anyDouble(), any(Attributes.class));

        MetricsObserverConfig config = MetricsObserverConfig.builder()
                .autoHttpMetrics(AutoHttpMetricsConfig.builder()
                                         .useUpdatedHttpMetrics(useUpdatedHttpMetrics)
                                         .build())
                .buildPrototype();
        return filter(histogram, config);
    }

    private static Filter filter(DoubleHistogram histogram, MetricsObserverConfig metricsConfig) {
        OpenTelemetry openTelemetry = mock(OpenTelemetry.class);
        MeterBuilder meterBuilder = mock(MeterBuilder.class);
        Meter meter = mock(Meter.class);
        DoubleHistogramBuilder histogramBuilder = mock(DoubleHistogramBuilder.class);

        when(openTelemetry.meterBuilder(anyString())).thenReturn(meterBuilder);
        when(meterBuilder.build()).thenReturn(meter);
        when(meter.histogramBuilder(OpenTelemetryMetricsHttpSemanticConventions.TIMER_NAME)).thenReturn(histogramBuilder);
        when(histogramBuilder.setDescription(anyString())).thenReturn(histogramBuilder);
        when(histogramBuilder.setUnit(anyString())).thenReturn(histogramBuilder);
        doReturn(histogramBuilder).when(histogramBuilder).setExplicitBucketBoundariesAdvice(any());
        when(histogramBuilder.build()).thenReturn(histogram);

        OpenTelemetryMetricsHttpSemanticConventions provider =
                new OpenTelemetryMetricsHttpSemanticConventions(openTelemetry,
                                                                Config.just("telemetry.service: test",
                                                                            MediaTypes.APPLICATION_YAML));

        return provider.filter(metricsConfig).orElseThrow();
    }

    private static RoutingRequest request() {
        return request(Context.create());
    }

    private static RoutingRequest request(Context context) {
        RoutingRequest request = mock(RoutingRequest.class);
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        ListenerContext listenerContext = mock(ListenerContext.class);

        when(request.prologue()).thenReturn(HttpPrologue.create("HTTP/1.1",
                                                                "HTTP",
                                                                "1.1",
                                                                Method.GET,
                                                                "/test",
                                                                false));
        when(request.matchingPattern()).thenReturn(Optional.empty());
        when(request.context()).thenReturn(context);
        when(request.listenerContext()).thenReturn(listenerContext);
        when(listenerContext.config()).thenReturn(listenerConfig);
        when(listenerConfig.name()).thenReturn("@default");

        return request;
    }

    private static RoutingResponse response(AtomicReference<Runnable> whenSent) {
        RoutingResponse response = mock(RoutingResponse.class);
        when(response.status()).thenReturn(Status.OK_200);
        when(response.whenSent(any(Runnable.class))).thenAnswer(invocation -> {
            whenSent.set(invocation.getArgument(0));
            return response;
        });
        return response;
    }

    private static final class TestLogHandler extends Handler implements AutoCloseable {
        private final Logger logger;
        private final Level previousLevel;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<LogRecord> record = new AtomicReference<>();

        private TestLogHandler(Logger logger) {
            this.logger = logger;
            this.previousLevel = logger.getLevel();
            setLevel(Level.ALL);
        }

        static TestLogHandler install() {
            Logger logger = Logger.getLogger(OpenTelemetryMetricsHttpSemanticConventions.MetricsRecordingFilter.class.getName());
            TestLogHandler handler = new TestLogHandler(logger);
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
            return handler;
        }

        @Override
        public void publish(LogRecord record) {
            if (this.record.compareAndSet(null, record)) {
                latch.countDown();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(previousLevel);
        }

        private LogRecord await() throws InterruptedException {
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
            return record.get();
        }
    }
}
