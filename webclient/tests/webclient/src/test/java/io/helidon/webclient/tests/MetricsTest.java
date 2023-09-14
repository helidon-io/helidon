/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.webclient.tests;

import io.helidon.http.Method;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.metrics.WebClientMetrics;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link WebClientMetrics}.
 */
public class MetricsTest extends TestParent {

    private static final MeterRegistry REGISTRY = Metrics.globalRegistry();

    MetricsTest(WebServer server) {
        super(server);
    }

    @Test
    public void testCounter() {
        WebClientService serviceCounterAll = WebClientMetrics.counter().nameFormat("counter.%1$s.%2$s").build();
        WebClientService serviceCounterGet = WebClientMetrics.counter()
                .methods(Method.GET)
                .nameFormat("counter.get.%1$s.%2$s")
                .build();
        WebClientService serviceCounterError = WebClientMetrics.counter()
                .nameFormat("counter.error.%1$s.%2$s")
                .success(false)
                .build();
        WebClientService serviceCounterSuccess = WebClientMetrics.counter()
                .nameFormat("counter.success.%1$s.%2$s")
                .errors(false)
                .build();
        Http1Client webClient = createNewClient(serviceCounterAll, serviceCounterGet, serviceCounterError, serviceCounterSuccess);
        Counter counterAll = REGISTRY.getOrCreate(Counter.builder("counter.GET.localhost"));
        Counter counterGet = REGISTRY.getOrCreate(Counter.builder("counter.get.GET.localhost"));
        Counter counterError = REGISTRY.getOrCreate(Counter.builder("counter.error.GET.localhost"));
        Counter counterSuccess = REGISTRY.getOrCreate(Counter.builder("counter.success.GET.localhost"));

        assertThat(counterAll.count(), is(0L));
        assertThat(counterGet.count(), is(0L));
        assertThat(counterError.count(), is(0L));
        assertThat(counterSuccess.count(), is(0L));

        webClient.get().request(String.class);
        assertThat(counterAll.count(), is(1L));
        assertThat(counterGet.count(), is(1L));
        assertThat(counterError.count(), is(0L));
        assertThat(counterSuccess.count(), is(1L));

        webClient.get("/error").request().close();

        assertThat(counterAll.count(), is(2L));
        assertThat(counterGet.count(), is(2L));
        assertThat(counterError.count(), is(1L));
        assertThat(counterSuccess.count(), is(1L));
    }

    @Test
    public void testMeter() {
        WebClientService serviceMeterAll = WebClientMetrics.meter()
                .nameFormat("meter.%1$s.%2$s")
                .build();
        WebClientService serviceMeterGet = WebClientMetrics.meter()
                .methods(Method.GET)
                .nameFormat("meter.get.%1$s.%2$s")
                .build();
        WebClientService serviceMeterError = WebClientMetrics.meter()
                .nameFormat("meter.error.%1$s.%2$s")
                .success(false)
                .build();
        WebClientService serviceMeterSuccess = WebClientMetrics.meter()
                .nameFormat("meter.success.%1$s.%2$s")
                .errors(false)
                .build();
        Http1Client webClient = createNewClient(serviceMeterAll, serviceMeterGet, serviceMeterError, serviceMeterSuccess);
        Counter meterAll = REGISTRY.getOrCreate(Counter.builder("meter.GET.localhost"));
        Counter meterGet = REGISTRY.getOrCreate(Counter.builder("meter.get.GET.localhost"));
        Counter meterError = REGISTRY.getOrCreate(Counter.builder("meter.error.GET.localhost"));
        Counter meterSuccess = REGISTRY.getOrCreate(Counter.builder("meter.success.GET.localhost"));

        assertThat(meterAll.count(), is(0L));
        assertThat(meterGet.count(), is(0L));
        assertThat(meterError.count(), is(0L));
        assertThat(meterSuccess.count(), is(0L));

        webClient.get().request(String.class);
        assertThat(meterAll.count(), is(1L));
        assertThat(meterGet.count(), is(1L));
        assertThat(meterError.count(), is(0L));
        assertThat(meterSuccess.count(), is(1L));

        webClient.get("/error").request().close();

        assertThat(meterAll.count(), is(2L));
        assertThat(meterGet.count(), is(2L));
        assertThat(meterError.count(), is(1L));
        assertThat(meterSuccess.count(), is(1L));
    }

