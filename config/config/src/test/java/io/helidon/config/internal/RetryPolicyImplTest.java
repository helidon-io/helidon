/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.helidon.config.ConfigException;

import static java.lang.Thread.sleep;
import java.time.Duration;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests {@link RetryPolicyImpl}.
 */
public class RetryPolicyImplTest {

    private static final Duration RETRY_TIMEOUT = ofMillis(250);
    private static final Duration OVERALL_TIMEOUT = ofMillis(250 * 10);
    
    @Test
    public void testCallOnce() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(0,
                                                          ofMillis(10),
                                                          1,
                                                          RETRY_TIMEOUT,
                                                          OVERALL_TIMEOUT,
                                                          Executors.newSingleThreadScheduledExecutor());

        SuccessSupplier sup = spy(new SuccessSupplier());
        retryPolicy.execute(sup::get);

        verify(sup, times(1)).get();
    }

    @Test
    public void testRetryTwiceButFirstCallSucceeded() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(2,
                                                          ofMillis(10),
                                                          1,
                                                          RETRY_TIMEOUT,
                                                          OVERALL_TIMEOUT,
                                                          Executors.newSingleThreadScheduledExecutor());

        SuccessSupplier sup = spy(new SuccessSupplier());
        retryPolicy.execute(sup::get);

        verify(sup, times(1)).get();
    }

    @Test
    public void testRetryTwiceCheckException() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(2,
                                                          ofMillis(10),
                                                          1,
                                                          RETRY_TIMEOUT,
                                                          OVERALL_TIMEOUT,
                                                          Executors.newSingleThreadScheduledExecutor());

        UniversalSupplier sup = spy(new UniversalSupplier(2, false, 0));

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            retryPolicy.execute(sup::get);
        });
        assertTrue(ex.getMessage().startsWith("All repeated calls failed."));

    }

    @Test
    public void testRetryTwiceCheckRetries() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(2,
                                                          ZERO,
                                                          1,
                                                          RETRY_TIMEOUT,
                                                          OVERALL_TIMEOUT,
                                                          Executors.newSingleThreadScheduledExecutor());

        UniversalSupplier sup = spy(new UniversalSupplier(2, false, 0));

        try {
            retryPolicy.execute(sup::get);
        } catch (ConfigException e) {

        }

        verify(sup, times(3)).get();
    }

    @Test
    public void testRetryTwicePolledTwice() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(2,
                                                          ofMillis(10),
                                                          1,
                                                          RETRY_TIMEOUT,
                                                          OVERALL_TIMEOUT,
                                                          Executors.newSingleThreadScheduledExecutor());

        UniversalSupplier sup = spy(new UniversalSupplier(2, false, 0));

        try {
            retryPolicy.execute(sup::get);
        } catch (ConfigException e) {
        }

        sup.reset();

        try {
            retryPolicy.execute(sup::get);
        } catch (ConfigException e) {
        }

        verify(sup, times(6)).get();
    }

    @Test
    public void testRetryThirdAttemptSucceed() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(2,
                                                          ZERO,
                                                          1,
                                                          RETRY_TIMEOUT,
                                                          OVERALL_TIMEOUT,
                                                          Executors.newSingleThreadScheduledExecutor());

        UniversalSupplier sup = spy(new UniversalSupplier(2, true, 0));

        retryPolicy.execute(sup::get);

        verify(sup, times(3)).get();
    }

    @Test
    public void testRetryCannotScheduleNextCall() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(2,
                                                          ofMillis(50),
                                                          1,
                                                          ofMillis(60),
                                                          ofMillis(100),
                                                          Executors.newSingleThreadScheduledExecutor());

        UniversalSupplier sup = new UniversalSupplier(1, false, 50);

        ConfigException ex = assertThrows(ConfigException.class, () -> {
                retryPolicy.execute(sup::get);
        });
        assertTrue(instanceOf(TimeoutException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("A timeout has been reached."));
    }

    @Test
    public void testRetryOverallTimeoutReached() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(0,
                                                          ofMillis(1),
                                                          1,
                                                          ofMillis(110),
                                                          ofMillis(100),
                                                          Executors.newSingleThreadScheduledExecutor());

        UniversalSupplier sup = new UniversalSupplier(0, true, 300);

        ConfigException ex = assertThrows(ConfigException.class, () -> {
                retryPolicy.execute(sup::get);
        });
        assertTrue(instanceOf(TimeoutException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("A timeout has been reached."));
    }

    @Test
    public void testRetryCallTimeoutReached() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(0,
                                                          ZERO,
                                                          1,
                                                          ofMillis(10),
                                                          ofMillis(100),
                                                          Executors.newSingleThreadScheduledExecutor());

        UniversalSupplier sup = new UniversalSupplier(0, true, 100);

        ConfigException ex = assertThrows(ConfigException.class, () -> {
                String result = retryPolicy.execute(sup::get);
                System.out.println(result);
        });
        assertTrue(instanceOf(TimeoutException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("A timeout has been reached."));
    }

    @Test
    public void testRetryCancel() throws Exception {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(0,
                                                          ZERO,
                                                          1,
                                                          ofMillis(500),
                                                          ofMillis(550),
                                                          Executors.newSingleThreadScheduledExecutor());

        UniversalSupplier sup = new UniversalSupplier(0, true, 500);

        new Thread(() -> {
            try {
                sleep(250);
                retryPolicy.cancel(true);
            } catch (InterruptedException e) {

            }
        }).start();

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            Object execute = retryPolicy.execute(sup::get);
            System.out.println(execute);
        });
        assertTrue(instanceOf(CancellationException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("An invocation has been canceled."));
    }

    @Test
    public void testDelayFactor1Delay0() {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(0,
                                                          ZERO,
                                                          1,
                                                          ofMillis(500),
                                                          ofMillis(550),
                                                          Executors.newSingleThreadScheduledExecutor());

        assertThat(retryPolicy.nextDelay(0, ZERO), is(ofMillis(0)));
        assertThat(retryPolicy.nextDelay(1, ZERO), is(ofMillis(0)));
    }

    @Test
    public void testDelayFactor1Delay1s() {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(0,
                                                          ofSeconds(1),
                                                          1,
                                                          ofMillis(500),
                                                          ofMillis(550),
                                                          Executors.newSingleThreadScheduledExecutor());

        assertThat(retryPolicy.nextDelay(0, ZERO), is(ofSeconds(1)));
        assertThat(retryPolicy.nextDelay(1, ofSeconds(1)), is(ofSeconds(1)));
    }

    @Test
    public void testDelayFactor15Delay1s() {
        RetryPolicyImpl retryPolicy = new RetryPolicyImpl(0,
                                                          ofSeconds(1),
                                                          1.5,
                                                          ofMillis(500),
                                                          ofMillis(550),
                                                          Executors.newSingleThreadScheduledExecutor());

        assertThat(retryPolicy.nextDelay(0, ZERO), is(ofMillis(1000)));
        assertThat(retryPolicy.nextDelay(1, ofSeconds(1)), is(ofMillis(1500)));
    }

    private class SuccessSupplier extends UniversalSupplier {
        SuccessSupplier() {
            super(0, true, 0);
        }
    }

    private class UniversalSupplier {

        private final int retries;
        private final boolean lastSucceed;
        private final long callDuration;
        private Supplier<? extends RuntimeException> exceptionSupplier;

        private int counter = 0;

        private UniversalSupplier(int retries, boolean lastSucceed, long callDuration) {
            this.retries = retries;
            this.lastSucceed = lastSucceed;
            this.callDuration = callDuration;
            this.exceptionSupplier = RuntimeException::new;
        }

        private UniversalSupplier(int retries,
                                  boolean lastSucceed,
                                  long callDuration,
                                  Supplier<? extends RuntimeException> exceptionSupplier) {
            this.retries = retries;
            this.lastSucceed = lastSucceed;
            this.callDuration = callDuration;
            this.exceptionSupplier = exceptionSupplier;
        }

        public String get() throws RuntimeException {
            counter++;
            try {
                sleep(callDuration);
            } catch (InterruptedException e) {
            }
            if (counter > retries && lastSucceed) {
                return "something";
            } else {
                throw exceptionSupplier.get();
            }

        }

        void reset() {
            counter = 0;
        }

    }
}
