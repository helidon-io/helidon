/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.logging.jul;/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import io.helidon.common.LogConfig;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.context.ExecutorException;
import io.helidon.logging.HelidonMdc;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test proper function of Jul MDC propagator and provider.
 */
public class JulMdcTest {

    private static final Logger LOGGER = Logger.getLogger(JulMdcTest.class.getName());
    private static final ByteArrayOutputStream OUTPUT_STREAM = new ByteArrayOutputStream();
    private static final PrintStream TEST_STREAM = new PrintStream(OUTPUT_STREAM);
    private static final PrintStream ORIGINAL = System.out;

    private static final String TEST_KEY = "test";
    private static final String TEST_VALUE = "value";

    @Test
    public void testMdc() {
        try {
            System.setOut(TEST_STREAM);
            LogConfig.initClass();
            OUTPUT_STREAM.reset();
            HelidonMdc.set(TEST_KEY, TEST_VALUE);
            String message = "This is test logging message";
            String thread = Thread.currentThread().toString();
            String logMessage = logMessage(message);
            assertThat(logMessage, endsWith(thread + ": " + message + " " + TEST_VALUE + System.lineSeparator()));

            HelidonMdc.remove(TEST_KEY);
            logMessage = logMessage(message);
            assertThat(logMessage, endsWith(thread + ": " + message + System.lineSeparator()));

            HelidonMdc.set(TEST_KEY, TEST_VALUE);
            HelidonMdc.clear();
            logMessage = logMessage(message);
            assertThat(logMessage, endsWith(thread + ": " + message + System.lineSeparator()));
        } finally {
            System.setOut(ORIGINAL);
        }
    }

    private String logMessage(String message) {
        try {
            LOGGER.info(message);
            return OUTPUT_STREAM.toString();
        } finally {
            OUTPUT_STREAM.reset();
        }
    }

    @Test
    public void testThreadPropagation() {
        HelidonMdc.set(TEST_KEY, TEST_VALUE);
        Context context = Context.create();
        ExecutorService executor = Contexts.wrap(Executors.newFixedThreadPool(1));

        Contexts.runInContext(context, () -> {
            try {
                String value = executor.submit(new TestCallable()).get();
                assertThat(value, is(TEST_VALUE));
            } catch (Exception e) {
                throw new ExecutorException("failed to execute", e);
            }
        });
    }

    private static final class TestCallable implements Callable<String> {

        @Override
        public String call() {
            return JulMdc.get(TEST_KEY);
        }
    }

}
