/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.io.UnsupportedEncodingException;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.helidon.microprofile.testing.junit5.AddConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@AddConfig(key = HelidonTelemetryContainerFilter.SPAN_NAME_INCLUDES_METHOD,
           value = "false")
class WithSpanUsingLegacySpanNameTest extends WithSpanTestBase {

    private static Logger logger = Logger.getLogger(HelidonTelemetryContainerFilter.class.getName());
    private static MemoryLogHandler memoryLogHandler;

    @BeforeAll
    static void setup() {
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof MemoryLogHandler logHandler) {
                memoryLogHandler = logHandler;
            }
        }
    }
//    static {
//        logger.addHandler(memoryLogHandler);
//    }

//    @AfterAll
//    static void cleanup() {
//        logger.removeHandler(memoryLogHandler);
//    }

    @ParameterizedTest()
    @MethodSource()
    void testDefaultAppSpanNameFromPath(SpanPathTestInfo spanPathTestInfo) throws UnsupportedEncodingException {

        testSpanNameFromPath(spanPathTestInfo);

        // @Deprecated(forRemoval = true) in 5.1 remove the following:
        assertThat("Log output",
                   memoryLogHandler.logAsString(),
                   containsString("does not set " + HelidonTelemetryContainerFilter.SPAN_NAME_INCLUDES_METHOD));
        // end of removal


    }

    static Stream<SpanPathTestInfo> testDefaultAppSpanNameFromPath() {
        return Stream.of(new SpanPathTestInfo("traced", "/traced"),
                         new SpanPathTestInfo("traced/sub/data", "/traced/sub/{name}"));
    }

}
