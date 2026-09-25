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
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.Size;
import io.helidon.http.HeaderNames;
import io.helidon.json.JsonParser;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.metrics.providers.helidon.HelidonMetricsPublisher;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.attributes;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.dataPoints;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.longValue;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.metrics;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

@Isolated
class TestOtlpRequestSize {
    private static final String SERVICE_NAME = "size-test";
    private static final String ESCAPED_TEXT = "Příliš žluťoučký 🦊 \"quoted\"\\path\nline\tend";
    private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
    private final Logger logger = Logger.getLogger(OtlpSession.class.getName());
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                events.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private Level previousLevel;

    @BeforeEach
    void captureWarnings() {
        previousLevel = logger.getLevel();
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
    }

    @AfterEach
    void restoreLogging() {
        logger.removeHandler(handler);
        logger.setLevel(previousLevel);
        handler.close();
    }

    @Test
    void finalExportAcceptsExactAndLargerLimits() throws Exception {
        for (int extraBytes : List.of(0, 1)) {
            try (var fixture = new Fixture()) {
                int size = fixture.requestSize();
                try (var session = fixture.start(size + extraBytes, Duration.ofDays(1))) {
                    // Closing the session triggers its only export.
                }
                assertThat("One final export", fixture.requests.get(), is(1));
                byte[] received = received(nextEvent());
                assertThat("The limit includes the complete UTF-8 JSON body", received.length, is(size));
                assertCounter(received);
                assertThat("Accepted exports produce no warning", events, empty());
            }
        }
    }

    @Test
    void finalExportDiscardsRequestOneByteAboveLimit() throws Exception {
        try (var fixture = new Fixture()) {
            int size = fixture.requestSize();
            try (var session = fixture.start(size - 1, Duration.ofDays(1))) {
                // Shutdown waits for the export, so no delay is needed to prove that nothing was sent.
            }
            assertThat("An oversized final export never reaches HTTP", fixture.requests.get(), is(0));
            assertDiscard(events.poll(), size, size - 1);
            assertThat("Discard completes without retrying", events, empty());
        }
    }

    @Test
    void periodicExportRecoversWhenRegistryBecomesSmaller() throws Exception {
        try (var fixture = new Fixture()) {
            int limit = fixture.requestSize();
            var extra = fixture.registry.getOrCreate(fixture.factory.counterBuilder("extra.counter")
                                                            .description("x".repeat(256)));
            extra.increment(11);
            int oversized = fixture.requestSize();
            assertThat("The additional meter exceeds the limit", limit, lessThan(oversized));
            try (var session = fixture.start(limit, Duration.ofMillis(50))) {
                assertDiscard(nextEvent(), oversized, limit);
                assertThat("The discarded periodic request never reaches HTTP", fixture.requests.get(), is(0));
                fixture.registry.remove(extra);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                Object event;
                do {
                    event = events.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    assertThat("A later interval exports the smaller registry", event, notNullValue());
                    if (event instanceof LogRecord) {
                        // A collection already in progress can still contain the just-removed meter.
                        assertDiscard(event, oversized, limit);
                    }
                } while (event instanceof LogRecord);
                byte[] received = received(event);
                assertThat("Later intervals obey the same limit", received.length, is(limit));
                assertCounter(received);
            }
        }
    }

    private Object nextEvent() throws InterruptedException {
        Object event = events.poll(5, TimeUnit.SECONDS);
        assertThat("Publisher emitted a warning or collector received a request", event, notNullValue());
        return event;
    }

    private static byte[] received(Object event) {
        assertThat("Collector received an accepted export", event, instanceOf(byte[].class));
        return (byte[]) event;
    }

    private static void assertDiscard(Object event, int size, int limit) {
        assertThat("An oversized export emits a warning instead of HTTP", event, instanceOf(LogRecord.class));
        var warning = (LogRecord) event;
        assertThat("Discard diagnostic supplies its byte counts", warning.getParameters(), notNullValue());
        assertThat("The diagnostic identifies the serialized byte count",
                   ((Number) warning.getParameters()[0]).longValue(), is((long) size));
        assertThat("The diagnostic identifies the permitted byte count",
                   ((Number) warning.getParameters()[1]).longValue(), is((long) limit));
    }

    private static void assertCounter(byte[] bytes) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat("The request contains multibyte UTF-8 characters", bytes.length, greaterThan(json.length()));
        assertThat("JSON quotes are escaped", json, containsString("\\\"quoted\\\""));
        assertThat("JSON backslashes are escaped", json, containsString("\\\\path"));
        assertThat("JSON newlines are escaped", json, containsString("\\nline"));
        assertThat("JSON tabs are escaped", json, containsString("\\tend"));
        var request = JsonParser.create(bytes).readJsonObject();
        var resource = request.arrayValue("resourceMetrics").orElseThrow().values().getFirst().asObject();
        assertThat(resource.arrayValue("scopeMetrics").orElseThrow().size(), is(1));
        assertThat("Resource attributes survive UTF-8 and escaping",
                   attributes(resource.objectValue("resource").orElseThrow()).get("region"), is(ESCAPED_TEXT));
        var metrics = metrics(request);
        assertThat(metrics.stream().map(metric -> metric.stringValue("name").orElseThrow()).toList(), contains("size.counter"));
        var metric = metrics.getFirst();
        assertThat("Descriptions survive UTF-8 and escaping", metric.stringValue("description").orElseThrow(), is(ESCAPED_TEXT));
        var point = dataPoints(metric, "sum").getFirst();
        assertThat(longValue(point, "asInt"), is(7L));
        assertThat("Meter attributes survive UTF-8 and escaping", attributes(point).get("label"), is(ESCAPED_TEXT));
    }

    private final class Fixture implements AutoCloseable {
        private final HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        private final MetricsConfig metricsConfig = MetricsConfig.create();
        private final MeterRegistry registry = factory.createMeterRegistry(metricsConfig);
        private final AtomicInteger requests = new AtomicInteger();
        private final WebServer server;

        private Fixture() {
            registry.getOrCreate(factory.counterBuilder("size.counter")
                                         .description(ESCAPED_TEXT)
                                         .addTag(factory.tagCreate("label", ESCAPED_TEXT))).increment(7);
            server = WebServer.builder()
                    .port(0)
                    .shutdownHook(false)
                    .routing(routing -> routing.post("/v1/metrics", (request, response) -> {
                        byte[] body = request.content().as(byte[].class);
                        requests.incrementAndGet();
                        events.add(body);
                        response.header(HeaderNames.CONTENT_TYPE, "application/json");
                        response.send("{}");
                    }))
                    .build().start();
        }

        @Override
        public void close() {
            try {
                factory.close();
            } finally {
                server.stop();
            }
        }

        private int requestSize() throws InterruptedException {
            try (var _ = start(Integer.MAX_VALUE, Duration.ofDays(1))) {
                // Measure actual wire bytes without depending on the encoder's JSON model or serializer.
            }
            byte[] body = received(nextEvent());
            assertThat("The measurement exports once", requests.get(), is(1));
            requests.set(0);
            return body.length;
        }

        private HelidonMetricsPublisher.Session start(int maxRequestBytes, Duration interval) {
            return OtlpPublisher.builder()
                    .endpoint(URI.create("http://localhost:" + server.port() + "/v1/metrics"))
                    .interval(interval)
                    .timeout(Duration.ofSeconds(5))
                    .serviceName(SERVICE_NAME)
                    .resourceAttributes(Map.of("region", ESCAPED_TEXT))
                    .maxRequestSize(Size.create(maxRequestBytes))
                    .build()
                    .start(registry, metricsConfig);
        }
    }
}
