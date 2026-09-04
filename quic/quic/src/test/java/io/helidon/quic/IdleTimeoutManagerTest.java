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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

class IdleTimeoutManagerTest {

    @Test
    void grantsOneOutboundRestartForEachPeerActivityPeriod() {
        IdleTimeoutManager manager = new IdleTimeoutManager(mock(QuicConnectionImpl.class));

        assertThat("No send restart is available before receiving a packet",
                   manager.ackElicitingPacketSent(),
                   is(false));

        manager.peerPacketProcessed();
        assertThat("The first send after a receive restarts the timeout",
                   manager.ackElicitingPacketSent(),
                   is(true));
        assertThat("A second send without another receive does not restart the timeout",
                   manager.ackElicitingPacketSent(),
                   is(false));

        manager.peerPacketProcessed();
        manager.peerPacketProcessed();
        assertThat("Multiple receives still grant only one outbound restart",
                   manager.ackElicitingPacketSent(),
                   is(true));
        assertThat(manager.ackElicitingPacketSent(), is(false));

        manager.peerPacketProcessed();
        assertThat("A later receive grants a new outbound restart",
                   manager.ackElicitingPacketSent(),
                   is(true));
    }

    @Test
    void concurrentSendsConsumeOneOutboundRestart() throws Exception {
        int senders = 32;
        IdleTimeoutManager manager = new IdleTimeoutManager(mock(QuicConnectionImpl.class));
        CountDownLatch ready = new CountDownLatch(senders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>(senders);

        manager.peerPacketProcessed();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < senders; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent send");
                    }
                    return manager.ackElicitingPacketSent();
                }));
            }
            assertThat("Not all concurrent send tasks became ready",
                       ready.await(10, TimeUnit.SECONDS),
                       is(true));
            start.countDown();

            int restarts = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    restarts++;
                }
            }
            assertThat("Exactly one concurrent send must restart the timeout", restarts, is(1));
        } finally {
            start.countDown();
        }
    }
}
