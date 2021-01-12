/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.CDI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.helidon.microprofile.faulttolerance.FaultToleranceExtension.getRealClass;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.eclipse.microprofile.metrics.Tag;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;

/**
 * Class FaultToleranceTest.
 */
@HelidonTest
abstract class FaultToleranceTest {

    private static final long TIMEOUT = 5000;
    private static final TimeUnit TIMEOUT_UNITS = TimeUnit.MILLISECONDS;
    private static final int NUMBER_OF_THREADS = 20;

    private static final Executor executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    void reset() {
    }

    /**
     * Clears all internal handlers before running each test. Latest FT spec has
     * clarified that each method of each class that uses a bulkhead/breaker has
     * its own state (in application scope). Most of our unit tests assume
     * independence so we clear this state before running each test.
     */
    @BeforeEach
    void resetHandlers() {
        reset();
        MethodInvoker.clearMethodStatesMap();
    }

    protected static <T> T newBean(Class<T> beanClass) {
        return CDI.current().select(beanClass).get();
    }

    protected static <T> T newNamedBean(Class<T> beanClass) {
        return CDI.current().select(beanClass, NamedLiteral.of(beanClass.getSimpleName())).get();
    }

    static void printStatus(String message, String status) {
        System.out.println(message + " -> " + status + " [Thread: "
                                   + Thread.currentThread().getName() + "]");
    }

    @SuppressWarnings("unchecked")
    static <T> CompletableFuture<T>[] getConcurrentCalls(Supplier<T> supplier, int size) {
        return Stream.generate(
                () -> CompletableFuture.supplyAsync(supplier, executor)
        ).limit(size).toArray(CompletableFuture[]::new);
    }

    @SuppressWarnings("unchecked")
    static <T> CompletableFuture<T>[] getAsyncConcurrentCalls(Supplier<CompletableFuture<T>> supplier, int size) {
        return Stream.generate(supplier::get).limit(size).toArray(CompletableFuture[]::new);
    }

    static void waitFor(CompletableFuture<String>[] calls) {
        for (CompletableFuture<String> c : calls) {
            try {
                c.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static <T> void assertCompleteExceptionally(Future<T> future,
                                                Class<? extends Throwable> exceptionClass) {
        assertCompleteExceptionally(future, exceptionClass, null);
    }

    static <T> void assertCompleteExceptionally(Future<T> future,
                                                Class<? extends Throwable> exceptionClass,
                                                String exceptionMessage) {
        try {
            future.get(TIMEOUT, TIMEOUT_UNITS);
            fail("Expected exception: " + exceptionClass.getName());
        } catch (InterruptedException | TimeoutException e) {
            fail("Unexpected exception " + e, e);
        } catch (ExecutionException ee) {
            assertThat("Cause of ExecutionException", ee.getCause(), instanceOf(exceptionClass));
            if (exceptionMessage != null) {
                assertThat(ee.getCause().getMessage(), is(exceptionMessage));
            }
        }
    }

    static void assertCompleteOk(CompletionStage<String> future, String expectedMessage) {
        try {
            CompletableFuture<?> cf = future.toCompletableFuture();
            assertThat(cf.get(TIMEOUT, TIMEOUT_UNITS), is(expectedMessage));
        } catch (Exception e) {
            fail("Unexpected exception" + e);
        }
    }

    static Tag getMethodTag(Object bean, String methodName) {
        return new Tag("method", getRealClass(bean).getName() + "." + methodName);
    }
}
