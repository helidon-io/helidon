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
package io.helidon.microprofile.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.telemetry.spi.HelidonTelemetryContainerFilterHelper;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tracing.Tracer;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@HelidonTest
@AddBean(TestSpanExporter.class)
@AddConfig(key = "otel.traces.exporter", value = "in-memory")
class DeprecatedSpanSettingsTest {
    private static final List<String> PROPERTIES = List.of("telemetry.span.full.url",
                                                          "telemetry.span.name-includes-method",
                                                          "telemetry.span.includes-response-write");
    private static final Logger LOGGER = Logger.getLogger(HelidonTelemetryContainerFilter.class.getName());

    @Inject
    private Tracer tracer;

    @Inject
    private Instance<HelidonTelemetryContainerFilterHelper> helpers;

    @AfterEach
    void restoreFullUrlSetting() {
        // The filter stores the full-URL setting statically; restore it for other tests.
        createFilter(Map.of("telemetry.span.full.url", "false"));
    }

    @Test
    void warnsOncePerSettingAcrossConcurrentAndRepeatedInstances() throws Exception {
        try (var handler = new ConcurrentLogHandler()) {
            createFilter(Map.of());
            assertThat("Unset properties do not log deprecation warnings", deprecationWarnings(handler), hasSize(0));

            for (int i = 0; i < PROPERTIES.size(); i++) {
                String property = PROPERTIES.get(i);
                // Exercise both Boolean values on first use, including explicitly configured defaults.
                createFiltersConcurrently(Map.of(property, i % 2 == 0 ? "false" : "true"));
                createFilter(Map.of(property, "true"));
                createFilter(Map.of(property, "false"));

                var warnings = deprecationWarnings(handler);
                assertThat("Only newly encountered properties log warnings", warnings, hasSize(i + 1));
                String message = warnings.get(i);
                for (String candidate : PROPERTIES) {
                    var matcher = containsString("Configuration property '" + candidate + "' is deprecated");
                    assertThat(candidate, message, candidate.equals(property) ? matcher : not(matcher));
                }
                assertThat("Removal notice", message, containsString("will be removed in a future major release"));
            }

            createFilter(Map.of(PROPERTIES.get(0), "true", PROPERTIES.get(1), "true", PROPERTIES.get(2), "true"));
            assertThat("Already reported properties remain suppressed", deprecationWarnings(handler), hasSize(3));
        }
    }

    private List<String> deprecationWarnings(ConcurrentLogHandler handler) {
        return handler.records.stream()
                .filter(record -> record.getMessage().startsWith("Warnings found:"))
                .peek(record -> assertThat("Deprecation log level", record.getLevel(), is(Level.WARNING)))
                .map(record -> record.getMessage())
                .toList();
    }

    private void createFiltersConcurrently(Map<String, String> settings) throws Exception {
        int count = 8;
        CyclicBarrier start = new CyclicBarrier(count);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    createFilter(settings);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
    }

    private void createFilter(Map<String, String> settings) {
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(settings))
                .build();
        new HelidonTelemetryContainerFilter(tracer, config, helpers);
    }

    private static final class ConcurrentLogHandler extends Handler implements AutoCloseable {
        private final Queue<LogRecord> records = new ConcurrentLinkedQueue<>();

        private ConcurrentLogHandler() {
            LOGGER.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            LOGGER.removeHandler(this);
        }
    }
}
