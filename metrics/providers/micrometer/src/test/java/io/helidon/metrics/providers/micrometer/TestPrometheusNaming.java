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

package io.helidon.metrics.providers.micrometer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.expositionformats.OpenMetricsTextFormatWriter;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPrometheusNaming {

    @Test
    void testNewClientNamingByDefault() {
        PrometheusMeterRegistry registry = registry(PrometheusPublisher.create());
        try {
            registry.counter("1request", "1tag", "value").increment();

            String output = scrape(registry);

            assertThat(output,
                       allOf(containsString("_request_total{_tag=\"value\"} 1.0"),
                             not(containsString("m_1request"))));
        } finally {
            registry.close();
        }

        registry = registry(PrometheusPublisher.create());
        try {
            registry.counter("_request", "_tag", "value").increment();
            Gauge.builder("queue_total", () -> 3).register(registry);
            registry.counter("requests_created").increment();

            String output = scrape(registry);

            assertThat(output,
                       allOf(containsString("_request_total{_tag=\"value\"} 1.0"),
                             containsString("queue 3.0"),
                             containsString("requests_total 1.0"),
                             not(containsString("queue_total 3.0")),
                             not(containsString("requests_created_total"))));
        } finally {
            registry.close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLegacyCompatibleNaming() {
        PrometheusPublisher publisher = PrometheusPublisher.builder()
                .namingConvention(PrometheusNamingConventionConfig.builder()
                                          .nonLetterPrefix("m_")
                                          .build())
                .build();

        PrometheusMeterRegistry registry = registry(publisher);
        try {
            AtomicLong functionCount = new AtomicLong(2);
            registry.counter("1request", "1tag", "value").increment();
            FunctionCounter.builder("_function", functionCount, AtomicLong::get).register(registry);
            Gauge.builder("queue_total", () -> 3).register(registry);
            Gauge.builder("queue_created", () -> 5).register(registry);
            Gauge.builder("queue_bucket", () -> 6).register(registry);
            Gauge.builder("queue_info", () -> 7).register(registry);
            Gauge.builder("build.info", () -> 4).register(registry);
            registry.counter("requests_created").increment();
            registry.counter("requests_total").increment();
            Timer timer = registry.timer("latency");
            timer.record(Duration.ofSeconds(1));
            DistributionSummary summary = registry.summary("payload");
            summary.record(5);

            String output = scrape(registry);

            assertThat(output,
                       allOf(containsString("m_1request_total{m_1tag=\"value\"} 1.0"),
                             containsString("m__function_total 2.0"),
                             containsString("queue_total 3.0"),
                             containsString("queue_created 5.0"),
                             containsString("queue_bucket 6.0"),
                             containsString("queue_info 7.0"),
                             containsString("build_info 4.0"),
                             containsString("requests_created_total 1.0"),
                             containsString("requests_total 1.0"),
                             not(containsString("requests_total_total")),
                             containsString("latency_seconds_count 1"),
                             containsString("latency_seconds_sum 1.0"),
                             containsString("payload_count 1"),
                             containsString("payload_sum 5.0")));
        } finally {
            registry.close();
        }
    }

    @Test
    void testCustomTimerSuffix() {
        PrometheusPublisher publisher = PrometheusPublisher.builder()
                .namingConvention(builder -> builder.timerSuffix("_duration")
                        .nonLetterPrefix("m_"))
                .build();

        PrometheusMeterRegistry registry = registry(publisher);
        try {
            registry.timer("latency_duration").record(Duration.ofSeconds(1));

            assertThat(scrape(registry), containsString("latency_duration_seconds_count 1"));
        } finally {
            registry.close();
        }
    }

    @Test
    void testCustomLegacyPrefix() {
        PrometheusPublisher publisher = PrometheusPublisher.builder()
                .namingConvention(builder -> builder.nonLetterPrefix("legacy_"))
                .build();

        PrometheusMeterRegistry registry = registry(publisher);
        try {
            registry.counter("1request").increment();

            assertThat(scrape(registry), containsString("legacy_1request_total 1.0"));
        } finally {
            registry.close();
        }
    }

    @Test
    void testLegacyInfoGaugeCollisionIsRejected() {
        PrometheusPublisher publisher = PrometheusPublisher.builder()
                .namingConvention(builder -> builder.nonLetterPrefix("m_"))
                .build();

        PrometheusMeterRegistry registry = registry(publisher).throwExceptionOnRegistrationFailure();
        try {
            Gauge.builder("build.info", () -> 1)
                    .tag("source", "dot")
                    .register(registry);

            assertThrows(IllegalArgumentException.class,
                         () -> Gauge.builder("build_info", () -> 2)
                                 .tag("source", "underscore")
                                 .register(registry));
        } finally {
            registry.close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLegacySupportedMeterOutputInBothFormats() {
        PrometheusPublisher publisher = PrometheusPublisher.builder()
                .namingConvention(builder -> builder.nonLetterPrefix("m_"))
                .build();
        PrometheusMeterRegistry registry = registry(publisher);
        try {
            io.micrometer.core.instrument.Counter.builder("jobs")
                    .baseUnit("tasks")
                    .description("Completed jobs")
                    .tag("kind", "batch")
                    .register(registry)
                    .increment(2);
            Gauge.builder("temperature", () -> 21)
                    .baseUnit("celsius")
                    .description("Current temperature")
                    .tag("kind", "batch")
                    .register(registry);
            Timer timer = Timer.builder("latency")
                    .description("Request latency")
                    .tag("kind", "batch")
                    .publishPercentileHistogram()
                    .register(registry);
            timer.record(Duration.ofSeconds(1));
            DistributionSummary summary = DistributionSummary.builder("payload")
                    .baseUnit("bytes")
                    .description("Payload size")
                    .tag("kind", "batch")
                    .publishPercentileHistogram()
                    .register(registry);
            summary.record(5);

            String prometheusText = registry.scrape(PrometheusTextFormatWriter.CONTENT_TYPE);
            String openMetricsText = scrape(registry);

            for (String output : new String[] {prometheusText, openMetricsText}) {
                assertThat(output,
                           allOf(containsString("# HELP jobs_tasks"),
                                 containsString("jobs_tasks_total{kind=\"batch\"} 2.0"),
                                 not(containsString("jobs_tasks_created")),
                                 containsString("temperature_celsius{kind=\"batch\"} 21.0"),
                                 containsString("latency_seconds_count{kind=\"batch\"} 1"),
                                 containsString("latency_seconds_sum{kind=\"batch\"} 1.0"),
                                 containsString("latency_seconds_max{kind=\"batch\"} 1.0"),
                                 containsString("latency_seconds_bucket{"),
                                 containsString("payload_bytes_count{kind=\"batch\"} 1"),
                                 containsString("payload_bytes_sum{kind=\"batch\"} 5.0"),
                                 containsString("payload_bytes_max{kind=\"batch\"} 5.0"),
                                 containsString("payload_bytes_bucket{")));
            }
            assertThat(openMetricsText, containsString("# EOF\n"));
            assertThat(prometheusText, not(containsString("# EOF")));
        } finally {
            registry.close();
        }
    }

    @Test
    void testExemplarUsesNewSpanContextApi() {
        AtomicBoolean markedAsExemplar = new AtomicBoolean();
        SpanContext spanContext = new SpanContext() {
            @Override
            public String getCurrentTraceId() {
                return "0123456789abcdef0123456789abcdef";
            }

            @Override
            public String getCurrentSpanId() {
                return "0123456789abcdef";
            }

            @Override
            public boolean isCurrentSpanSampled() {
                return true;
            }

            @Override
            public void markCurrentSpanAsExemplar() {
                markedAsExemplar.set(true);
            }
        };
        SpanContextSupplierProvider provider = () -> spanContext;
        PrometheusMeterRegistry registry = PrometheusPublisher.create()
                .prometheusRegistry()
                .apply(_ -> null, provider);
        try {
            registry.counter("exemplar.counter").increment();

            assertThat(scrape(registry),
                       allOf(containsString("trace_id=\"0123456789abcdef0123456789abcdef\""),
                             containsString("span_id=\"0123456789abcdef\"")));
            assertThat("Current span was marked as an exemplar", markedAsExemplar.get());
        } finally {
            registry.close();
        }
    }

    private static PrometheusMeterRegistry registry(PrometheusPublisher publisher) {
        return publisher.prometheusRegistry().apply(_ -> null, new NoOpSpanContextSupplierProvider());
    }

    private static String scrape(PrometheusMeterRegistry registry) {
        return registry.scrape(OpenMetricsTextFormatWriter.CONTENT_TYPE);
    }
}
