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

package io.helidon.webserver.observe.telemetry.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.AfterStop;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@ServerTest
class TestSingleOtelAssignment {

    private static Logger logger;
    private static InMemoryHandler inMemoryHandler;

    @SetUpServer
    static void setupServer(WebServerConfig.Builder serverBuilder) {
        GlobalOpenTelemetry.resetForTest();

        logger = Logger.getLogger(OpenTelemetry.class.getName());
        inMemoryHandler = new InMemoryHandler();
        logger.addHandler(inMemoryHandler);

        var configText = """
                server:
                  features:
                    observe:
                      observers:
                        metrics:
                """;
        Config config = Config.just(configText, MediaTypes.APPLICATION_YAML);
        serverBuilder
                .config(config.get("server"));
    }

    @AfterStop
    void tearDown() {
        logger.removeHandler(inMemoryHandler);
    }

    @Test
    void testSingleAssignment() {

        var otel =Services.get(OpenTelemetry.class);
        var semConf = Services.get(OpenTelemetryMetricsHttpSemanticConventions.class);
        assertThat("Startup warnings",
                   inMemoryHandler.logRecords().stream()
                        .filter(lr -> lr.getLevel().equals(Level.WARNING))
                        .toList(),
                   hasSize(0));
    }

    static class InMemoryHandler extends Handler {

        private final List<LogRecord> logRecords = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            logRecords.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
            logRecords.clear();
        }

        List<LogRecord> logRecords() {
            return logRecords;
        }
    }
}
