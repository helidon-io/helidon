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

import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Class TimeoutTest.
 */
public class TimeoutTest extends FaultToleranceTest {

    @Test
    public void testForceTimeout() {
        TimeoutBean bean = newBean(TimeoutBean.class);
        assertThrows(TimeoutException.class, bean::forceTimeout);
    }

    @Test
    public void testForceTimeoutAsync() throws Exception {
        TimeoutBean bean = newBean(TimeoutBean.class);
        CompletableFuture<String> future = bean.forceTimeoutAsync();
        assertCompleteExceptionally(future, TimeoutException.class);
    }

    @Test
    public void testNoTimeout() throws Exception {
        TimeoutBean bean = newBean(TimeoutBean.class);
        assertThat(bean.noTimeout(), is("success"));
    }

    @Test
    public void testForceTimeoutWithCatch() {
        TimeoutBean bean = newBean(TimeoutBean.class);
        assertThrows(TimeoutException.class, bean::forceTimeoutWithCatch);
    }

    @Test
    public void testTimeoutWithRetries() throws Exception {
        TimeoutBean bean = newBean(TimeoutBean.class);
        assertThat(bean.timeoutWithRetries(), is("success"));
    }

    @Test
    public void testTimeoutWithFallback() throws Exception {
        TimeoutBean bean = newBean(TimeoutBean.class);
        assertThat(bean.timeoutWithFallback(), is("fallback"));
    }

    @Test
    public void testTimeoutWithRetriesAndFallback() throws Exception {
        TimeoutBean bean = newBean(TimeoutBean.class);
        assertThat(bean.timeoutWithRetriesAndFallback(), is("fallback"));
    }

    @Test
    public void testForceTimeoutSleep() {
        TimeoutNoRetryBean bean = newBean(TimeoutNoRetryBean.class);
        long start = System.currentTimeMillis();
        try {
            bean.forceTimeoutSleep();       // can interrupt
        } catch (InterruptedException | TimeoutException e) {
            assertThat(System.currentTimeMillis() - start, is(lessThan(2000L)));
        }
     }

    @Test
    public void testForceTimeoutLoop() {
        TimeoutNoRetryBean bean = newBean(TimeoutNoRetryBean.class);
        long start = System.currentTimeMillis();
        try {
            bean.forceTimeoutLoop();        // cannot interrupt
        } catch (TimeoutException e) {
            assertThat(System.currentTimeMillis() - start, is(greaterThanOrEqualTo(2000L)));
        }
    }
}
