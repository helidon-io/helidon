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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Class BulkheadTest.
 */
public class BulkheadTest extends FaultToleranceTest {

    @Test
    public void testBulkhead() throws Exception {
        BulkheadBean bean = newBean(BulkheadBean.class);
        CompletableFuture<String>[] calls = getConcurrentCalls(
            () -> bean.execute(100), bean.MAX_CONCURRENT_CALLS);
        CompletableFuture.allOf(calls).get();
        assertThat(getThreadNames(calls).size(), is(bean.CONCURRENT_CALLS));
    }

    @Test
    public void testBulkheadPlusOne() throws Exception {
        BulkheadBean bean = newBean(BulkheadBean.class);
        CompletableFuture<String>[] calls = getConcurrentCalls(
            () -> bean.executePlusOne(100), bean.MAX_CONCURRENT_CALLS + 2);
        CompletableFuture.allOf(calls).get();
        assertThat(getThreadNames(calls).size(), is(bean.CONCURRENT_CALLS + 1));
    }

    @Test
    public void testBulkheadNoQueue() throws Exception {
        BulkheadBean bean = newBean(BulkheadBean.class);
        CompletableFuture<String>[] calls = getConcurrentCalls(
            () -> bean.executeNoQueue(2000), 10);
        try {
            CompletableFuture.allOf(calls).get();
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof BulkheadException);
            return;
        }
        fail("ExecutionException was expected!");
    }

    @Test
    public void testBulkheadNoQueueWithFallback() throws Exception {
        BulkheadBean bean = newBean(BulkheadBean.class);
        CompletableFuture<String>[] calls = getConcurrentCalls(
            () -> bean.executeNoQueueWithFallback(2000), 10);
        CompletableFuture.allOf(calls).get();
    }
}
