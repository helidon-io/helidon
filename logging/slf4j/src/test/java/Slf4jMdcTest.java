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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.context.ExecutorException;
import io.helidon.logging.common.HelidonMdc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test proper function of Slf4j MDC propagator and provider.
 */
public class Slf4jMdcTest {

    private static final String TEST_KEY = "test";
    private static final String TEST_VALUE = "value";

    @AfterEach
    public void clearMdc() {
        HelidonMdc.clear();
    }

    @Test
    public void testMdc() {
        HelidonMdc.set(TEST_KEY, TEST_VALUE);
        assertThat(MDC.get(TEST_KEY), is(TEST_VALUE));
        HelidonMdc.remove(TEST_KEY);
        assertThat(MDC.get(TEST_KEY), nullValue());
        HelidonMdc.set(TEST_KEY, TEST_VALUE);
        HelidonMdc.clear();
        assertThat(MDC.get(TEST_KEY), nullValue());
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

    @Test
    public void testThreadPropagationWithEmptyMdc() {
        Context context = Context.create();
        ExecutorService executor = Contexts.wrap(Executors.newFixedThreadPool(1));

        Contexts.runInContext(context, () -> {
            try {
                Boolean value = executor.submit(new TestEmptyMdc()).get();
                assertThat(value, is(true));
            } catch (Exception e) {
                throw new ExecutorException("failed to execute", e);
            }
        });
    }

    private static final class TestCallable implements Callable<String> {

        @Override
        public String call() {
            return MDC.get(TEST_KEY);
        }
    }

    private static final class TestEmptyMdc implements Callable<Boolean> {

        @Override
        public Boolean call() {
            return MDC.getCopyOfContextMap().isEmpty();
        }
    }

}
