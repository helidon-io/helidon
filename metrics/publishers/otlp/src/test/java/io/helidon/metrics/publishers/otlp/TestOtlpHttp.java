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

package io.helidon.metrics.publishers.otlp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.json.JsonParser;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.attributes;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.dataPoints;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.longValue;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.metric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class TestOtlpHttp {
    private static final Duration EXPORT_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void configuredPublisherExportsJsonAndFinalValues() throws Exception {
        try (Collector collector = new Collector(_ -> 200, "{}")) {
            Config config = Config.just(ConfigSources.create(Map.of(
                    "publishers.otlp.endpoint", collector.endpoint(),
                    "publishers.otlp.interval", "PT24H",
                    "publishers.otlp.timeout", "PT5S",
                    "publishers.otlp.service-name", "wire-test",
                    "publishers.otlp.headers.X-Test", "configured-header")));
            MetricsConfig metricsConfig = MetricsConfig.create(config);
            assertThat("Config discovers the native publisher", metricsConfig.publishers().getFirst(),
                       instanceOf(OtlpPublisher.class));

            HelidonMetricsFactory factory = HelidonMetricsFactory.builder().metricsConfig(metricsConfig).build();
            try {
                MeterRegistry registry = factory.globalRegistry();
                Counter counter = registry.getOrCreate(factory.counterBuilder("wire.counter"));
                counter.increment(7);
            } finally {
                factory.close();
            }

            Request received = collector.next();
            assertThat("OTLP media type", received.contentType(), is("application/json"));
            assertThat("Accepted response media type", received.accept(), is("application/json"));
            assertThat("Configured request header", received.testHeader(), is("configured-header"));
            var request = JsonParser.create(received.body()).readJsonObject();
            var resourceMetrics = request.arrayValue("resourceMetrics").orElseThrow().values().getFirst().asObject();
            assertThat("Configured service identity",
                       attributes(resourceMetrics.objectValue("resource").orElseThrow()).get("service.name"), is("wire-test"));
            var metric = metric(request, "wire.counter");
            assertThat("Final export precedes meter removal", longValue(dataPoints(metric, "sum").getFirst(), "asInt"), is(7L));
            assertThat("One final export", collector.requestCount(), is(1));
        }
    }

    @Test
    void transientFailureRetriesIdenticalPayload() throws Exception {
        try (Collector collector = new Collector(attempt -> attempt == 1 ? 503 : 200, "{}")) {
            var samples = new AtomicInteger();
            HelidonMetricsFactory factory = factory(collector, Duration.ofDays(1));
            try {
                factory.globalRegistry().getOrCreate(factory.counterBuilder("retry.counter")).increment(3);
                factory.globalRegistry().getOrCreate(factory.gaugeBuilder("retry.gauge", samples::incrementAndGet));
            } finally {
                factory.close();
            }
            Request first = collector.next();
            Request second = collector.next();
            assertThat("Retry preserves the cumulative snapshot", second.body(), is(first.body()));
            var request = JsonParser.create(first.body()).readJsonObject();
            assertThat("The original gauge sample is exported",
                       longValue(dataPoints(metric(request, "retry.gauge"), "gauge").getFirst(), "asInt"), is(1L));
            assertThat("Retry does not sample the gauge again", samples.get(), is(1));
            assertThat("Successful retry ends export", collector.requestCount(), is(2));
        }
    }

    @Test
    void permanentFailureIsNotRetried() throws Exception {
        try (Collector collector = new Collector(_ -> 400, "{}")) {
            HelidonMetricsFactory factory = factory(collector, Duration.ofDays(1));
            try {
                factory.globalRegistry().getOrCreate(factory.counterBuilder("rejected.counter")).increment();
            } finally {
                factory.close();
            }
            collector.next();
            assertThat("Permanent rejection must not retry", collector.requestCount(), is(1));
        }
    }

    @Test
    void partialSuccessIsNotRetried() throws Exception {
        String response = """
                {"partialSuccess":{"rejectedDataPoints":"1","errorMessage":"invalid attribute"}}
                """;
        try (Collector collector = new Collector(_ -> 200, response)) {
            HelidonMetricsFactory factory = factory(collector, Duration.ofDays(1));
            try {
                factory.globalRegistry().getOrCreate(factory.counterBuilder("partial.counter")).increment();
            } finally {
                factory.close();
            }
            collector.next();
            assertThat("Partial success must not retry", collector.requestCount(), is(1));
        }
    }

    @Test
    void periodicExportContinuesAfterPermanentFailure() throws Exception {
        try (Collector collector = new Collector(attempt -> attempt == 1 ? 400 : 200, "{}")) {
            HelidonMetricsFactory factory = factory(collector, Duration.ofMillis(50));
            try {
                factory.globalRegistry().getOrCreate(factory.counterBuilder("periodic.counter")).increment(4);
                collector.next();
                Request second = collector.next();
                var request = JsonParser.create(second.body()).readJsonObject();
                assertThat("Next interval retains cumulative value",
                           longValue(dataPoints(metric(request, "periodic.counter"), "sum").getFirst(), "asInt"), is(4L));
            } finally {
                factory.close();
            }
        }
    }

    @Test
    void disabledMetricsNeverExport() {
        try (Collector collector = new Collector(_ -> 200, "{}")) {
            OtlpPublisher publisher = OtlpPublisher.builder().endpoint(URI.create(collector.endpoint())).build();
            HelidonMetricsFactory factory = HelidonMetricsFactory.builder()
                    .metricsConfig(MetricsConfig.builder().enabled(false).addPublisher(publisher))
                    .build();
            try {
                factory.globalRegistry().getOrCreate(factory.counterBuilder("disabled.counter")).increment();
            } finally {
                factory.close();
            }
            assertThat("Disabled registry has no publisher session", collector.requestCount(), is(0));
        }
    }

    private static HelidonMetricsFactory factory(Collector collector, Duration interval) {
        return HelidonMetricsFactory.builder()
                .metricsConfig(MetricsConfig.builder().addPublisher(OtlpPublisher.builder()
                        .endpoint(URI.create(collector.endpoint()))
                        .interval(interval)
                        .timeout(EXPORT_TIMEOUT).build()))
                .build();
    }

    private record Request(byte[] body, String contentType, String accept, String testHeader) {
    }

    private static final class Collector implements AutoCloseable {
        private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final IntFunction<Integer> responseStatus;
        private final byte[] responseBody;
        private final WebServer server;

        private Collector(IntFunction<Integer> responseStatus, String responseBody) {
            this.responseStatus = responseStatus;
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
            server = WebServer.builder()
                    .port(0)
                    .shutdownHook(false)
                    .routing(routing -> routing.post("/v1/metrics", this::receive))
                    .build().start();
        }

        @Override
        public void close() {
            server.stop();
        }

        private String endpoint() {
            return "http://localhost:" + server.port() + "/v1/metrics";
        }

        private int requestCount() {
            return requestCount.get();
        }

        private Request next() throws InterruptedException {
            Request request = requests.poll(10, TimeUnit.SECONDS);
            assertThat("Collector received an OTLP request", request, notNullValue());
            return request;
        }

        private void receive(ServerRequest request, ServerResponse response) {
            byte[] body = request.content().as(byte[].class);
            requests.add(new Request(body,
                                     request.headers().value(HeaderNames.CONTENT_TYPE).orElse(""),
                                     request.headers().value(HeaderNames.ACCEPT).orElse(""),
                                     request.headers().value(HeaderNames.create("X-Test")).orElse("")));
            response.status(responseStatus.apply(requestCount.incrementAndGet()));
            response.header(HeaderNames.CONTENT_TYPE, "application/json");
            response.header(HeaderNames.RETRY_AFTER, "0");
            response.send(responseBody);
        }
    }
}
