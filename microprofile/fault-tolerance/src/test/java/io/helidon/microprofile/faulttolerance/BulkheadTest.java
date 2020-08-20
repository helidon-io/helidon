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

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Class BulkheadTest.
 */
public class BulkheadTest extends FaultToleranceTest {

    @Test
    public void testBulkhead() {
        BulkheadBean bean = newBean(BulkheadBean.class);
        CompletableFuture<String>[] calls = getAsyncConcurrentCalls(
            () -> bean.execute(100), BulkheadBean.TOTAL_CALLS);
        waitFor(calls);
        assertThat(bean.getCounter().concurrentCalls(), is(BulkheadBean.CONCURRENT_CALLS));
        assertThat(bean.getCounter().totalCalls(), is(BulkheadBean.TOTAL_CALLS));
    }

    @Test
    public void testBulkheadPlusOne() {
        BulkheadBean bean = newBean(BulkheadBean.class);
        CompletableFuture<String>[] calls = getAsyncConcurrentCalls(
            () -> bean.executePlusOne(100), BulkheadBean.TOTAL_CALLS + 2);
        waitFor(calls);
        assertThat(bean.getCounter().concurrentCalls(), is(BulkheadBean.CONCURRENT_CALLS + 1));
        assertThat(bean.getCounter().totalCalls(), is(BulkheadBean.TOTAL_CALLS + 2));
    }

    @Test
    public void testBulkheadNoQueue() {
        BulkheadBean bean = newBean(BulkheadBean.class);
        CompletableFuture<String>[] calls = getAsyncConcurrentCalls(
            () -> bean.executeNoQueue(2000), 10);
        RuntimeException e = assertThrows(RuntimeException.class, () -> waitFor(calls));
        assertThat(e.getCause().getCause(), instanceOf(BulkheadException.class));
    }

    @Test
    public void testBulkheadNoQueueWithFallback() {
        BulkheadBean bean = newBean(BulkheadBean.class);
        CompletableFuture<String>[] calls = getAsyncConcurrentCalls(
            () -> bean.executeNoQueueWithFallback(2000), 10);
        waitFor(calls);
    }

    @Test
    public void testBulkheadExecuteCancelInQueue() throws Exception {
        BulkheadBean bean = newBean(BulkheadBean.class);
        CompletableFuture<String> f1 = bean.executeCancelInQueue(1000);
        CompletableFuture<String> f2 = bean.executeCancelInQueue(2000);    // should never run
        boolean b = f2.cancel(true);
        assertTrue(b);
        assertTrue(f2.isCancelled());
        assertThrows(CancellationException.class, f2::get);
        assertNotNull(f1.get());
    }

    @Test
    public void testSynchronous() {
        BulkheadBean bean = newBean(BulkheadBean.class);

        // Run 10 threads that attempt to enter bulkhead
        CompletableFuture<Integer>[] calls = FaultToleranceTest.getConcurrentCalls(
                () -> {
                    try {
                        bean.executeSynchronous(2000);
                    } catch (BulkheadException e) {
                        return 0;   // not entered
                    }
                    return 1;       // entered
                },
                10);

        // Check that only one thread entered the bulkhead
        int sum = Arrays.asList(calls).stream().map(c -> {
            try {
                return c.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).reduce(0, Integer::sum);
        assertThat(sum, is(1));
    }

    @Test
    public void testSynchronousWithAsyncCaller() throws Exception {
        BulkheadBean bean = newBean(BulkheadBean.class);
        AsynchronousCallerBean callerBean = newBean(AsynchronousCallerBean.class);
        Callable<Integer> callable = () -> {
            try {
                bean.executeSynchronous(1000);
                return 1;
            } catch (BulkheadException e) {
                return 0;
            }
        };
        CompletableFuture<Integer> f1 = callerBean.submit(callable);
        CompletableFuture<Integer> f2 = callerBean.submit(callable);
        assertThat(f1.get() + f2.get(), is(1));
    }
}
