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
package io.helidon.tests.integration.webclient;

import io.helidon.common.http.Http;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.nima.webclient.api.WebClientServiceResponse;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.metrics.WebClientMetrics;
import io.helidon.nima.webclient.spi.WebClientService;
import io.helidon.nima.webserver.WebServer;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link WebClientMetrics}.
 */
public class MetricsTest extends TestParent {

    private static final MetricRegistry FACTORY = RegistryFactory.getInstance().getRegistry(Registry.APPLICATION_SCOPE);

    MetricsTest(WebServer server) {
        super(server);
    }

    @Test
    public void testCounter() {
        WebClientService serviceCounterAll = WebClientMetrics.counter().nameFormat("counter.%1$s.%2$s").build();
        WebClientService serviceCounterGet = WebClientMetrics.counter()
                .methods(Http.Method.GET)
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
        Counter counterAll = FACTORY.counter("counter.GET.localhost");
        Counter counterGet = FACTORY.counter("counter.get.GET.localhost");
        Counter counterError = FACTORY.counter("counter.error.GET.localhost");
        Counter counterSuccess = FACTORY.counter("counter.success.GET.localhost");

        assertThat(counterAll.getCount(), is(0L));
        assertThat(counterGet.getCount(), is(0L));
        assertThat(counterError.getCount(), is(0L));
        assertThat(counterSuccess.getCount(), is(0L));

        webClient.get().request(String.class);
        assertThat(counterAll.getCount(), is(1L));
        assertThat(counterGet.getCount(), is(1L));
        assertThat(counterError.getCount(), is(0L));
        assertThat(counterSuccess.getCount(), is(1L));

        webClient.get("/error").request().close();

        assertThat(counterAll.getCount(), is(2L));
        assertThat(counterGet.getCount(), is(2L));
        assertThat(counterError.getCount(), is(1L));
        assertThat(counterSuccess.getCount(), is(1L));
    }

    @Test
    public void testMeter() {
        WebClientService serviceMeterAll = WebClientMetrics.meter()
                .nameFormat("meter.%1$s.%2$s")
                .build();
        WebClientService serviceMeterGet = WebClientMetrics.meter()
                .methods(Http.Method.GET)
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
        Counter meterAll = FACTORY.counter("meter.GET.localhost");
        Counter meterGet = FACTORY.counter("meter.get.GET.localhost");
        Counter meterError = FACTORY.counter("meter.error.GET.localhost");
        Counter meterSuccess = FACTORY.counter("meter.success.GET.localhost");

        assertThat(meterAll.getCount(), is(0L));
        assertThat(meterGet.getCount(), is(0L));
        assertThat(meterError.getCount(), is(0L));
        assertThat(meterSuccess.getCount(), is(0L));

        webClient.get().request(String.class);
        assertThat(meterAll.getCount(), is(1L));
        assertThat(meterGet.getCount(), is(1L));
        assertThat(meterError.getCount(), is(0L));
        assertThat(meterSuccess.getCount(), is(1L));

        webClient.get("/error").request().close();

        assertThat(meterAll.getCount(), is(2L));
        assertThat(meterGet.getCount(), is(2L));
        assertThat(meterError.getCount(), is(1L));
        assertThat(meterSuccess.getCount(), is(1L));
    }

    @Test
    public void testGaugeInProgress() {
        WebClientService inProgressAll = WebClientMetrics.gaugeInProgress().nameFormat("gauge.%1$s.%2$s").build();
        WebClientService inProgressPut = WebClientMetrics.gaugeInProgress()
                .methods(Http.Method.PUT)
                .nameFormat("gauge.put.%1$s.%2$s")
                .build();
        WebClientService inProgressGet = WebClientMetrics.gaugeInProgress()
                .methods(Http.Method.GET)
                .nameFormat("gauge.get.%1$s.%2$s")
                .build();

        WebClientService clientService = (chain, request) -> {
            WebClientServiceResponse response = chain.proceed(request);
            assertThat(FACTORY.gauge("gauge.GET.localhost", () -> 0L).getValue(), is(1L));
            assertThat(FACTORY.gauge("gauge.get.GET.localhost", () -> 0L).getValue(), is(1L));
            assertThat(FACTORY.gauge("gauge.put.PUT.localhost", () -> 0L).getValue(), is(0L));
            return response;
        };

        Http1Client webClient = createNewClient(inProgressAll, inProgressPut, inProgressGet, clientService);

        // ensure the metrics are created
        webClient.get().request().close();

        Gauge<Long> progressAll = FACTORY.gauge("gauge.GET.localhost", () -> 0L);
        Gauge<Long> progressGet = FACTORY.gauge("gauge.get.GET.localhost", () -> 0L);
        Gauge<Long> progressPut = FACTORY.gauge("gauge.put.PUT.localhost", () -> 0L);

        assertThat(progressAll.getValue(), is(0L));
        assertThat(progressGet.getValue(), is(0L));
        assertThat(progressPut.getValue(), is(0L));

        webClient.get().request().close();

        assertThat(progressAll.getValue(), is(0L));
        assertThat(progressGet.getValue(), is(0L));
        assertThat(progressPut.getValue(), is(0L));
    }

    @Test
    public void testErrorHandling() {
        WebClientService errorAll = WebClientMetrics.counter()
                .success(false)
                .nameFormat("counter.all.errors.%2$s")
                .build();
        WebClientService errorGet = WebClientMetrics.counter()
                .methods(Http.Method.GET)
                .success(false)
                .nameFormat("counter.errors.%1$s.%2$s")
                .build();
        WebClientService errorPut = WebClientMetrics.counter()
                .methods(Http.Method.PUT)
                .success(false)
                .nameFormat("counter.errors.%1$s.%2$s")
                .build();

        Http1Client webClient = createNewClient(errorAll, errorGet, errorPut);

        Counter counterAll = FACTORY.counter("counter.all.errors.localhost");
        Counter counterGet = FACTORY.counter("counter.errors.GET.localhost");
        Counter counterPut = FACTORY.counter("counter.errors.PUT.localhost");

        assertThat(counterAll.getCount(), is(0L));
        assertThat(counterGet.getCount(), is(0L));
        assertThat(counterPut.getCount(), is(0L));

        webClient.get("/invalid").request().close();

        assertThat(counterAll.getCount(), is(1L));
        assertThat(counterGet.getCount(), is(1L));
        assertThat(counterPut.getCount(), is(0L));

        webClient.put("/invalid").request().close();

        assertThat(counterAll.getCount(), is(2L));
        assertThat(counterGet.getCount(), is(1L));
        assertThat(counterPut.getCount(), is(1L));
    }

}
