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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.MetricsConfig;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPublisherConfig {

    @Test
    void testPrometheusConfig() {
        String configText = """
                metrics:
                  publishers:
                    - type: prometheus
                      name: one-second
                      step: "PT1S"
                    - type: prometheus
                      name: one-minute
                      step: "PT60S"
                """;

        var metricsConfig = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                         .get("metrics"));

        assertThat("Publishers",
                   metricsConfig.publishers(),
                   hasSize(2));
    }

    @Test
    void testMixed() {
        String configText = """
                metrics:
                  publishers:
                    - type: prometheus
                      name: one-second
                      step: "PT1S"
                    - type: otlp
                      name: my-otlp
                      url: "http://localhost:8080/somewhere"
                """;

        var metricsConfig = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                         .get("metrics"));

        assertThat("Publishers",
                   metricsConfig.publishers(),
                   hasSize(2));
    }

    @Test
    void verifyBriefPrometheusMention() {
        // Make sure that just mentioning a publisher type without any lower-level settings works.
        String configText = """
                metrics:
                  publishers:
                    otlp:
                    prometheus:
                """;

        var metricsConfig = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                         .get("metrics"));

        assertThat("Publishers",
                   metricsConfig.publishers(),
                   hasSize(2));
    }

    @Test
    void testPrometheusNamingConventionConfig() {
        String configText = """
                metrics:
                  publishers:
                    prometheus:
                      naming-convention:
                        timer-suffix: "_duration"
                        non-letter-prefix: "m_"
                """;

        var metricsConfig = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                         .get("metrics"));
        var publisher = (PrometheusPublisher) metricsConfig.publishers().getFirst();
        var namingConfig = publisher.prototype().namingConvention().orElseThrow();

        assertThat("Timer suffix", namingConfig.timerSuffix(), is("_duration"));
        assertThat("Non-letter prefix", namingConfig.nonLetterPrefix().orElseThrow(), is("m_"));

        PrometheusMeterRegistry registry = publisher.prometheusRegistry()
                .apply(_ -> null, new NoOpSpanContextSupplierProvider());
        try {
            registry.counter("1request").increment();
            assertThat("Config selects legacy naming",
                       registry.scrape(),
                       containsString("m_1request_total 1.0"));
        } finally {
            registry.close();
        }
    }

    @Test
    void testInvalidNonLetterPrefix() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> PrometheusPublisher.builder()
                                                           .namingConvention(builder -> builder.nonLetterPrefix("1_"))
                                                           .build());

        assertThat(ex.getMessage(), containsString("must match [A-Za-z][A-Za-z0-9_]*"));

        assertThrows(IllegalArgumentException.class,
                     () -> PrometheusPublisher.builder()
                             .namingConvention(builder -> builder.nonLetterPrefix(""))
                             .build());
    }

    @Test
    void testLegacyHistogramFlavorIsAcceptedAndIgnored() {
        String configText = """
                metrics:
                  prometheus:
                    histogramFlavor: VictoriaMetrics
                  publishers:
                    prometheus:
                """;

        var metricsConfig = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                         .get("metrics"));
        var publisher = (PrometheusPublisher) metricsConfig.publishers().getFirst();
        List<LogRecord> logRecords = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logRecords.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(PrometheusPublisher.class.getName());
        logger.addHandler(handler);

        PrometheusMeterRegistry registry = null;
        try {
            registry = publisher.prometheusRegistry()
                    .apply(key -> metricsConfig.lookupConfig(key).orElse(null), new NoOpSpanContextSupplierProvider());
            assertThat("Warning count", logRecords, hasSize(1));
            assertThat("Warning level", logRecords.getFirst().getLevel(), is(Level.WARNING));
            assertThat("Warning message",
                       logRecords.getFirst().getMessage(),
                       containsString("prometheus.histogramFlavor is no longer supported and is ignored"));
        } finally {
            if (registry != null) {
                registry.close();
            }
            logger.removeHandler(handler);
        }
    }
}
