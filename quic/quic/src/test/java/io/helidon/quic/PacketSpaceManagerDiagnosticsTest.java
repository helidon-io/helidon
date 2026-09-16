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

import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.quic.QuicTLSEngine.HandshakeState;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;

import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated("Changes JUL logger levels")
@ResourceLock("java.util.logging")
class PacketSpaceManagerDiagnosticsTest {
    @ParameterizedTest
    @CsvSource({
            "INFO, true, false",
            "FINE, true, false",
            "FINER, false, false",
            "FINER, true, true"
    })
    void fixedAckDeadlineReadsClockOnlyForEnabledDiagnostics(String level, boolean verbose, boolean diagnostics) {
        Logger logger = Logger.getLogger(PacketSpaceManager.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        Deadline receivedAt = Deadline.of(Instant.EPOCH);
        AtomicInteger clockReads = new AtomicInteger();
        AtomicInteger logTags = new AtomicInteger();
        TimeLine timeLine = () -> {
            clockReads.incrementAndGet();
            return receivedAt;
        };
        PacketEmitter emitter = mock(PacketEmitter.class);
        when(emitter.executor()).thenReturn((Executor) Runnable::run);
        when(emitter.isOpen()).thenReturn(true);
        QuicCongestionController controller = mock(QuicCongestionController.class);
        when(controller.canSendPacket()).thenReturn(true);
        when(controller.maxDatagramSize()).thenReturn(1200L);
        QuicTLSEngine tlsEngine = mock(QuicTLSEngine.class);
        when(tlsEngine.handshakeState()).thenReturn(HandshakeState.HANDSHAKE_CONFIRMED);
        QuicRuntimeConfig config = QuicRuntimeConfig.create(QuicConfig.create());
        PacketSpaceManager manager = new PacketSpaceManager(PacketNumberSpace.APPLICATION,
                                                            emitter,
                                                            timeLine,
                                                            QuicRttEstimator.create(config.recovery()),
                                                            controller,
                                                            tlsEngine,
                                                            () -> "diagnostics-test-" + logTags.incrementAndGet(),
                                                            new PacketSpaceManager.PathRecoveryState(0),
                                                            config.transportParameters().ackDelayExponent(),
                                                            config.transportParameters().maxAckDelay().toMillis(),
                                                            _ -> {
                                                            });
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);
        try {
            manager.packetReceived(PacketType.ONERTT, 0, true);
            Deadline ackDeadline = receivedAt.plusMillis(manager.maxAckDelay());
            clockReads.set(0);
            logTags.set(0);
            logger.setLevel(Level.parse(level));

            assertThat(manager.computeNextDeadline(verbose), is(ackDeadline));
            if (diagnostics) {
                assertThat(clockReads.get(), greaterThan(0));
                assertThat(logTags.get(), greaterThan(0));
            } else {
                assertThat(clockReads.get(), is(0));
                assertThat(logTags.get(), is(0));
            }
        } finally {
            try {
                manager.close();
            } finally {
                logger.setLevel(previousLevel);
                logger.setUseParentHandlers(previousUseParentHandlers);
            }
        }
    }
}
