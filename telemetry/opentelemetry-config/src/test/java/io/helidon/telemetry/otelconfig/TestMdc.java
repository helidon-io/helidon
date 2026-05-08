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

package io.helidon.telemetry.otelconfig;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import io.helidon.logging.common.LogConfig;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

class TestMdc {

    private static final Logger LOGGER = Logger.getLogger(TestMdc.class.getName());
    private static final ByteArrayOutputStream TEST_OUTPUT_STREAM = new ByteArrayOutputStream();
    private static final PrintStream TEST_PRINT_STREAM = new PrintStream(TEST_OUTPUT_STREAM);


    @BeforeAll
    static void init() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterAll
    static void close() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void testOtelMdc() {

        var originalPrint = System.out;

        try {
            System.setOut(TEST_PRINT_STREAM);
            LogConfig.initClass();
            TEST_OUTPUT_STREAM.reset();

            assertThat("Log with no OTel support", logMessage("No OTel"),
                       allOf(containsString("trace= "),
                             containsString("span= "),
                             containsString("baggage= ")));


            var helidonOtel = HelidonOpenTelemetry.create(OpenTelemetryConfig.builder()
                                                                  .service("log-test-service")
                                                                  .buildPrototype());

            assertThat("Log with Otel support but no current context", logMessage("No context"),
                       allOf(containsString("trace=none"),
                             containsString("span=none"),
                             containsString("baggage=none")));


            var tracer = GlobalOpenTelemetry.getTracer("test");
            var tracerSpan = tracer.spanBuilder("test").startSpan();

            var traceId = tracerSpan.getSpanContext().getTraceId();
            var spanId = tracerSpan.getSpanContext().getSpanId();

            List<String> output = new ArrayList<>();

            try (var ignoredBaggageContext = Baggage.builder()
                    .put("b1", "v1")
                    .build()
                    .storeInContext(Context.current())
                    .makeCurrent()) {

                try (Scope ignoredSpanScope = tracerSpan.makeCurrent()) {
                    output.add(logMessage("Tracer span created"));;
                }

            }
            assertThat("Captured log records", output,
                       hasItem(allOf(containsString("baggage"),
                                                 containsString("b1"),
                                                 containsString("v1"),
                                                 containsString(traceId),
                                                 containsString(spanId),
                                                 not(containsString("none")))));

        } finally {
            System.setOut(originalPrint);
        }

    }

    private String logMessage(String message) {
        try {
            LOGGER.info(message);
            return TEST_OUTPUT_STREAM.toString();
        } finally {
            for (var handler : LOGGER.getHandlers()) {
                handler.flush();
            }
            TEST_OUTPUT_STREAM.reset();
        }
    }
}
