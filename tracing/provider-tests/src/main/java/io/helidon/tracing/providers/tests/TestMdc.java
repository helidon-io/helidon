/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.tests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.common.testing.junit5.InMemoryLoggingHandler;
import io.helidon.logging.jul.HelidonFormatter;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

class TestMdc {

    @BeforeAll
    static void beforeAll() throws IOException {
        String loggingConfig = "# HelidonConsoleHandler uses a SimpleFormatter subclass that replaces \"!thread!\" with the current thread\n"
                + "java.util.logging.SimpleFormatter.format=%1$tY.%1$tm.%1$td %1$tH:%1$tM:%1$tS %4$s %3$s !thread!: %5$s%6$s trace_id %X{trace_id}%n\n";

        LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(loggingConfig.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testTraceId() {

        Logger logger = Logger.getLogger(TestMdc.class.getName());
        HelidonFormatter helidonFormatter = new HelidonFormatter();
        try (InMemoryLoggingHandler loggingHandler = InMemoryLoggingHandler.create(logger)) {
            loggingHandler.setFormatter(helidonFormatter);
            Span span = Tracer.global().spanBuilder("logging-test-span").start();
            String expectedTraceId = span.context().traceId();
            String formattedMessage;
            try (Scope ignored = span.activate()) {
                logger.log(Level.INFO, "Test log message");
                formattedMessage = helidonFormatter.format(loggingHandler.logRecords().getFirst());
            }

            assertThat("MDC-processed log message",
                       formattedMessage,
                       containsString("trace_id " + expectedTraceId));
        }

    }
}
