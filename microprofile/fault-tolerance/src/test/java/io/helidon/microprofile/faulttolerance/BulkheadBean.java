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

import javax.enterprise.context.Dependent;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.Fallback;

/**
 * Class BulkheadBean.
 */
@Dependent
public class BulkheadBean {

    static final int CONCURRENT_CALLS = 3;
    static final int WAITING_TASK_QUEUE = 3;
    static final int TOTAL_CALLS = CONCURRENT_CALLS + WAITING_TASK_QUEUE;

    static class ConcurrencyCounter {

        private int currentCalls;
        private int concurrentCalls;
        private int totalCalls;

        synchronized void increment() {
            currentCalls++;
            if (currentCalls > concurrentCalls) {
                concurrentCalls = currentCalls;
            }
            totalCalls++;
        }

        synchronized void decrement() {
            currentCalls--;
        }

        synchronized int concurrentCalls() {
            return concurrentCalls;
        }

        synchronized int totalCalls() {
            return totalCalls;
        }
    }

    private ConcurrencyCounter counter = new ConcurrencyCounter();

    ConcurrencyCounter getCounter() {
        return counter;
    }

    @Asynchronous
    @Bulkhead(value = CONCURRENT_CALLS, waitingTaskQueue = WAITING_TASK_QUEUE)
    public CompletableFuture<String> execute(long sleepMillis) {
        try {
            counter.increment();
            FaultToleranceTest.printStatus("BulkheadBean::execute", "success");
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                // falls through
            }
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        } finally {
            counter.decrement();
        }
    }

    @Asynchronous
    @Bulkhead(value = CONCURRENT_CALLS + 1, waitingTaskQueue = WAITING_TASK_QUEUE + 1)
    public CompletableFuture<String> executePlusOne(long sleepMillis) {
        try {
            counter.increment();
            FaultToleranceTest.printStatus("BulkheadBean::executePlusOne", "success");
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                // falls through
            }
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        } finally {
            counter.decrement();
        }
    }

    @Asynchronous
    @Bulkhead(value = 2, waitingTaskQueue = 1)
    public CompletableFuture<String> executeNoQueue(long sleepMillis) {
        try {
            counter.increment();
            FaultToleranceTest.printStatus("BulkheadBean::executeNoQueue", "success");
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                // falls through
            }
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        } finally {
            counter.decrement();
        }
    }

    @Asynchronous
    @Fallback(fallbackMethod = "onFailure")
    @Bulkhead(value = 2, waitingTaskQueue = 1)
    public CompletableFuture<String> executeNoQueueWithFallback(long sleepMillis) {
        try {
            counter.increment();
            FaultToleranceTest.printStatus("BulkheadBean::executeNoQueue", "success");
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                // falls through
            }
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        } finally {
            counter.decrement();
        }
    }

    public CompletableFuture<String> onFailure(long sleepMillis) {
        FaultToleranceTest.printStatus("BulkheadBean::onFailure()", "success");
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }

    @Asynchronous
    @Bulkhead(value = 1, waitingTaskQueue = 1)
    public CompletableFuture<String> executeCancelInQueue(long sleepMillis) {
        FaultToleranceTest.printStatus("BulkheadBean::executeCancelInQueue " + sleepMillis, "success");
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            // falls through
        }
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }

    @Bulkhead(value = 1)
    public String executeSynchronous(long sleepMillis) {
        FaultToleranceTest.printStatus("BulkheadBean::executeSynchronous " + sleepMillis, "success");
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            // falls through
        }
        return Thread.currentThread().getName();
    }
}
