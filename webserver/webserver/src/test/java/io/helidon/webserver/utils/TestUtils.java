/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.helidon.common.http.DataChunk;

import org.hamcrest.Description;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.text.StringContainsInOrder.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The TestUtils.
 */
public class TestUtils {

    /**
     * Test a result of a lambda call of a given object that is under a test.
     *
     * @param call       the lambda method to call while testing the given object
     * @param subMatcher the matcher to apply on the lambda method call result
     * @param <T>        the type of the object under a test
     * @param <R>        the returning type of the lambda method call which is the value that is tested by the {@code subMatcher}
     * @return matcher that can be combined with another matchers
     */
    public static <T, R> FeatureMatcher<T, R> matcher(Function<T, R> call, Matcher<? super R> subMatcher) {
        return new FeatureMatcher<T,R>(subMatcher, "lambda call result", "...") {
            @Override
            protected R featureValueOf(T actual) {
                return call.apply(actual);
            }

            @Override
            protected boolean matchesSafely(T actual, Description mismatch) {
                R featureValue = featureValueOf(actual);
                if (!subMatcher.matches(featureValue)) {
                    mismatch.appendText("lambda call of ")
                            .appendText(actual.toString())
                            .appendText(" (of type ")
                            .appendText(actual.getClass().getCanonicalName())
                            .appendText(") ");
                    subMatcher.describeMismatch(featureValue, mismatch);
                    return false;
                }
                return true;
            }
        };
    }

    /**
     * Test a result of a lambda call of a given object that is under a test.
     *
     * @param call       the lambda method to call while testing the given object
     * @param subMatcher the matcher to apply on the lambda method call result
     * @param <T>        the type of the object under a test
     * @param <R>        the returning type of the lambda method call which is the value that is tested by the {@code subMatcher}
     * @return matcher that can be combined with another matchers
     */
    public static <T, R> FeatureMatcher<T, R> matcher(Supplier<R> call, Matcher<? super R> subMatcher) {
        return matcher(t -> call.get(), subMatcher);
    }

    /**
     * Converts given array of altering keys and values into a map.
     *
     * @param entries an altering array of keys and values ({@code K1, V1, K2, V2, ...}
     * @return a map
     * @throws IllegalStateException in case of odd number of entries
     */
    @SafeVarargs
    public static <T> Map<String, T> toMap(T... entries) {
        if (entries.length % 2 == 1) {
            throw new IllegalArgumentException("Invalid entries");
        }
        return IntStream.range(0, entries.length / 2).map(i -> i * 2)
                .collect(HashMap::new,
                         (m, i) -> m.put(String.valueOf(entries[i]),
                                         entries[i + 1]),
                         Map::putAll);
    }

    @SafeVarargs
    public static <T> List<T> toList(T... entries) {
        return IntStream.range(0, entries.length)
                .collect(ArrayList::new,
                         (m, i) -> m.add(entries[i]),
                         List::addAll);
    }

    /**
     * Asserts that provided {@code runnable} execution throws {@link Exception} of the expected type.
     *
     * @param runnable a runnable to execute.
     * @param expectedException a type of the expected {@link Exception}
     * @param causes a list of expected exception causes ({@code .getCause()}).
     */
    @SuppressWarnings("unchecked")
    public static void assertException(RunnableWithException runnable,
                                       Class<? extends Exception> expectedException,
                                       Class... causes) {
        try {
            runnable.run();
            throw new AssertionError("Missing expected exception! [expected: "
                                             + expectedException.getName() + ", throws: nothing]");
        } catch (Exception e) {
            if (!expectedException.isAssignableFrom(e.getClass())) {
                throw new AssertionError("Missing expected exception! [expected: "
                                                 + expectedException.getName() + ", throws: " + e.getClass()+ "]");
            }
            if (causes != null && causes.length > 0) {
                Throwable lastCause = e.getCause();
                for (Class cause : causes) {
                    if (!cause.isAssignableFrom(lastCause.getClass())) {
                        throw new AssertionError("Exception doesn't have expected cause! [expected: "
                                                         + cause.getName() + ", actual: " + lastCause.getClass()+ "]");
                    }
                }
            }
        }
    }

    @Test
    public void testMyMatcherFailure() throws Exception {
        Map<String, String> map = Collections.singletonMap("my-key", "my-value");

        try {
            assertThat(map, matcher(foo -> foo.get("not-a-key"), Is.is("my-value")));
            fail("Should have thrown an exception");
        } catch (AssertionError e) {

            Logger.getLogger(getClass().getName()).log(Level.FINE, "THIS IS NOT A FAILURE. The assertion message would be:", e);

            assertThat(e, hasProperty("message", stringContainsInOrder(Stream.of(
                    "Expected:",
                    "lambda call result",
                    "is",
                    "\"my-value\"",
                    "but:",
                    "was null"
            ).collect(Collectors.toList()))));
        }
    }

    @Test
    public void testMyMatcher() throws Exception {
        Map<String, String> map = Collections.singletonMap("my-key", "my-value");

        assertThat(map, matcher(testedMap -> testedMap.get("my-key"), Is.is("my-value")));
    }

    @FunctionalInterface
    public interface RunnableWithException {
        void run() throws Exception;
    }

    @Test
    public void testMySupplierMatcher() throws Exception {
        Map<String, String> map = Collections.singletonMap("my-key", "my-value");

        assertThat(map, matcher(() -> map.get("my-key"), Is.is("my-value")));
    }

    public static String requestChunkAsString(DataChunk chunk) {
        try {
            return new String(chunk.bytes());
        } finally {
            chunk.release();
        }
    }
}
