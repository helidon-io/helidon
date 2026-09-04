/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.quic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuicSelectorTest {
    @Test
    void boundedCloseReturnsWhenPollerWorkerDoesNotStop() throws Exception {
        QuicInstance instance = mock(QuicInstance.class);
        QuicConfig config = QuicConfig.create();
        when(instance.quicConfig()).thenReturn(config);
        when(instance.executor()).thenReturn(Runnable::run);
        QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(config);
        QuicSelector.QuicVirtualThreadPoller poller =
                (QuicSelector.QuicVirtualThreadPoller) QuicSelector.createQuicVirtualThreadPoller(
                        instance,
                        runtimeConfig,
                        "test-poller");
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        poller.readLoopExecutor().submit(() -> {
            reading.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        poller.start();
        try {
            assertThat(reading.await(5, TimeUnit.SECONDS), is(true));

            assertThat(poller.close(1, TimeUnit.MILLISECONDS), is(false));
        } finally {
            release.countDown();
        }
        assertThat(poller.close(5, TimeUnit.SECONDS), is(true));
        assertThat(poller.readLoopExecutor().isTerminated(), is(true));
    }

    @Test
    void closeWaitsForPollerTermination() {
        QuicInstance instance = mock(QuicInstance.class);
        QuicConfig config = QuicConfig.create();
        when(instance.quicConfig()).thenReturn(config);
        when(instance.executor()).thenReturn(Runnable::run);
        QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(config);
        QuicSelector.QuicVirtualThreadPoller poller =
                (QuicSelector.QuicVirtualThreadPoller) QuicSelector.createQuicVirtualThreadPoller(
                        instance,
                        runtimeConfig,
                        "test-poller");
        poller.start();

        poller.close();

        assertThat(poller.readLoopExecutor().isTerminated(), is(true));
    }

    @Test
    void closeFromPollerWorkerDoesNotWaitForItself() throws Exception {
        QuicInstance instance = mock(QuicInstance.class);
        QuicConfig config = QuicConfig.create();
        when(instance.quicConfig()).thenReturn(config);
        when(instance.executor()).thenReturn(Runnable::run);
        QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(config);
        QuicSelector.QuicVirtualThreadPoller poller =
                (QuicSelector.QuicVirtualThreadPoller) QuicSelector.createQuicVirtualThreadPoller(
                        instance,
                        runtimeConfig,
                        "test-poller");
        poller.start();

        poller.readLoopExecutor().submit((Runnable) poller::close).get(5, TimeUnit.SECONDS);
        poller.close();

        assertThat(poller.readLoopExecutor().isTerminated(), is(true));
    }
}
