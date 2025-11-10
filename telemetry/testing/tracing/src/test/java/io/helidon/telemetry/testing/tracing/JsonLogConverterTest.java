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
package io.helidon.telemetry.testing.tracing;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class JsonLogConverterTest {

    @Test
    void testJsonLogConverterWithConcurrentUpdates() throws Exception {

        Logger logger = Logger.getLogger(JsonLogConverterTest.class.getName());
        JsonLogConverterImpl.TestLogHandler testLogHandler = JsonLogConverterImpl.TestLogHandler.create();
        logger.addHandler(testLogHandler);

        // Trigger a concurrent update exception if the code is not handling multithreading properly
        // by starting a stream and then adding a new message.
        logger.log(Level.INFO, "First msg");
        testLogHandler.resourceSpans()
                .forEach(span -> logger.log(Level.INFO, "Second msg"));
        assertThat("Fetch of spans during update", testLogHandler.resourceSpans(), hasSize(2));

    }
}
