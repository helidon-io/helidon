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

package io.helidon.microprofile.health.checks;

import java.lang.management.ThreadMXBean;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeadlockHealthCheckTest {
    private ThreadMXBean threadBean;

    @BeforeEach
    void init() {
        threadBean = Mockito.mock(ThreadMXBean.class);
    }

    @Test
    void testThatHealthCheckNameDoesNotChange() {
        DeadlockHealthCheck check = new DeadlockHealthCheck(threadBean);
        HealthCheckResponse response = check.call();
        assertThat("deadlock", equalTo(response.getName()));
    }

    @Test
    void deadlockDetected() {
        Mockito.when(threadBean.findDeadlockedThreads()).thenReturn(new long[] {123, 456}); // Deadlocked!
        DeadlockHealthCheck check = new DeadlockHealthCheck(threadBean);
        HealthCheckResponse response = check.call();
        assertThat(HealthCheckResponse.State.DOWN, equalTo(response.getState()));
        assertFalse(response.getData().isPresent());
    }

    @Test
    void noDeadlockDetected() {
        Mockito.when(threadBean.findDeadlockedThreads()).thenReturn(null); // no deadlock
        DeadlockHealthCheck check = new DeadlockHealthCheck(threadBean);
        HealthCheckResponse response = check.call();
        assertThat(HealthCheckResponse.State.UP, equalTo(response.getState()));
        assertFalse(response.getData().isPresent());
    }
}
