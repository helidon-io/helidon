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

package io.helidon.common;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for classes and methods defined in {@link io.helidon.common.Functions}.
 * <p>
 * Tests for classes make sure we can use a lambda.
 */
public class FunctionsTest {
    @Test
    void testCheckedSupplier() {
        try {
            String result = supply(TestMethods::supplier);
            assertThat(result, is("works"));
        } catch (IOException e) {
            // just making sure we can correctly catch the checked exception thrown by the runnable
        }
    }

    @Test
    void testUncheckedSupplier() {
        String result = Functions.unchecked(TestMethods::supplier).get();
        assertThat(result, is("works"));
    }

    @Test
    void testCheckedRunnable() {
        try {
            run(TestMethods::runnable);
        } catch (IOException e) {
            // just making sure we can correctly catch the checked exception thrown by the runnable
        }
    }

    @Test
    void testUncheckedRunnable() {
        Functions.unchecked(TestMethods::runnable)
                .run();
    }

    @Test
    void testCheckedConsumer() {
        try {
            consume(TestMethods::consumer, "hello");
        } catch (IOException e) {
            // just making sure we can correctly catch the checked exception thrown by the runnable
        }
    }

    @Test
    void testUncheckedConsumer() {
        Functions.unchecked(TestMethods::consumer)
                .accept("hello");
    }

    @Test
    void testCheckedBiConsumer() {
        try {
            biConsume(TestMethods::biConsumer, "hello", 42);
        } catch (IOException e) {
            // just making sure we can correctly catch the checked exception thrown by the runnable
        }
    }

    @Test
    void testUncheckedBiConsumer() {
        Functions.unchecked(TestMethods::biConsumer)
                .accept("hello", 42);
    }

    @Test
    void testCheckedFunction() {
        try {
            String result = function(TestMethods::function, "John");
            assertThat(result, is("Hello John!"));
        } catch (IOException e) {
            // just making sure we can correctly catch the checked exception thrown by the runnable
        }
    }

    @Test
    void testUncheckedFunction() {
        String result = Functions.unchecked(TestMethods::function)
                .apply("jack");
        assertThat(result, is("Hello jack!"));
    }


    private static <E extends Exception> void run(Functions.CheckedRunnable<E> runnable) throws E {
        runnable.run();
    }

    private static <T, E extends Exception> T supply(Functions.CheckedSupplier<T, E> supplier) throws E {
        return supplier.get();
    }

    private static <T, E extends Exception> void consume(Functions.CheckedConsumer<T, E> consumer, T value) throws E {
        consumer.accept(value);
    }

    private static <T, U, E extends Exception> U function(Functions.CheckedFunction<T, U, E> function, T param) throws E {
        return function.apply(param);
    }


    static <U, V, E extends Exception> void biConsume(Functions.CheckedBiConsumer<U, V, E> consumer, U value, V value2) throws E {
        consumer.accept(value, value2);
    }

    private static class TestMethods {
        static void runnable() throws IOException {
        }

        static String supplier() throws IOException {
            return "works";
        }

        static void consumer(String param) throws IOException {
        }

        static void biConsumer(String param, int param2) throws IOException {
        }

        static String function(String param) throws IOException {
            return "Hello " + param + "!";
        }
    }
}
