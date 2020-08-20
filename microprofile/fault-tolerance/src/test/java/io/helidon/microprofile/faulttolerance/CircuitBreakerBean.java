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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

import javax.enterprise.context.Dependent;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Class CircuitBreakerBean.
 */
@Dependent
public class CircuitBreakerBean {

    static final int REQUEST_VOLUME_THRESHOLD = 4;
    static final int SUCCESS_THRESHOLD = 2;
    static final int DELAY = 1000;
    static final double FAILURE_RATIO = 0.75;

    private int counter = 0;

    public int getCounter() {
        return counter;
    }

    @CircuitBreaker(
        successThreshold = SUCCESS_THRESHOLD,
        requestVolumeThreshold = REQUEST_VOLUME_THRESHOLD,
        failureRatio = FAILURE_RATIO,
        delay = DELAY)
    public void exerciseBreaker(boolean success) {
        counter++;
        if (success) {
            FaultToleranceTest.printStatus("CircuitBreakerBean::exerciseBreaker", "success");
        } else {
            FaultToleranceTest.printStatus("CircuitBreakerBean::exerciseBreaker", "failure");
            throw new RuntimeException("Oops");
        }
    }

    @CircuitBreaker(
        failOn = UnsupportedOperationException.class,
        successThreshold = SUCCESS_THRESHOLD,
        requestVolumeThreshold = REQUEST_VOLUME_THRESHOLD,
        failureRatio = FAILURE_RATIO,
        delay = DELAY)
    public void exerciseBreaker(boolean success, RuntimeException e) {
        counter++;
        if (success) {
            FaultToleranceTest.printStatus("CircuitBreakerBean::exerciseBreaker", "success");
        } else {
            FaultToleranceTest.printStatus("CircuitBreakerBean::exerciseBreaker", "failure");
            throw e;
        }
    }

    @Timeout(500)
    @CircuitBreaker(
        successThreshold = SUCCESS_THRESHOLD,
        requestVolumeThreshold = REQUEST_VOLUME_THRESHOLD,
        failureRatio = FAILURE_RATIO,
        delay = DELAY)
    public void openOnTimeouts() throws InterruptedException {
        counter++;
        FaultToleranceTest.printStatus("CircuitBreakerBean::openOnTimeouts", "failure");
        Thread.sleep(1000);     // forces timeout
    }

    @Asynchronous
    @Bulkhead(value = 1, waitingTaskQueue = 1)
    @CircuitBreaker(
            requestVolumeThreshold = 3,
            failureRatio = 1.0,
            delay = 50000,
            failOn = UnitTestException.class)
    public CompletableFuture<?> withBulkhead(CountDownLatch started) throws InterruptedException {
        started.countDown();
        FaultToleranceTest.printStatus("CircuitBreakerBean::withBulkhead", "success");
        Thread.sleep(3 * DELAY);
        return CompletableFuture.completedFuture(null);
    }

    @Asynchronous
    @Bulkhead(value = 1, waitingTaskQueue = 1)
    @CircuitBreaker(
            requestVolumeThreshold = 3,
            failureRatio = 1.0,
            delay = 50000,
            failOn = UnitTestException.class)
    public CompletionStage<?> withBulkheadStage(CountDownLatch started) throws InterruptedException {
        started.countDown();
        FaultToleranceTest.printStatus("CircuitBreakerBean::withBulkheadStage", "success");
        Thread.sleep(3 * DELAY);
        return CompletableFuture.completedFuture(null);
    }
}
