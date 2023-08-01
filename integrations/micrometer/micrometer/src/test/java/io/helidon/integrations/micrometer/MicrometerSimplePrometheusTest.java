/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.micrometer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MicrometerSimplePrometheusTest {

    private static PrometheusMeterRegistry registry;

    private Timer timer1;
    private Counter counter1;
    private AtomicInteger gauge1;
    private DistributionSummary summary1;

    private static WebServer webServer;

    private Http1Client webClient;

    @BeforeAll
    static void prepAll() {
        MeterRegistryFactory factory = MeterRegistryFactory.builder()
                .enrollRegistry(registry, req -> {
                    // If there is no media type, assume text/plain which means, for us, Prometheus.
                    if (req.headers().bestAccepted(MediaTypes.TEXT_PLAIN).isPresent()
                            || req.query().first("type").orElse("").equals("prometheus")) {
                        return Optional.of(PrometheusHandler.create(registry));
                    } else {
                        return Optional.empty();
                    }
                })
                .build();
        MicrometerFeature.Builder builder = MicrometerFeature.builder()
                .meterRegistryFactorySupplier(factory);

        webServer = MicrometerTestUtil.startServer(builder);
    }

    @BeforeEach
    void prepTest() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        initSomeMetrics();
        webClient = Http1Client.builder()
                .baseUri("http://localhost:" + webServer.port())
                .get();
    }

    @Test
    public void checkViaMediaType() throws ExecutionException, InterruptedException {
        timer1.record(2L, TimeUnit.SECONDS);
        counter1.increment(3);
        gauge1.set(4);
        Http1ClientResponse response = webClient.get()
                .header(Header.ACCEPT, HttpMediaType.TEXT_PLAIN.toString())
                .path("/micrometer")
                .request();

        String promOutput = response.entity().as(String.class);

        assertThat("Unexpected HTTP status, response is: " + promOutput, response.status(), is(Http.Status.OK_200));
    }

    @Test
    public void checkViaQueryParam() throws ExecutionException, InterruptedException {
        timer1.record(2L, TimeUnit.SECONDS);
        counter1.increment(3);
        gauge1.set(4);
        Http1ClientResponse response = webClient.get()
                .header(Header.ACCEPT, MediaTypes.create(MediaTypes.TEXT_PLAIN.type(), "special").toString())
                .path("/micrometer")
                .queryParam("type", "prometheus")
                .request();

        assertThat("Unexpected HTTP status", response.status(), is(Http.Status.OK_200));

        String promOutput = response.entity().as(String.class);
    }

    @Test
    public void checkNoMatch() throws ExecutionException, InterruptedException {
        Http1ClientResponse response = webClient.get()
                .header(Header.ACCEPT, MediaTypes.create(MediaTypes.TEXT_PLAIN.type(), "special").toString())
                .path("/micrometer")
                .request();

        assertThat("Expected failed HTTP status", response.status(), is(Http.Status.NOT_ACCEPTABLE_406));
    }

    private void initSomeMetrics() {
        counter1 = registry.counter("ctr1", "app", "1");
        timer1 = registry.timer("timer1", "app", "1");
        gauge1 = registry.gauge("gauge1", new AtomicInteger(4));
        summary1 = registry.summary("summary1");
    }
}