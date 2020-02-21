/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletableFuture;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.metrics.RegistryFactory;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.metrics.ClientMetrics;
import io.helidon.webclient.spi.ClientService;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link ClientMetrics}.
 */
public class MetricsTest extends TestParent {

    private static final MetricRegistry FACTORY = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);

    @Test
    public void testCounter() throws Exception {
        ClientService serviceCounterAll = ClientMetrics.counter().nameFormat("counter.%1$s.%2$s").build();
        ClientService serviceCounterGet = ClientMetrics.counter()
                .methods(Http.Method.GET)
                .nameFormat("counter.get.%1$s.%2$s")
                .build();
        ClientService serviceCounterError = ClientMetrics.counter()
                .nameFormat("counter.error.%1$s.%2$s")
                .success(false)
                .build();
        ClientService serviceCounterSuccess = ClientMetrics.counter()
                .nameFormat("counter.success.%1$s.%2$s")
                .errors(false)
                .build();
        WebClient webClient = createNewClient(serviceCounterAll, serviceCounterGet, serviceCounterError, serviceCounterSuccess);
        Counter counterAll = FACTORY.counter("counter.GET.localhost");
        Counter counterGet = FACTORY.counter("counter.get.GET.localhost");
        Counter counterError = FACTORY.counter("counter.error.GET.localhost");
        Counter counterSuccess = FACTORY.counter("counter.success.GET.localhost");

        assertThat(counterAll.getCount(), is(0L));
        assertThat(counterGet.getCount(), is(0L));
        assertThat(counterError.getCount(), is(0L));
        assertThat(counterSuccess.getCount(), is(0L));
        webClient.get()
                .request(JsonObject.class)
                .exceptionally(throwable -> {
                    Assertions.fail(throwable);
                    return null;
                })
                .toCompletableFuture()
                .get();
        assertThat(counterAll.getCount(), is(1L));
        assertThat(counterGet.getCount(), is(1L));
        assertThat(counterError.getCount(), is(0L));
        assertThat(counterSuccess.getCount(), is(1L));
        webClient.get()
                .path("/error")
                .request()
                .thenCompose(response -> response.content().as(String.class))
                .exceptionally(throwable -> {
                    Assertions.fail(throwable);
                    return null;
                })
                .toCompletableFuture()
                .get();
        assertThat(counterAll.getCount(), is(2L));
        assertThat(counterGet.getCount(), is(2L));
        assertThat(counterError.getCount(), is(1L));
        assertThat(counterSuccess.getCount(), is(1L));
    }

    @Test
    public void testMeter() throws Exception {
        ClientService serviceMeterAll = ClientMetrics.meter().nameFormat("meter.%1$s.%2$s").build();
        ClientService serviceMeterGet = ClientMetrics.meter()
                .methods(Http.Method.GET)
                .nameFormat("meter.get.%1$s.%2$s")
                .build();
        ClientService serviceMeterError = ClientMetrics.meter()
                .nameFormat("meter.error.%1$s.%2$s")
                .success(false)
                .build();
        ClientService serviceMeterSuccess = ClientMetrics.meter()
                .nameFormat("meter.success.%1$s.%2$s")
                .errors(false)
                .build();
        WebClient webClient = createNewClient(serviceMeterAll, serviceMeterGet, serviceMeterError, serviceMeterSuccess);
        Meter meterAll = FACTORY.meter("meter.GET.localhost");
        Meter meterGet = FACTORY.meter("meter.get.GET.localhost");
        Meter meterError = FACTORY.meter("meter.error.GET.localhost");
        Meter meterSuccess = FACTORY.meter("meter.success.GET.localhost");

        assertThat(meterAll.getCount(), is(0L));
        assertThat(meterGet.getCount(), is(0L));
        assertThat(meterError.getCount(), is(0L));
        assertThat(meterSuccess.getCount(), is(0L));
        webClient.get()
                .request(JsonObject.class)
                .exceptionally(throwable -> {
                    Assertions.fail(throwable);
                    return null;
                })
                .toCompletableFuture()
                .get();
        assertThat(meterAll.getCount(), is(1L));
        assertThat(meterGet.getCount(), is(1L));
        assertThat(meterError.getCount(), is(0L));
        assertThat(meterSuccess.getCount(), is(1L));
        webClient.get()
                .path("/error")
                .request()
                .thenCompose(response -> response.content().as(String.class))
                .exceptionally(throwable -> {
                    Assertions.fail(throwable);
                    return null;
                })
                .toCompletableFuture()
                .get();
        assertThat(meterAll.getCount(), is(2L));
        assertThat(meterGet.getCount(), is(2L));
        assertThat(meterError.getCount(), is(1L));
        assertThat(meterSuccess.getCount(), is(1L));
    }

    @Test
    public void testGaugeInProgress() throws Exception {
        ConcurrentGauge progressAll = FACTORY.concurrentGauge("gauge.GET.localhost");
        ConcurrentGauge progressPut = FACTORY.concurrentGauge("gauge.put.PUT.localhost");
        ConcurrentGauge progressGet = FACTORY.concurrentGauge("gauge.get.GET.localhost");
        ClientService inProgressAll = ClientMetrics.gaugeInProgress().nameFormat("gauge.%1$s.%2$s").build();
        ClientService inProgressPut = ClientMetrics.gaugeInProgress()
                .methods(Http.Method.PUT)
                .nameFormat("gauge.put.%1$s.%2$s")
                .build();
        ClientService inProgressGet = ClientMetrics.gaugeInProgress()
                .methods(Http.Method.GET)
                .nameFormat("gauge.get.%1$s.%2$s")
                .build();

        ClientService clientService = request -> {
            request.whenSent()
                    .thenAccept(clientServiceRequest -> {
                        assertThat(progressAll.getCount(), is(1L));
                        assertThat(progressGet.getCount(), is(1L));
                        assertThat(progressPut.getCount(), is(0L));
                    });
            return CompletableFuture.completedFuture(request);
        };

        WebClient webClient = createNewClient(inProgressAll, inProgressPut, inProgressGet, clientService);

        assertThat(progressAll.getCount(), is(0L));
        assertThat(progressGet.getCount(), is(0L));
        assertThat(progressPut.getCount(), is(0L));
        webClient.get()
                .request(JsonObject.class)
                .toCompletableFuture()
                .get();
        assertThat(progressAll.getCount(), is(0L));
        assertThat(progressGet.getCount(), is(0L));
        assertThat(progressPut.getCount(), is(0L));
    }

}
