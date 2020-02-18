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

package io.helidon.config;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.Thread.sleep;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests {@link io.helidon.config.SimpleRetryPolicy}.
 */
public class SimpleRetryPolicyTest {

    private static final Duration RETRY_TIMEOUT = ofMillis(250);
    private static final Duration OVERALL_TIMEOUT = ofMillis(250 * 10);
    private ScheduledExecutorService executor;

    @BeforeEach
    void init() {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void destroy() {
        executor.shutdown();
    }

    @Test
    public void testCallOnce() {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(0)
                .delay(ofMillis(10))
                .delayFactor(1)
                .callTimeout(RETRY_TIMEOUT)
                .overallTimeout(OVERALL_TIMEOUT)
                .executorService(executor)
                .build();

        SuccessSupplier sup = spy(new SuccessSupplier());
        retryPolicy.execute(sup::get);

        verify(sup, times(1)).get();
    }

    @Test
    public void testRetryTwiceButFirstCallSucceeded() throws Exception {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(2)
                .delay(ofMillis(10))
                .delayFactor(1)
                .callTimeout(RETRY_TIMEOUT)
                .overallTimeout(OVERALL_TIMEOUT)
                .executorService(executor)
                .build();

        SuccessSupplier sup = spy(new SuccessSupplier());
        retryPolicy.execute(sup::get);

        verify(sup, times(1)).get();
    }

    @Test
    public void testRetryTwiceCheckException() {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(2)
                .delay(ofMillis(10))
                .delayFactor(1)
                .callTimeout(RETRY_TIMEOUT)
                .overallTimeout(OVERALL_TIMEOUT)
                .executorService(executor)
                .build();

        UniversalSupplier sup = new UniversalSupplier(2, false, 0);

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            retryPolicy.execute(sup::get);
        });
        assertThat(ex.getMessage(), startsWith("All repeated calls failed."));

    }

    @Test
    public void testRetryTwiceCheckRetries() throws Exception {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(2)
                .delay(ZERO)
                .delayFactor(1)
                .callTimeout(RETRY_TIMEOUT)
                .overallTimeout(OVERALL_TIMEOUT)
                .executorService(executor)
                .build();

        UniversalSupplier sup = spy(new UniversalSupplier(2, false, 0));

        try {
            retryPolicy.execute(sup::get);
        } catch (ConfigException e) {

        }

        verify(sup, times(3)).get();
    }

    @Test
    public void testRetryTwicePolledTwice() throws Exception {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(2)
                .delay(ofMillis(10))
                .delayFactor(1)
                .callTimeout(RETRY_TIMEOUT)
                .overallTimeout(OVERALL_TIMEOUT)
                .executorService(executor)
                .build();

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
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(2)
                .delay(ZERO)
                .delayFactor(1)
                .callTimeout(RETRY_TIMEOUT)
                .overallTimeout(OVERALL_TIMEOUT)
                .executorService(executor)
                .build();

        UniversalSupplier sup = spy(new UniversalSupplier(2, true, 0));

        retryPolicy.execute(sup::get);

        verify(sup, times(3)).get();
    }

    @Test
    public void testRetryCannotScheduleNextCall() throws Exception {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(2)
                .delay(ofMillis(50))
                .delayFactor(1)
                .callTimeout(ofMillis(60))
                .overallTimeout(ofMillis(100))
                .executorService(executor)
                .build();

        UniversalSupplier sup = new UniversalSupplier(1, false, 50);

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            retryPolicy.execute(sup::get);
        });
        assertThat(ex.getCause(), instanceOf(TimeoutException.class));
        assertThat(ex.getMessage(), startsWith("A timeout has been reached."));
    }

    @Test
    public void testRetryOverallTimeoutReached() throws Exception {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(0)
                .delay(ofMillis(1))
                .delayFactor(1)
                .callTimeout(ofMillis(110))
                .overallTimeout(ofMillis(100))
                .executorService(executor)
                .build();

        UniversalSupplier sup = new UniversalSupplier(0, true, 300);

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            retryPolicy.execute(sup::get);
        });
        assertThat(ex.getCause(), instanceOf(TimeoutException.class));
        assertThat(ex.getMessage(), startsWith("A timeout has been reached."));
    }

    @Test
    public void testRetryCallTimeoutReached() {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(0)
                .delay(ZERO)
                .delayFactor(1)
                .callTimeout(ofMillis(10))
                .overallTimeout(ofMillis(100))
                .executorService(executor)
                .build();

        UniversalSupplier sup = new UniversalSupplier(0, true, 100);

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            String result = retryPolicy.execute(sup::get);
            System.out.println(result);
        });
        assertThat(ex.getCause(), instanceOf(TimeoutException.class));
        assertThat(ex.getMessage(), startsWith("A timeout has been reached."));
    }

    @Test
    public void testRetryCancel() throws Exception {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(0)
                .delay(ZERO)
                .delayFactor(1)
                .callTimeout(ofMillis(500))
                .overallTimeout(ofMillis(550))
                .executorService(executor)
                .build();

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
        assertThat(ex.getCause(), instanceOf(CancellationException.class));
        assertThat(ex.getMessage(), startsWith("An invocation has been canceled."));
    }

    @Test
    public void testDelayFactor1Delay0() {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(0)
                .delay(ZERO)
                .delayFactor(1)
                .callTimeout(ofMillis(500))
                .overallTimeout(ofMillis(550))
                .executorService(executor)
                .build();

        assertThat(retryPolicy.nextDelay(0, ZERO), is(ofMillis(0)));
        assertThat(retryPolicy.nextDelay(1, ZERO), is(ofMillis(0)));
    }

    @Test
    public void testDelayFactor1Delay1s() {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(0)
                .delay(ofSeconds(1))
                .delayFactor(1)
                .callTimeout(ofMillis(500))
                .overallTimeout(ofMillis(550))
                .executorService(executor)
                .build();

        assertThat(retryPolicy.nextDelay(0, ZERO), is(ofSeconds(1)));
        assertThat(retryPolicy.nextDelay(1, ofSeconds(1)), is(ofSeconds(1)));
    }

    @Test
    public void testDelayFactor15Delay1s() {
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy.builder()
                .retries(0)
                .delay(ofSeconds(1))
                .delayFactor(1.5)
                .callTimeout(ofMillis(500))
                .overallTimeout(ofMillis(550))
                .executorService(executor)
                .build();

        assertThat(retryPolicy.nextDelay(0, ZERO), is(ofMillis(1000)));
        assertThat(retryPolicy.nextDelay(1, ofSeconds(1)), is(ofMillis(1500)));
    }

    private static class SuccessSupplier extends UniversalSupplier {
        SuccessSupplier() {
            super(0, true, 0);
        }
    }

    private static class UniversalSupplier {

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
