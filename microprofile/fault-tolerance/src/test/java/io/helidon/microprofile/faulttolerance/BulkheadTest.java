/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Future;

import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Class BulkheadTest.
 */
public class BulkheadTest extends FaultToleranceTest {

    @Test
    public void testBulkhead() {
        BulkheadBean bean = newBean(BulkheadBean.class);
        Future<String>[] calls = getAsyncConcurrentCalls(
            () -> bean.execute(100), BulkheadBean.MAX_CONCURRENT_CALLS);
        assertThat(getThreadNames(calls).size(), is(BulkheadBean.CONCURRENT_CALLS));
    }


    @Test
    public void testBulkheadPlusOne() {
        BulkheadBean bean = newBean(BulkheadBean.class);
        Future<String>[] calls = getAsyncConcurrentCalls(
            () -> bean.executePlusOne(100), BulkheadBean.MAX_CONCURRENT_CALLS + 2);
        assertThat(getThreadNames(calls).size(), is(BulkheadBean.CONCURRENT_CALLS + 1));
    }

    @Test
    public void testBulkheadNoQueue() {
        BulkheadBean bean = newBean(BulkheadBean.class);
        Future<String>[] calls = getAsyncConcurrentCalls(
            () -> bean.executeNoQueue(2000), 10);

        RuntimeException e = assertThrows(RuntimeException.class, () -> getThreadNames(calls));
        assertThat(e.getCause().getCause(), instanceOf(BulkheadException.class));
    }

    @Test
    public void testBulkheadNoQueueWithFallback() {
        BulkheadBean bean = newBean(BulkheadBean.class);
        Future<String>[] calls = getAsyncConcurrentCalls(
            () -> bean.executeNoQueueWithFallback(2000), 10);
        getThreadNames(calls);
    }
}
