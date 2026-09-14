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
package io.helidon.metrics.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class TestMetricsConfigDeprecation {

    private static final Logger LOGGER = Logger.getLogger(MetricsConfigSupport.class.getName());

    private final TestHandler testHandler = new TestHandler();

    @BeforeEach
    void setUp() {
        MetricsFactory.closeAll();
        LOGGER.addHandler(testHandler);
    }

    @AfterEach
    void tearDown() {
        LOGGER.removeHandler(testHandler);
        MetricsFactory.closeAll();
    }

    @Test
    void warnsOnceAboutPrometheusHistogramFlavor() {
        MetricsConfig.create(Config.empty());
        assertThat("Deprecation warnings without the setting", testHandler.records(), empty());

        Config config = Config.just("""
                                            metrics:
                                              prometheus:
                                                histogramFlavor: VictoriaMetrics
                                            """,
                                    MediaTypes.APPLICATION_YAML)
                .get("metrics");

        MetricsFactoryManager.getMetricsFactory(config);
        assertThat("Deprecation warnings during factory initialization", testHandler.records(), hasSize(1));

        MetricsConfig metricsConfig = MetricsConfig.create(config);

        assertThat("Raw Prometheus setting",
                   metricsConfig.lookupConfig("prometheus.histogramFlavor"),
                   is(Optional.of("VictoriaMetrics")));
        assertThat("Deprecation warnings after a later config build", testHandler.records(), hasSize(1));
        LogRecord warning = testHandler.records().getFirst();
        assertThat("Warning level", warning.getLevel(), is(Level.WARNING));
        assertThat("Warning message",
                   warning.getMessage(),
                   allOf(containsString("metrics.prometheus.histogramFlavor"),
                         containsString("deprecated as of Helidon 4.5.5"),
                         containsString("future release of the Prometheus Java client will no longer honor it"),
                         containsString("future Helidon release will adopt that Prometheus client version")));
    }

    private static class TestHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<LogRecord> records() {
            return List.copyOf(records);
        }
    }
}