    @Test
    public void testGaugeInProgress() {
        WebClientService inProgressAll = WebClientMetrics.gaugeInProgress().nameFormat("gauge.%1$s.%2$s").build();
        WebClientService inProgressPut = WebClientMetrics.gaugeInProgress()
                .methods(Method.PUT)
                .nameFormat("gauge.put.%1$s.%2$s")
                .build();
        WebClientService inProgressGet = WebClientMetrics.gaugeInProgress()
                .methods(Method.GET)
                .nameFormat("gauge.get.%1$s.%2$s")
                .build();

        WebClientService clientService = (chain, request) -> {
            WebClientServiceResponse response = chain.proceed(request);
            assertThat(REGISTRY.getOrCreate(Gauge.builder("gauge.GET.localhost", () -> 0L)).value(), is(1L));
            assertThat(REGISTRY.getOrCreate(Gauge.builder("gauge.get.GET.localhost", () -> 0L)).value(), is(1L));
            assertThat(REGISTRY.getOrCreate(Gauge.builder("gauge.put.PUT.localhost", () -> 0L)).value(), is(0L));
            return response;
        };

        Http1Client webClient = createNewClient(inProgressAll, inProgressPut, inProgressGet, clientService);

        // ensure the metrics are created
        webClient.get().request().close();

        Gauge<Long> progressAll = REGISTRY.getOrCreate(Gauge.builder("gauge.GET.localhost", () -> 0L));
        Gauge<Long> progressGet = REGISTRY.getOrCreate(Gauge.builder("gauge.get.GET.localhost", () -> 0L));
        Gauge<Long> progressPut = REGISTRY.getOrCreate(Gauge.builder("gauge.put.PUT.localhost", () -> 0L));

        assertThat(progressAll.value(), is(0L));
        assertThat(progressGet.value(), is(0L));
        assertThat(progressPut.value(), is(0L));

        webClient.get().request().close();

        assertThat(progressAll.value(), is(0L));
        assertThat(progressGet.value(), is(0L));
        assertThat(progressPut.value(), is(0L));
    }

    @Test
    public void testErrorHandling() {
        WebClientService errorAll = WebClientMetrics.counter()
                .success(false)
                .nameFormat("counter.all.errors.%2$s")
                .build();
        WebClientService errorGet = WebClientMetrics.counter()
                .methods(Method.GET)
                .success(false)
                .nameFormat("counter.errors.%1$s.%2$s")
                .build();
        WebClientService errorPut = WebClientMetrics.counter()
                .methods(Method.PUT)
                .success(false)
                .nameFormat("counter.errors.%1$s.%2$s")
                .build();

        Http1Client webClient = createNewClient(errorAll, errorGet, errorPut);

        Counter counterAll = REGISTRY.getOrCreate(Counter.builder("counter.all.errors.localhost"));
        Counter counterGet = REGISTRY.getOrCreate(Counter.builder("counter.errors.GET.localhost"));
        Counter counterPut = REGISTRY.getOrCreate(Counter.builder("counter.errors.PUT.localhost"));

        assertThat(counterAll.count(), is(0L));
        assertThat(counterGet.count(), is(0L));
        assertThat(counterPut.count(), is(0L));

        webClient.get("/invalid").request().close();

        assertThat(counterAll.count(), is(1L));
        assertThat(counterGet.count(), is(1L));
        assertThat(counterPut.count(), is(0L));

        webClient.put("/invalid").request().close();

        assertThat(counterAll.count(), is(2L));
        assertThat(counterGet.count(), is(1L));
        assertThat(counterPut.count(), is(1L));
    }

}
