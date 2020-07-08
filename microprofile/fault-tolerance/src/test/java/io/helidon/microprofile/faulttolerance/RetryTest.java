/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Class RetryTest.
 */
public class RetryTest extends FaultToleranceTest {

    // parameterize these for ease of debugging
    private static final long TIMEOUT = 1000;
    private static final TimeUnit TIMEOUT_UNITS = TimeUnit.MILLISECONDS;

    static Stream<Arguments> createBeans() {
        return Stream.of(
                Arguments.of(newBean(RetryBean.class), "ManagedRetryBean"));
                // Arguments.of(newNamedBean(SyntheticRetryBean.class), "SyntheticRetryBean"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryBean(RetryBean bean, String unused) {
        assertThat(bean.getInvocations(), is(0));
        bean.retry();
        assertThat(bean.getInvocations(), is(3));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryBeanFallback(RetryBean bean, String unused) {
        assertThat(bean.getInvocations(), is(0));
        String value = bean.retryWithFallback();
        assertThat(bean.getInvocations(), is(2));
        assertThat(value, is("fallback"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryAsync(RetryBean bean, String unused) throws Exception {
        Future<String> future = bean.retryAsync();
        future.get();
        assertThat(bean.getInvocations(), is(3));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryWithDelayAndJitter(RetryBean bean, String unused) throws Exception {
        long millis = System.currentTimeMillis();
        String value = bean.retryWithDelayAndJitter();
        assertThat(System.currentTimeMillis() - millis, greaterThan(200L));
    }

    /**
     * Inspired by a TCK test which makes sure failed executions propagate correctly.
     *
     * @param bean the bean to invoke
     * @param unused bean name to use for the specific test invocation
     * @throws Exception
     */
    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryWithException(RetryBean bean, String unused) throws Exception {
        final CompletionStage<String> future = bean.retryWithException();

        assertCompleteExceptionally(future, IOException.class, "Simulated error");
        assertThat(bean.getInvocations(), is(3));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryCompletionStageWithEventualSuccess(RetryBean bean, String unused) {
        assertCompleteOk(bean.retryWithUltimateSuccess(), "Success");
        assertThat(bean.getInvocations(), is(3));
    }

    private void assertCompleteOk(final CompletionStage<String> future, final String expectedMessage) {
        try {
            CompletableFuture<?> cf = toCompletableFuture(future);
            assertThat(cf.get(TIMEOUT, TIMEOUT_UNITS), is(expectedMessage));
        }
        catch (Exception e) {
            fail("Unexpected exception" + e);
        }
    }

    private void assertCompleteExceptionally(final CompletionStage<String> future,
            final Class<? extends Throwable> exceptionClass,
            final String exceptionMessage) {
        try {
            Object result = toCompletableFuture(future).get(TIMEOUT, TIMEOUT_UNITS);
            fail("Expected exception: " + exceptionClass.getName() + " with message: " + exceptionMessage);
        }
        catch (InterruptedException | TimeoutException e) {
            fail("Unexpected exception " + e, e);
        }
        catch (ExecutionException ee) {
            assertThat("Cause of ExecutionException", ee.getCause(), instanceOf(exceptionClass));
            assertThat(ee.getCause().getMessage(), is(exceptionMessage));
        }
    }

    /**
     * Returns a future that is completed when the stage is completed and has the same value or exception
     * as the completed stage. It's supposed to be equivalent to calling
     * {@link CompletionStage#toCompletableFuture()} but works with any CompletionStage
     * and doesn't throw {@link java.lang.UnsupportedOperationException}.
     *
     * @param <U> The type of the future result
     * @param stage Stage to convert to a future
     * @return Future converted from stage
     */
    public static <U> CompletableFuture<U> toCompletableFuture(CompletionStage<U> stage) {
        CompletableFuture<U> future = new CompletableFuture<>();
        stage.whenComplete((v, e) -> {
            if (e != null) {
                future.completeExceptionally(e);
            }
            else {
                future.complete(v);
            }
        });
        return future;
    }


}
