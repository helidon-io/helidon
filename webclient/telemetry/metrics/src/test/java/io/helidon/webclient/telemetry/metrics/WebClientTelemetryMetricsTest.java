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

package io.helidon.webclient.telemetry.metrics;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebClientTelemetryMetricsTest {
    @Test
    void recordsRequestDurationInSeconds() {
        ServiceRegistry originalRegistry = GlobalServiceRegistry.registry();
        OpenTelemetry openTelemetry = mock(OpenTelemetry.class);
        MeterProvider meterProvider = mock(MeterProvider.class);
        Meter meter = mock(Meter.class);
        DoubleHistogramBuilder histogramBuilder = mock(DoubleHistogramBuilder.class);
        DoubleHistogram histogram = mock(DoubleHistogram.class);
        when(openTelemetry.getMeterProvider()).thenReturn(meterProvider);
        when(meterProvider.get("test")).thenReturn(meter);
        when(meter.histogramBuilder(WebClientTelemetryMetrics.REQUEST_DURATION)).thenReturn(histogramBuilder);
        when(histogramBuilder.setDescription("Outbound HTTP request duration")).thenReturn(histogramBuilder);
        when(histogramBuilder.setUnit("s")).thenReturn(histogramBuilder);
        when(histogramBuilder.setExplicitBucketBoundariesAdvice(any())).thenReturn(histogramBuilder);
        when(histogramBuilder.build()).thenReturn(histogram);
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, Config.just(ConfigSources.create(Map.of("telemetry.service", "test"))))
                .putContractInstance(OpenTelemetry.class, openTelemetry)
                .build());
        Services.registry(manager.registry());
        try {
            WebClientTelemetryMetrics service = WebClientTelemetryMetrics.create();
            WebClientServiceRequest request = request();
            WebClientServiceResponse successResponse = response(Status.OK_200);
            WebClientServiceResponse errorResponse = response(Status.NOT_FOUND_404);
            IllegalStateException failure = new IllegalStateException("Request failed");
            long startTime = System.nanoTime();
            assertThat(service.handle(ignored -> {
                delayRequest();
                return successResponse;
            }, request), sameInstance(successResponse));
            assertThat(service.handle(ignored -> {
                delayRequest();
                return errorResponse;
            }, request), sameInstance(errorResponse));
            assertThat(assertThrows(IllegalStateException.class,
                                    () -> service.handle(ignored -> {
                                        delayRequest();
                                        throw failure;
                                    }, request)), sameInstance(failure));
            double elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
            ArgumentCaptor<Double> durations = ArgumentCaptor.forClass(Double.class);
            ArgumentCaptor<Attributes> attributes = ArgumentCaptor.forClass(Attributes.class);
            verify(histogram, times(3)).record(durations.capture(), attributes.capture());
            assertThat("Each request takes at least one millisecond within the measured interval, in seconds",
                       durations.getAllValues(), everyItem(allOf(greaterThanOrEqualTo(0.001),
                                                                 lessThanOrEqualTo(elapsedSeconds))));
            assertThat(attributes.getAllValues(), is(List.of(attributes(200, ""),
                                                             attributes(404, "404"),
                                                             attributes(0, "IllegalStateException"))));
            verify(histogramBuilder).setUnit("s");
        } finally {
            Services.registry(originalRegistry);
            manager.shutdown();
        }
    }

    private static Attributes attributes(int status, String errorType) {
        return Attributes.builder()
                .put(WebClientTelemetryMetrics.HTTP_REQUEST_METHOD, "GET")
                .put(WebClientTelemetryMetrics.SERVER_ADDRESS, "localhost")
                .put(WebClientTelemetryMetrics.SERVER_PORT, 8080)
                .put(WebClientTelemetryMetrics.ERROR_TYPE, errorType)
                .put(WebClientTelemetryMetrics.HTTP_RESPONSE_STATUS_CODE, status)
                .put(WebClientTelemetryMetrics.URL_SCHEME, "http")
                .put(WebClientTelemetryMetrics.URL_TEMPLATE, "/metrics")
                .build();
    }

    private static WebClientServiceRequest request() {
        WebClientServiceRequest request = mock(WebClientServiceRequest.class);
        ClientUri uri = ClientUri.create(URI.create("http://localhost:8080/metrics"));
        when(request.method()).thenReturn(Method.GET);
        when(request.uri()).thenReturn(uri);
        return request;
    }

    private static WebClientServiceResponse response(Status status) {
        WebClientServiceResponse response = mock(WebClientServiceResponse.class);
        when(response.status()).thenReturn(status);
        return response;
    }

    private static void delayRequest() {
        long startTime = System.nanoTime();
        do {
            LockSupport.parkNanos(1_000_000);
        } while (System.nanoTime() - startTime < 1_000_000);
    }
}
