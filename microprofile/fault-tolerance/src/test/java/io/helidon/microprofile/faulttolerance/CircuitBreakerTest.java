/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Class CircuitBreakerTest.
 */
public class CircuitBreakerTest extends FaultToleranceTest {

    @Test
    public void testTripCircuit() {
        tripCircuit();
    }

    @Test
    public void testOpenAndCloseCircuit() throws Exception {
        openAndCloseCircuit();
    }

    @Test
    public void testOpenAndCloseCircuitNoWait() throws Exception {
        openAndCloseCircuitNoWait();
    }

    @Test
    public void testNotTripCircuit() {
        CircuitBreakerBean bean = newBean(CircuitBreakerBean.class);

        // Iterate a few times to test circuit
        for (int i = 0; i < bean.REQUEST_VOLUME_THRESHOLD; i++) {
            bean.exerciseBreaker(true);
        }

        // Now check circuit is still closed
        bean.exerciseBreaker(true);
    }

    @Test
    public void testOpenOnTimeouts() {
        CircuitBreakerBean bean = newBean(CircuitBreakerBean.class);

        // Iterate a few times to test circuit
        for (int i = 0; i < bean.REQUEST_VOLUME_THRESHOLD; i++) {
            assertThrows(TimeoutException.class, () -> bean.openOnTimeouts());
        }

        // Now check circuit is opened
        assertThrows(CircuitBreakerOpenException.class, () -> bean.openOnTimeouts());
    }

    @Test
    public void testOpenAndCloseAndOpen() throws Exception {
        // Open circuit breaker
        CircuitBreakerBean bean = tripCircuit();

        // Wait for more than delay
        Thread.sleep(bean.DELAY + 100);

        // Now a successful invocation => HALF_OPEN_MP
        bean.exerciseBreaker(true);

        // Now a failed invocation => OPEN_MP
        assertThrows(RuntimeException.class, () ->bean.exerciseBreaker(false));

        // Now it should be a circuit breaker exception
        assertThrows(CircuitBreakerOpenException.class, () -> bean.exerciseBreaker(true));
    }

    @Test
    public void testNotOpenWrongException() {
        CircuitBreakerBean bean = newBean(CircuitBreakerBean.class);

        // Should not trip circuit since it is a superclass exception
        for (int i = 0; i < bean.REQUEST_VOLUME_THRESHOLD; i++) {
            assertThrows(RuntimeException.class,
                         () -> bean.exerciseBreaker(false,
                                                    new RuntimeException("Oops")));
        }

        // Should not throw CircuitBreakerOpenException
        try {
            bean.exerciseBreaker(false, new RuntimeException("Oops"));
            fail("Should have failed on previous statement");
        } catch (RuntimeException ignored) {
            // this is OK
        }
    }

    @Test
    public void testWithBulkhead() throws Exception {
        CountDownLatch started;
        CircuitBreakerBean bean = newBean(CircuitBreakerBean.class);

        started = new CountDownLatch(1);
        bean.withBulkhead(started);             // enters bulkhead
        assertTrue(started.await(1000, TimeUnit.MILLISECONDS));

        started = new CountDownLatch(1);
        bean.withBulkhead(started);             // gets queued
        assertFalse(started.await(1000, TimeUnit.MILLISECONDS));

        assertThrows(ExecutionException.class, () -> {
            CompletableFuture<?> future = bean.withBulkhead(new CountDownLatch(1));
            future.get();
        });
    }

    @Test
    public void testWithBulkheadStage() throws Exception {
        CountDownLatch started;
        CircuitBreakerBean bean = newBean(CircuitBreakerBean.class);

        started = new CountDownLatch(1);
        bean.withBulkheadStage(started);             // enters bulkhead
        assertTrue(started.await(1000, TimeUnit.MILLISECONDS));

        started = new CountDownLatch(1);
        bean.withBulkheadStage(started);             // gets queued
        assertFalse(started.await(1000, TimeUnit.MILLISECONDS));

        CompletionStage<?> stage = bean.withBulkheadStage(new CountDownLatch(1));
        final CountDownLatch called = new CountDownLatch(1);
        stage.whenComplete((result, throwable) -> {
            called.countDown();
            assertThat(throwable, instanceOf(BulkheadException.class));
        });
        assertTrue(called.await(1000, TimeUnit.MILLISECONDS));
    }

    // -- Private methods -----------------------------------------------------

    private CircuitBreakerBean tripCircuit() {
        CircuitBreakerBean bean = newBean(CircuitBreakerBean.class);

        // Iterate a few times to test circuit
        for (int i = 0; i < bean.REQUEST_VOLUME_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () -> bean.exerciseBreaker(false));
        }

        // Now check circuit is opened
        assertThrows(CircuitBreakerOpenException.class, () -> bean.exerciseBreaker(false));
        return bean;
    }

    private CircuitBreakerBean openAndCloseCircuit() throws Exception {
        // Open circuit breaker
        CircuitBreakerBean bean = tripCircuit();

        // Now sleep for longer than breaker delay and test again
        Thread.sleep(bean.DELAY + 100);
        bean.exerciseBreaker(true);
        return bean;
    }

    private CircuitBreakerBean openAndCloseCircuitNoWait() throws Exception {
        // Open circuit breaker
        CircuitBreakerBean bean = tripCircuit();

        // Should get exception
        assertThrows(CircuitBreakerOpenException.class, () -> bean.exerciseBreaker(true));
        return bean;
    }
}
