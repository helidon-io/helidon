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

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

import io.helidon.microprofile.tests.junit5.AddBean;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Test beans with timeout methods.
 */
@AddBean(TimeoutBean.class)
@AddBean(TimeoutNoRetryBean.class)
class TimeoutTest extends FaultToleranceTest {

    @Inject
    private TimeoutBean timeoutBean;

    @Inject
    private TimeoutNoRetryBean timeoutNoRetryBean;

    @Override
    void reset() {
        timeoutBean.reset();
    }

    @Test
    void testForceTimeout() {
        assertThrows(TimeoutException.class, timeoutBean::forceTimeout);
    }

    @Test
    void testForceTimeoutAsync() throws Exception {
        CompletableFuture<String> future = timeoutBean.forceTimeoutAsync();
        assertCompleteExceptionally(future, TimeoutException.class);
    }

    @Test
    void testNoTimeout() throws Exception {
        assertThat(timeoutBean.noTimeout(), is("success"));
    }

    @Test
    void testForceTimeoutWithCatch() {
        assertThrows(TimeoutException.class, timeoutBean::forceTimeoutWithCatch);
    }

    @Test
    void testTimeoutWithRetries() throws Exception {
        assertThat(timeoutBean.timeoutWithRetries(), is("success"));
    }

    @Test
    void testTimeoutWithFallback() throws Exception {
        assertThat(timeoutBean.timeoutWithFallback(), is("fallback"));
    }

    @Test
    void testTimeoutWithRetriesAndFallback() throws Exception {
        assertThat(timeoutBean.timeoutWithRetriesAndFallback(), is("fallback"));
    }

    @Test
    void testForceTimeoutSleep() {
        long start = System.currentTimeMillis();
        try {
            timeoutNoRetryBean.forceTimeoutSleep();       // can interrupt
        } catch (InterruptedException | TimeoutException e) {
            assertThat(System.currentTimeMillis() - start, is(lessThan(2000L)));
        }
     }

    @Test
    void testForceTimeoutLoop() {
        long start = System.currentTimeMillis();
        try {
            timeoutNoRetryBean.forceTimeoutLoop();        // cannot interrupt
        } catch (TimeoutException e) {
            assertThat(System.currentTimeMillis() - start, is(greaterThanOrEqualTo(2000L)));
        }
    }
}
