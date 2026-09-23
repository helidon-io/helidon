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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.crypto.AEADBadTagException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.quic.QuicEndpoint.QuicDatagram;
import io.helidon.quic.QuicTLSEngine.HandshakeState;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.QuicTransportParameters.ParameterId;
import io.helidon.quic.QuicTransportParameters.VersionInformation;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.frame.AckFrame.AckRange;
import io.helidon.quic.frame.ConnectionCloseFrame;
import io.helidon.quic.frame.CryptoFrame;
import io.helidon.quic.frame.HandshakeDoneFrame;
import io.helidon.quic.frame.MaxStreamsFrame;
import io.helidon.quic.frame.NewConnectionIDFrame;
import io.helidon.quic.frame.PathChallengeFrame;
import io.helidon.quic.frame.PathResponseFrame;
import io.helidon.quic.frame.PingFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.RetireConnectionIDFrame;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.packet.HandshakePacket;
import io.helidon.quic.packet.InitialPacket;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;
import io.helidon.quic.packet.QuicPacketDecoder;
import io.helidon.quic.packet.QuicPacketEncoder;
import io.helidon.quic.spi.QuicPacketTLSEngine;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated("Changes JUL logger levels and handlers")
@ResourceLock("java.util.logging")
class QuicConnectionImplTest {
    private static final byte[] PEER_CONNECTION_ID = new byte[] {0x11, 0x22, 0x33, 0x44};
    private static final int MAX_INCOMING_CRYPTO_CAPACITY = 64 << 10;

    @Test
    void cancelingBidiReservationBeforeHandshakePreventsAcquisition() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL))) {
            CompletableFuture<?> reservation = harness.connection().reserveNewLocalBidiStream();

            assertThat(reservation.isDone(), is(false));
            assertThat(reservation.cancel(false), is(true));

            harness.connection().completeHandshakeCF();

            assertThat(reservation.isCancelled(), is(true));
        }
    }

    @Test
    void cancelingBidiReservationRacingHandshakeCompletionPreventsAcquisition() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL))) {
            AtomicReference<Runnable> handshakeCompletion = new AtomicReference<>();
            harness.instance().executor = handshakeCompletion::set;
            CompletableFuture<?> reservation = harness.connection().reserveNewLocalBidiStream();

            harness.connection().completeHandshakeCF();
            assertThat(handshakeCompletion.get() != null, is(true));
            assertThat(reservation.cancel(false), is(true));
            handshakeCompletion.get().run();
            harness.instance().executor = Runnable::run;

            assertThat(reservation.isCancelled(), is(true));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelingLocalStreamOpenBeforeHandshakePreservesCredit(boolean bidi) throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            var opened = openLocalStream(harness.connection(), bidi, Duration.ofSeconds(30));

            assertThat(opened.isDone(), is(false));
            assertThat(opened.cancel(false), is(true));

            harness.connection().completeHandshakeCF();
            harness.connection().incoming1RTTFrame(MaxStreamsFrame.create(bidi, 1));

            assertThat(opened.isCancelled(), is(true));
            var next = openLocalStream(harness.connection(), bidi, Duration.ZERO).get(1, TimeUnit.SECONDS);
            assertThat(next.streamId(), is(bidi ? 0L : 2L));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelingLocalStreamOpenRacingHandshakeCompletionPreservesCredit(boolean bidi) throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            var handshakeCompletion = new AtomicReference<Runnable>();
            harness.instance().executor = handshakeCompletion::set;
            var opened = openLocalStream(harness.connection(), bidi, Duration.ofSeconds(30));

            try {
                harness.connection().completeHandshakeCF();
                assertThat(handshakeCompletion.get(), notNullValue());
                assertThat(opened.cancel(false), is(true));
                handshakeCompletion.get().run();
            } finally {
                harness.instance().executor = Runnable::run;
            }
            harness.connection().incoming1RTTFrame(MaxStreamsFrame.create(bidi, 1));

            assertThat(opened.isCancelled(), is(true));
            var next = openLocalStream(harness.connection(), bidi, Duration.ZERO).get(1, TimeUnit.SECONDS);
            assertThat(next.streamId(), is(bidi ? 0L : 2L));
        }
    }

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    void cancelingLocalStreamOpenWhileWaitingForCreditPreservesCredit(boolean bidi,
                                                                     boolean handshakeComplete) throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            if (handshakeComplete) {
                harness.connection().completeHandshakeCF();
            }
            var opened = openLocalStream(harness.connection(), bidi, Duration.ofSeconds(30));
            if (!handshakeComplete) {
                harness.connection().completeHandshakeCF();
            }

            assertThat(opened.isDone(), is(false));
            assertThat(opened.cancel(false), is(true));
            harness.connection().incoming1RTTFrame(MaxStreamsFrame.create(bidi, 1));

            assertThat(opened.isCancelled(), is(true));
            var next = openLocalStream(harness.connection(), bidi, Duration.ZERO).get(1, TimeUnit.SECONDS);
            assertThat(next.streamId(), is(bidi ? 0L : 2L));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelingLocalStreamOpenWithQueuedAcquisitionPreservesCredit(boolean bidi) throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.connection().completeHandshakeCF();
            var deferCompletion = new AtomicBoolean();
            var acquisitionCompletion = new AtomicReference<Runnable>();
            harness.instance().executor = task -> {
                if (deferCompletion.get()) {
                    acquisitionCompletion.set(task);
                } else {
                    task.run();
                }
            };
            var opened = openLocalStream(harness.connection(), bidi, Duration.ofSeconds(30));

            try {
                assertThat(opened.isDone(), is(false));
                deferCompletion.set(true);
                harness.connection().incoming1RTTFrame(MaxStreamsFrame.create(bidi, 1));

                assertThat(acquisitionCompletion.get(), notNullValue());
                assertThat(opened.isDone(), is(false));
                deferCompletion.set(false);
                ExecutionException noCredit = assertThrows(ExecutionException.class,
                                                           () -> openLocalStream(harness.connection(), bidi, Duration.ZERO)
                                                                   .get(1, TimeUnit.SECONDS));
                assertThat(noCredit.getCause(), instanceOf(QuicStreamLimitException.class));
                assertThat(opened.cancel(false), is(true));
                acquisitionCompletion.get().run();
            } finally {
                harness.instance().executor = Runnable::run;
            }

            assertThat(opened.isCancelled(), is(true));
            var next = openLocalStream(harness.connection(), bidi, Duration.ZERO).get(1, TimeUnit.SECONDS);
            assertThat(next.streamId(), is(bidi ? 0L : 2L));
        }
    }

    @Test
    void terminatingConnectionBeforeHandshakeFailsBidiReservation() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL))) {
            CompletableFuture<?> reservation = harness.connection().reserveNewLocalBidiStream();

            harness.connection().terminate(QuicCloseCommand.silent("test reservation termination"));

            CompletionException handshakeFailure = assertThrows(
                    CompletionException.class,
                    () -> harness.connection().handshakeFlow().handshakeCF().join());
            CompletionException reservationFailure = assertThrows(CompletionException.class, reservation::join);
            assertThat(reservationFailure.getCause(), sameInstance(handshakeFailure.getCause()));
        }
    }

    @Test
    void rejectsNullLocalStreamTimeouts() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL))) {
            assertThrows(NullPointerException.class, () -> harness.connection().openNewLocalBidiStream(null));
            assertThrows(NullPointerException.class, () -> harness.connection().openNewLocalUniStream(null));
        }
    }

    @Test
    void connectionCloseDrainsQueuedDatagramAccountingBeforeStoppedTaskCanRun() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL))) {
            AtomicReference<Runnable> scheduled = new AtomicReference<>();
            harness.instance().executor = scheduled::set;
            ByteBuffer packet = initialPacket();
            int packetBytes = packet.remaining();

            harness.connection().processIncoming(harness.connection().peerAddress(),
                                                 ByteBuffer.allocate(0),
                                                 QuicPacket.HeadersType.LONG,
                                                 packet);

            assertThat(harness.instance().endpoint().buffered(), is(packetBytes));
            assertThat(scheduled.get() != null, is(true));
            harness.connection().closeIncoming();
            assertThat(harness.instance().endpoint().buffered(), is(0));

            scheduled.get().run();
            assertThat(harness.instance().endpoint().buffered(), is(0));
        }
    }

    @Test
    void connectionSchedulerRejectionRollsBackQueuedDatagramAccounting() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL))) {
            harness.instance().executor = _ -> {
                throw new RejectedExecutionException("test rejection");
            };
            ByteBuffer packet = initialPacket();

            assertThrows(RejectedExecutionException.class,
                         () -> harness.connection().processIncoming(harness.connection().peerAddress(),
                                                                    ByteBuffer.allocate(0),
                                                                    QuicPacket.HeadersType.LONG,
                                                                    packet));
            assertThat(harness.instance().endpoint().buffered(), is(0));
        }
    }

    @Test
    void switchesVersionWhenDebugLoggingIsDisabled() throws Exception {
        Logger logger = Logger.getLogger(QuicConnectionImpl.class.getName());
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        try {
            assertThat(System.getLogger(QuicConnectionImpl.class.getName())
                               .isLoggable(System.Logger.Level.DEBUG),
                       is(false));
            QuicConfig config = QuicConfig.builder()
                    .availableVersions(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1))
                    .maxUniStreams(4)
                    .buildPrototype();
            FakeQuicTLSEngine engine = new FakeQuicTLSEngine(EnumSet.of(KeySpace.INITIAL));
            try (TestQuicInstance instance = new TestQuicInstance(quicTlsContext(engine), true, config)) {
                TestQuicConnection connection = new TestQuicConnection(QuicVersion.QUIC_V2,
                                                                       instance,
                                                                       QuicRuntimeConfig.create(config));
                connection.startHandshake();
                QuicPacket versions = QuicPacketEncoder.newVersionNegotiationPacket(
                        connection.originalServerConnId(),
                        connection.localConnectionId().orElseThrow(),
                        new int[] {QuicVersion.QUIC_V1.versionNumber()});

                connection.processVersionNegotiationPacket(versions);

                assertThat(connection.quicVersion(), is(QuicVersion.QUIC_V1));
                assertThat(instance.requestedTokenVersions,
                           contains(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1));
            }
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    @ParameterizedTest
    @CsvSource({"QUIC_V1, false", "QUIC_V1, true", "QUIC_V2, false", "QUIC_V2, true"})
    void rejectsClientVersionInformationWithoutChosenVersion(QuicVersion version, boolean emptyAvailable) throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createForVersion(version, false)) {
            TestQuicConnection connection = harness.connection();
            QuicVersion otherVersion = version == QuicVersion.QUIC_V1 ? QuicVersion.QUIC_V2 : QuicVersion.QUIC_V1;
            int[] available = emptyAvailable ? new int[0] : new int[] {otherVersion.versionNumber()};
            QuicTransportParameters parameters = peerTransportParameters(connection);
            parameters.versionInformationParameter(ParameterId.version_information,
                                                   VersionInformation.create(version.versionNumber(), available));

            QuicTransportException failure = assertThrows(QuicTransportException.class,
                                                         () -> connection.consumeQuicParameters(encodeParameters(parameters)));

            assertThat(failure.errorCode(), is(0x08L));
            assertThat(connection.peerTransportParameters().isEmpty(), is(true));
        }
    }

    @ParameterizedTest
    @EnumSource(QuicVersion.class)
    void acceptsClientVersionInformationContainingChosenVersion(QuicVersion version) throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createForVersion(version, false)) {
            TestQuicConnection connection = harness.connection();
            QuicVersion otherVersion = version == QuicVersion.QUIC_V1 ? QuicVersion.QUIC_V2 : QuicVersion.QUIC_V1;
            VersionInformation versionInformation = VersionInformation.create(
                    version.versionNumber(),
                    new int[] {otherVersion.versionNumber(), version.versionNumber()});
            QuicTransportParameters parameters = peerTransportParameters(connection);
            parameters.versionInformationParameter(ParameterId.version_information, versionInformation);

            connection.consumeQuicParameters(encodeParameters(parameters));

            assertThat(connection.peerTransportParameters().orElseThrow()
                               .versionInformationParameter(ParameterId.version_information).orElseThrow(),
                       is(versionInformation));
        }
    }

    @ParameterizedTest
    @CsvSource({"QUIC_V1, false", "QUIC_V1, true", "QUIC_V2, false", "QUIC_V2, true"})
    void acceptsServerVersionInformationWithoutChosenVersion(QuicVersion version, boolean emptyAvailable) throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createForVersion(version, true)) {
            TestQuicConnection connection = harness.connection();
            connection.startHandshake();
            harness.seedPeerConnectionId();
            QuicVersion otherVersion = version == QuicVersion.QUIC_V1 ? QuicVersion.QUIC_V2 : QuicVersion.QUIC_V1;
            int[] available = emptyAvailable ? new int[0] : new int[] {otherVersion.versionNumber()};
            VersionInformation versionInformation = VersionInformation.create(version.versionNumber(), available);
            QuicTransportParameters parameters = peerTransportParameters(connection);
            parameters.versionInformationParameter(ParameterId.version_information, versionInformation);

            connection.consumeQuicParameters(encodeParameters(parameters));

            assertThat(connection.peerTransportParameters().orElseThrow()
                               .versionInformationParameter(ParameterId.version_information).orElseThrow(),
                       is(versionInformation));
        }
    }

    @Test
    void acceptsV1FallbackWithoutVersionInformation() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createForVersion(QuicVersion.QUIC_V2, true)) {
            TestQuicConnection connection = harness.connection();
            connection.startHandshake();
            QuicPacket versions = QuicPacketEncoder.newVersionNegotiationPacket(
                    connection.originalServerConnId(),
                    connection.localConnectionId().orElseThrow(),
                    new int[] {QuicVersion.QUIC_V1.versionNumber()});

            connection.processVersionNegotiationPacket(versions);

            assertThat(connection.quicVersion(), is(QuicVersion.QUIC_V1));
            harness.seedPeerConnectionId();
            QuicTransportParameters parameters = peerTransportParameters(connection);
            parameters.intParameter(ParameterId.initial_max_data, 1234);
            connection.consumeQuicParameters(encodeParameters(parameters));

            QuicTransportParameters published = connection.peerTransportParameters().orElseThrow();
            assertThat(published.isPresent(ParameterId.version_information), is(false));
            assertThat(published.intParameter(ParameterId.initial_max_data), is(1234L));
        }
    }

    @Test
    void discardableAuthenticationFailureDoesNotLogWarningOrError() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, QuicPacketDecoder.class)) {
            harness.engine().decryptFailure =
                    new QuicPacketAuthenticationException("Packet authentication failed", new AEADBadTagException());

            harness.connection().internalProcessIncoming(harness.connection().peerAddress(),
                                                          ByteBuffer.allocate(0),
                                                          QuicPacket.HeadersType.LONG,
                                                          initialPacket());

            assertThat(harness.connection().isOpen(), is(true));
            List<LogRecord> records = logs.records();
            assertThat(records.stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
            assertThat(records.stream()
                               .filter(record -> record.getLevel() == Level.FINE)
                               .filter(record -> record.getMessage().contains("Discarding peer packet after decode failure"))
                               .count(),
                       is(1L));
        }
    }

    @Test
    void decodeUnderflowRemainsDiscardable() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, QuicPacketDecoder.class)) {
            harness.engine().decryptFailure = new BufferUnderflowException();

            harness.connection().internalProcessIncoming(harness.connection().peerAddress(),
                                                          ByteBuffer.allocate(0),
                                                          QuicPacket.HeadersType.LONG,
                                                          initialPacket());

            assertThat(harness.connection().isOpen(), is(true));
            List<LogRecord> records = logs.records();
            assertThat(records.stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
            assertThat(records.stream()
                               .filter(record -> record.getLevel() == Level.FINE)
                               .filter(record -> record.getMessage().contains("Discarding peer packet after decode failure"))
                               .count(),
                       is(1L));
        }
    }

    @Test
    void overLimitAckDiscardsWholePacketAndConnectionRemainsUsable() throws Exception {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxUniStreams(4)
                .maxAckRangesPerFrame(1)
                .buildPrototype();
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL), config);
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, QuicPacketDecoder.class)) {
            TestQuicConnection connection = harness.connection();
            QuicConnectionId localConnectionId = connection.localConnectionId().orElseThrow();
            PacketSpaceManager initialSpace =
                    (PacketSpaceManager) connection.packetSpace(PacketNumberSpace.INITIAL);
            for (long expectedPacketNumber = 0; expectedPacketNumber < 3; expectedPacketNumber++) {
                harness.engine().queueHandshakeFlight(KeySpace.INITIAL, ByteBuffer.allocate(1));
                connection.continueHandshake();
                assertThat(initialSpace.nextPacketNumber().get(), is(expectedPacketNumber + 1));
            }
            assertThat(initialSpace.largestPeerAcknowledgedPacketNumber(), is(-1L));
            ByteBuffer overLimit = connection.encodeIncomingInitial(
                    0,
                    List.of(CryptoFrame.create(0, 1, ByteBuffer.allocate(1)),
                            AckFrame.create(2, 0, List.of(AckRange.INITIAL, AckRange.INITIAL))));

            connection.internalProcessIncoming(connection.peerAddress(),
                                               localConnectionId.asReadOnlyBuffer(),
                                               QuicPacket.HeadersType.LONG,
                                               overLimit);

            assertThat(connection.isOpen(), is(true));
            assertThat(harness.engine().consumedCryptoBytes.get(), is(0));
            assertThat(connection.largestProcessedPN(PacketNumberSpace.INITIAL), is(-1L));
            assertThat(initialSpace.largestPeerAcknowledgedPacketNumber(), is(-1L));
            assertThat(connection.isAcknowledged(PacketNumberSpace.INITIAL, 0), is(false));
            List<LogRecord> records = logs.records();
            assertThat(records.stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
            assertThat(records.stream()
                               .filter(record -> record.getLevel() == Level.FINE)
                               .filter(record -> record.getMessage()
                                       .contains("Discarding peer packet after decode failure"))
                               .count(),
                       is(1L));

            ByteBuffer accepted = connection.encodeIncomingInitial(
                    1,
                    List.of(CryptoFrame.create(0, 1, ByteBuffer.allocate(1))));
            connection.internalProcessIncoming(connection.peerAddress(),
                                               localConnectionId.asReadOnlyBuffer(),
                                               QuicPacket.HeadersType.LONG,
                                               accepted);

            assertThat(connection.isOpen(), is(true));
            assertThat(harness.engine().consumedCryptoBytes.get(), is(1));
            assertThat(connection.largestProcessedPN(PacketNumberSpace.INITIAL), is(1L));
            assertThat(connection.isAcknowledged(PacketNumberSpace.INITIAL, 1), is(true));
        }
    }

    @Test
    void overLimitAckDiscardPrecedesUnsentPacketValidation() throws Exception {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxUniStreams(4)
                .maxAckRangesPerFrame(1)
                .buildPrototype();
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL), config)) {
            TestQuicConnection connection = harness.connection();
            QuicConnectionId localConnectionId = connection.localConnectionId().orElseThrow();
            PacketSpaceManager initialSpace =
                    (PacketSpaceManager) connection.packetSpace(PacketNumberSpace.INITIAL);
            assertThat(initialSpace.nextPacketNumber().get(), is(0L));
            ByteBuffer overLimit = connection.encodeIncomingInitial(
                    0,
                    List.of(AckFrame.create(2, 0, List.of(AckRange.INITIAL, AckRange.INITIAL))));

            connection.internalProcessIncoming(connection.peerAddress(),
                                               localConnectionId.asReadOnlyBuffer(),
                                               QuicPacket.HeadersType.LONG,
                                               overLimit);

            assertThat(connection.isOpen(), is(true));
            assertThat(connection.termination().isEmpty(), is(true));
            assertThat(connection.largestProcessedPN(PacketNumberSpace.INITIAL), is(-1L));
            assertThat(initialSpace.nextPacketNumber().get(), is(0L));
            assertThat(initialSpace.largestPeerAcknowledgedPacketNumber(), is(-1L));
        }
    }

    @Test
    void peerProtocolDecodeFailureClosesWithoutWarningOrError() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, ConnectionTerminatorImpl.class)) {
            QuicTransportException failure = new QuicTransportException("Peer protocol violation",
                                                                          KeySpace.INITIAL,
                                                                          0,
                                                                          QuicTransportErrors.PROTOCOL_VIOLATION);
            harness.engine().decryptFailure = failure;

            harness.connection().internalProcessIncoming(harness.connection().peerAddress(),
                                                          ByteBuffer.allocate(0),
                                                          QuicPacket.HeadersType.LONG,
                                                          initialPacket());

            QuicTermination termination = harness.connection().termination().orElseThrow();
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            List<LogRecord> records = logs.records();
            assertThat(records.stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
            assertThat(records.stream()
                               .filter(record -> record.getLevel() == Level.FINE)
                               .filter(record -> record.getMessage().contains("Closing connection after peer protocol violation"))
                               .count(),
                       is(1L));
        }
    }

    @Test
    void localDecodeFailureLogsErrorAndCloses() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class)) {
            IllegalStateException failure = new IllegalStateException("Local decoder failure");
            harness.engine().decryptFailure = failure;

            harness.connection().internalProcessIncoming(harness.connection().peerAddress(),
                                                          ByteBuffer.allocate(0),
                                                          QuicPacket.HeadersType.LONG,
                                                          initialPacket());

            QuicTermination termination = harness.connection().termination().orElseThrow();
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            assertThat(logs.records().stream()
                               .filter(record -> record.getLevel() == Level.SEVERE)
                               .count(),
                       is(1L));
        }
    }

    @Test
    void localTransportDecodeFailureLogsErrorAndCloses() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, ConnectionTerminatorImpl.class)) {
            IllegalStateException cause = new IllegalStateException("Local provider failure");
            QuicTransportException failure = new QuicTransportException("Local transport failure",
                                                                          KeySpace.INITIAL,
                                                                          0,
                                                                          QuicTransportErrors.INTERNAL_ERROR.code(),
                                                                          cause);
            harness.engine().decryptFailure = failure;

            harness.connection().internalProcessIncoming(harness.connection().peerAddress(),
                                                          ByteBuffer.allocate(0),
                                                          QuicPacket.HeadersType.LONG,
                                                          initialPacket());

            QuicTermination termination = harness.connection().termination().orElseThrow();
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            assertThat(logs.records().stream()
                               .filter(record -> record.getLevel() == Level.SEVERE)
                               .count(),
                       is(1L));
        }
    }

    @Test
    void causeLessInternalTransportFailureLogsErrorAndCloses() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, ConnectionTerminatorImpl.class)) {
            QuicTransportException failure = new QuicTransportException("Unsupported local handshake message",
                                                                          KeySpace.INITIAL,
                                                                          0,
                                                                          QuicTransportErrors.INTERNAL_ERROR);
            harness.engine().decryptFailure = failure;

            harness.connection().internalProcessIncoming(harness.connection().peerAddress(),
                                                          ByteBuffer.allocate(0),
                                                          QuicPacket.HeadersType.LONG,
                                                          initialPacket());

            QuicTermination termination = harness.connection().termination().orElseThrow();
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            assertThat(logs.records().stream()
                               .filter(record -> record.getLevel() == Level.SEVERE)
                               .count(),
                       is(1L));
        }
    }

    @Test
    void authenticatedProcessingUnderflowLogsErrorAndCloses() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, ConnectionTerminatorImpl.class)) {
            QuicPacket packet = mock(QuicPacket.class);
            when(packet.packetType()).thenReturn(PacketType.INITIAL);
            BufferUnderflowException failure = new BufferUnderflowException();

            harness.connection().onProcessingError(packet, failure);

            QuicTermination termination = harness.connection().termination().orElseThrow();
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            assertThat(logs.records().stream()
                               .filter(record -> record.getLevel() == Level.SEVERE)
                               .count(),
                       is(1L));
        }
    }

    @Test
    void localHandshakeSchedulerFailureLogsErrorAndTerminates() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, ConnectionTerminatorImpl.class)) {
            IllegalStateException failure = new IllegalStateException("Local handshake failure");
            harness.engine().handshakeFailure = failure;

            harness.connection().continueHandshake0();

            QuicTermination termination = harness.connection().termination().orElseThrow();
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            assertThat(harness.connection().handshakeFlow().handshakeCF().isCompletedExceptionally(), is(true));
            assertThat(logs.records().stream()
                               .filter(record -> record.getLevel() == Level.SEVERE)
                               .count(),
                       is(1L));
        }
    }

    @Test
    void peerHandshakeProtocolFailureClosesWithoutWarningOrError() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, ConnectionTerminatorImpl.class)) {
            QuicTransportException failure = new QuicTransportException("Peer handshake violation",
                                                                          KeySpace.INITIAL,
                                                                          0,
                                                                          QuicTransportErrors.PROTOCOL_VIOLATION);
            harness.engine().handshakeFailure = failure;

            harness.connection().continueHandshake0();

            QuicTermination termination = harness.connection().termination().orElseThrow();
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            List<LogRecord> records = logs.records();
            assertThat(records.stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
            assertThat(records.stream()
                               .filter(record -> record.getLevel() == Level.FINE)
                               .filter(record -> record.getMessage().contains("peer handshake protocol violation"))
                               .count(),
                       is(1L));
        }
    }

    @Test
    void silentHandshakeRejectionDoesNotLogOrSendClose() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, ConnectionTerminatorImpl.class)) {
            QuicSilentTlsRejection failure =
                    new QuicSilentTlsRejection(new IllegalArgumentException("Rejected server name"));
            harness.engine().handshakeFailure = failure;

            harness.connection().continueHandshake0();

            QuicTermination termination = harness.connection().termination().orElseThrow();
            assertThat(termination.kind(), is(QuicTermination.Kind.SILENT));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            assertThat(termination.errorCode().isEmpty(), is(true));
            assertThat(logs.records().stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
        }
    }

    @Test
    void mapsAllocatedDatagramOverflowToInternalError() {
        QuicPacket packet = mock(QuicPacket.class);
        when(packet.packetType()).thenReturn(PacketType.INITIAL);
        ByteBuffer datagram = ByteBuffer.allocate(0);
        CodingContext context = mock(CodingContext.class);
        BufferOverflowException overflow = new BufferOverflowException();
        when(context.writePacket(packet, datagram)).thenThrow(overflow);
        QuicConnectionImpl.ProtectionRecord record =
                QuicConnectionImpl.ProtectionRecord.single(packet, _ -> datagram);

        QuicTransportException failure =
                assertThrows(QuicTransportException.class, () -> record.encrypt(context));

        assertThat(failure.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(failure.keySpace().orElseThrow(), is(KeySpace.INITIAL));
        assertThat(failure.getCause(), sameInstance(overflow));
    }

    @Test
    void boundsLargePeerMaxUdpPayloadSizeByLocalPath() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            for (long advertised : List.of(65_528L, VariableLengthEncoder.MAX_ENCODED_INTEGER)) {
                QuicTransportParameters peerParameters = QuicTransportParameters.create();
                peerParameters.intParameter(QuicTransportParameters.ParameterId.max_udp_payload_size, advertised);

                harness.connection.installPeerTransportParameters(peerParameters);

                assertThat(harness.connection.peerTransportParameters()
                                   .orElseThrow()
                                   .intParameter(QuicTransportParameters.ParameterId.max_udp_payload_size),
                           is(advertised));
                assertThat(harness.connection.maxDatagramSize(), is(QuicRuntimeConfig.DEFAULT_DATAGRAM_SIZE));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "FINE", "FINER"})
    void reusesReleasedDirectBufferAfterPoolExhaustion(String level) throws Exception {
        try (var logs = new TestLogCapture(Level.parse(level), QuicConnectionImpl.class);
             ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            TestQuicConnection connection = harness.connection();
            int size = connection.maxDatagramSize();
            ByteBuffer first = connection.outgoingByteBuffer(size);
            ByteBuffer second = connection.outgoingByteBuffer(size);
            ByteBuffer third = connection.outgoingByteBuffer(size);
            ByteBuffer overflow = connection.outgoingByteBuffer(size);

            assertThat(first.isDirect(), is(true));
            assertThat(second.isDirect(), is(true));
            assertThat(third.isDirect(), is(true));
            assertThat(overflow.isDirect(), is(false));
            assertThat(overflow.remaining(), is(size));

            first.putLong(0x0102030405060708L).flip();
            connection.datagramReleased(QuicDatagram.create(connection, connection.peerAddress(), first));
            ByteBuffer reused = connection.outgoingByteBuffer(size);

            assertThat(reused, sameInstance(first));
            assertThat(reused.position(), is(0));
            assertThat(reused.limit(), is(size));
            assertThat(reused.isDirect(), is(true));

            for (ByteBuffer buffer : List.of(reused, second, third, overflow)) {
                connection.datagramReleased(QuicDatagram.create(connection, connection.peerAddress(), buffer));
            }
            List<String> bufferMessages = logs.records().stream()
                    .map(LogRecord::getMessage)
                    .filter(message -> message.contains("DIRECTBB:"))
                    .toList();
            if (level.equals("FINER")) {
                assertThat(bufferMessages, hasItem(containsString("allocating direct buffer")));
                assertThat(bufferMessages, hasItem(containsString("got direct buffer from pool")));
                assertThat(bufferMessages, hasItem(containsString("offering buffer to pool")));
            } else {
                assertThat(bufferMessages, empty());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "FINE", "FINER"})
    void oversizedDatagramUsesHeapWithoutConsumingDirectPoolCapacity(String level) throws Exception {
        try (var _ = new TestLogCapture(Level.parse(level), QuicConnectionImpl.class);
             ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            TestQuicConnection connection = harness.connection();
            int size = connection.maxDatagramSize();
            ByteBuffer oversized = connection.outgoingByteBuffer(size + 1);

            assertThat(oversized.isDirect(), is(false));
            assertThat(oversized.remaining(), is(size + 1));
            connection.datagramReleased(QuicDatagram.create(connection, connection.peerAddress(), oversized));

            List<ByteBuffer> pooled = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                ByteBuffer buffer = connection.outgoingByteBuffer(size);
                assertThat(buffer.isDirect(), is(true));
                assertThat(buffer.remaining(), is(size));
                pooled.add(buffer);
            }
            for (ByteBuffer buffer : pooled) {
                connection.datagramReleased(QuicDatagram.create(connection, connection.peerAddress(), buffer));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "FINE", "FINER"})
    void disabledDirectPoolAllocatesIndependentHeapBuffers(String level) throws Exception {
        try (var _ = new TestLogCapture(Level.parse(level), QuicConnectionImpl.class);
             ConnectionHarness harness = ConnectionHarness.createWithDirectBufferPool(false)) {
            TestQuicConnection connection = harness.connection();
            ByteBuffer first = connection.outgoingByteBuffer(64);
            assertThat(first.isDirect(), is(false));
            assertThat(first.remaining(), is(64));
            connection.datagramReleased(QuicDatagram.create(connection, connection.peerAddress(), first));

            ByteBuffer second = connection.outgoingByteBuffer(64);
            assertThat(second.isDirect(), is(false));
            assertThat(second.remaining(), is(64));
            assertThat(second, not(sameInstance(first)));
            connection.datagramReleased(QuicDatagram.create(connection, connection.peerAddress(), second));
        }
    }

    @Test
    void shouldDispatchEmptyFinWithZeroConnectionCredit() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            QuicTransportParameters peerParameters = QuicTransportParameters.create();
            peerParameters.intParameter(QuicTransportParameters.ParameterId.initial_max_data, 0);
            peerParameters.intParameter(QuicTransportParameters.ParameterId.initial_max_streams_uni, 1);
            harness.connection.installPeerTransportParameters(peerParameters);
            QuicSenderStream stream = harness.connection.streams()
                    .createNewLocalUniStream(Duration.ZERO)
                    .join();
            QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
            }));

            CompletableFuture<Void> dispatch = writer.scheduleForWritingAndGetDispatchCompletion(BufferData.create(0), true);

            assertThat(dispatch.isDone(), is(true));
            StreamFrame streamFrame = harness.connection.applicationPackets().stream()
                    .flatMap(packet -> packet.frames().stream())
                    .filter(StreamFrame.class::isInstance)
                    .map(StreamFrame.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(streamFrame.streamId(), is(stream.streamId()));
            assertThat(streamFrame.isLast(), is(true));
        }
    }

    @Test
    void shouldCompleteSuccessfulDispatchBeforeConcurrentTermination() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            QuicTransportParameters peerParameters = QuicTransportParameters.create();
            peerParameters.intParameter(QuicTransportParameters.ParameterId.initial_max_data, 1);
            peerParameters.intParameter(QuicTransportParameters.ParameterId.initial_max_stream_data_uni, 1);
            peerParameters.intParameter(QuicTransportParameters.ParameterId.initial_max_streams_uni, 1);
            harness.connection.installPeerTransportParameters(peerParameters);
            QuicSenderStream stream = harness.connection.streams()
                    .createNewLocalUniStream(Duration.ZERO)
                    .join();
            QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
            }));
            CountDownLatch applicationSendStarted = new CountDownLatch(1);
            CountDownLatch continueApplicationSend = new CountDownLatch(1);
            CountDownLatch terminationStarted = new CountDownLatch(1);
            harness.connection.applicationSendStarted = applicationSendStarted;
            harness.connection.continueApplicationSend = continueApplicationSend;
            CompletableFuture<CompletableFuture<Void>> submission = CompletableFuture.supplyAsync(() ->
                    writer.scheduleForWritingAndGetDispatchCompletion(BufferData.create(new byte[] {1}), false));
            CompletableFuture<Void> termination = null;
            try {
                assertThat(applicationSendStarted.await(10, TimeUnit.SECONDS), is(true));
                termination = CompletableFuture.runAsync(() -> {
                    terminationStarted.countDown();
                    harness.connection.terminate(QuicCloseCommand.silent("test termination"));
                });
                assertThat(terminationStarted.await(10, TimeUnit.SECONDS), is(true));

                continueApplicationSend.countDown();

                CompletableFuture<Void> dispatch = submission.get(10, TimeUnit.SECONDS);
                termination.get(10, TimeUnit.SECONDS);
                assertThat(dispatch.isDone(), is(true));
                assertThat(dispatch.isCompletedExceptionally(), is(false));
            } finally {
                continueApplicationSend.countDown();
                harness.connection.applicationSendStarted = null;
                harness.connection.continueApplicationSend = null;
                submission.get(10, TimeUnit.SECONDS);
                if (termination != null) {
                    termination.get(10, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Test
    void shouldCompleteSuccessfulDispatchBeforeConcurrentReset() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            QuicTransportParameters peerParameters = QuicTransportParameters.create();
            peerParameters.intParameter(QuicTransportParameters.ParameterId.initial_max_data, 1);
            peerParameters.intParameter(QuicTransportParameters.ParameterId.initial_max_stream_data_uni, 1);
            peerParameters.intParameter(QuicTransportParameters.ParameterId.initial_max_streams_uni, 1);
            harness.connection.installPeerTransportParameters(peerParameters);
            QuicSenderStream stream = harness.connection.streams()
                    .createNewLocalUniStream(Duration.ZERO)
                    .join();
            QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
            }));
            CountDownLatch applicationSendStarted = new CountDownLatch(1);
            CountDownLatch continueApplicationSend = new CountDownLatch(1);
            CountDownLatch resetStarted = new CountDownLatch(1);
            harness.connection.applicationSendStarted = applicationSendStarted;
            harness.connection.continueApplicationSend = continueApplicationSend;
            CompletableFuture<CompletableFuture<Void>> submission = CompletableFuture.supplyAsync(() ->
                    writer.scheduleForWritingAndGetDispatchCompletion(BufferData.create(new byte[] {1}), false));
            CompletableFuture<Void> reset = null;
            try {
                assertThat(applicationSendStarted.await(10, TimeUnit.SECONDS), is(true));
                reset = CompletableFuture.runAsync(() -> {
                    resetStarted.countDown();
                    stream.reset(0x10c);
                });
                assertThat(resetStarted.await(10, TimeUnit.SECONDS), is(true));

                continueApplicationSend.countDown();

                CompletableFuture<Void> dispatch = submission.get(10, TimeUnit.SECONDS);
                reset.get(10, TimeUnit.SECONDS);
                assertThat(dispatch.isDone(), is(true));
                assertThat(dispatch.isCompletedExceptionally(), is(false));
            } finally {
                continueApplicationSend.countDown();
                harness.connection.applicationSendStarted = null;
                harness.connection.continueApplicationSend = null;
                submission.get(10, TimeUnit.SECONDS);
                if (reset != null) {
                    reset.get(10, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Test
    void shouldOnlySignalCleanupAfterEveryCleanupStepSucceeds() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT));
             TestLogCapture logs = new TestLogCapture(ConnectionTerminatorImpl.class)) {
            RuntimeException incomingFailure = new IllegalStateException("incoming cleanup failed");
            RuntimeException streamFailure = new IllegalStateException("stream cleanup failed");
            harness.connection.closeIncomingFailure = incomingFailure;
            harness.connection.streamTerminationFailure = streamFailure;
            try {
                harness.connection.terminate(QuicCloseCommand.silent("test cleanup"));
                CompletionException thrown = assertThrows(CompletionException.class,
                                                           () -> harness.connection.cleanupComplete()
                                                                   .toCompletableFuture()
                                                                   .join());

                assertThat(thrown.getCause(), sameInstance(incomingFailure));
                assertThat(List.of(thrown.getCause().getSuppressed()), contains(streamFailure));
                assertThat(harness.connection.streamTerminationAttempts.get(), is(1));
                assertThat(harness.connection.packetSpace(PacketNumberSpace.APPLICATION).isClosed(), is(true));
                assertThat(harness.connection.cleanupComplete().toCompletableFuture().isCompletedExceptionally(), is(true));
                CompletionException connectionTermination = assertThrows(
                        CompletionException.class,
                        () -> harness.connection.whenTerminated().toCompletableFuture().join());
                assertThat(connectionTermination.getCause(), sameInstance(incomingFailure));
                assertThat(logs.records().stream()
                                   .filter(record -> record.getLevel() == Level.SEVERE)
                                   .filter(record -> record.getMessage().contains("Failed to terminate QUIC connection"))
                                   .count(),
                           is(1L));
            } finally {
                harness.connection.closeIncomingFailure = null;
                harness.connection.streamTerminationFailure = null;
            }
        }
    }

    @Test
    void statelessResetCleanupFailureLogsError() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT));
             TestLogCapture logs = new TestLogCapture(ConnectionTerminatorImpl.class)) {
            RuntimeException failure = new IllegalStateException("stateless reset cleanup failed");
            harness.connection.closeIncomingFailure = failure;
            try {
                harness.connection.processStatelessReset();

                CompletionException cleanup = assertThrows(CompletionException.class,
                                                             () -> harness.connection.cleanupComplete()
                                                                     .toCompletableFuture()
                                                                     .join());
                assertThat(cleanup.getCause(), sameInstance(failure));
                assertThat(logs.records().stream()
                                   .filter(record -> record.getLevel() == Level.SEVERE)
                                   .filter(record -> record.getMessage().contains("Failed to process peer stateless reset"))
                                   .count(),
                           is(1L));
            } finally {
                harness.connection.closeIncomingFailure = null;
            }
        }
    }

    @Test
    void terminationCleanupErrorIsReportedAndRethrown() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT));
             TestLogCapture logs = new TestLogCapture(ConnectionTerminatorImpl.class)) {
            AssertionError failure = new AssertionError("fatal cleanup failure");
            harness.connection.closeIncomingError = failure;
            try {
                AssertionError thrown = assertThrows(
                        AssertionError.class,
                        () -> harness.connection.terminate(QuicCloseCommand.silent("test cleanup error")));
                CompletionException cleanup = assertThrows(CompletionException.class,
                                                             () -> harness.connection.cleanupComplete()
                                                                     .toCompletableFuture()
                                                                     .join());

                assertThat(thrown, sameInstance(failure));
                assertThat(cleanup.getCause(), sameInstance(failure));
                assertThat(logs.records().stream()
                                   .filter(record -> record.getLevel() == Level.SEVERE)
                                   .filter(record -> record.getMessage().contains("Failed to terminate QUIC connection"))
                                   .count(),
                           is(1L));
            } finally {
                harness.connection.closeIncomingError = null;
            }
        }
    }

    @Test
    void shouldSignalSuccessfulSilentCleanup() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.connection.terminate(QuicCloseCommand.silent("test cleanup"));

            assertThat(harness.connection.cleanupComplete().toCompletableFuture().isDone(), is(true));
            assertThat(harness.connection.cleanupComplete().toCompletableFuture().isCompletedExceptionally(), is(false));
        }
    }

    @Test
    void handshakeTimeoutClosesAtInitialLevel() throws Exception {
        assertServerHandshakeTimeoutClose(EnumSet.of(KeySpace.INITIAL),
                                          List.of(KeySpace.INITIAL));
    }

    @Test
    void handshakeTimeoutClosesAtHandshakeAndInitialLevels() throws Exception {
        assertServerHandshakeTimeoutClose(EnumSet.of(KeySpace.INITIAL, KeySpace.HANDSHAKE),
                                          List.of(KeySpace.HANDSHAKE, KeySpace.INITIAL));
    }

    @Test
    void handshakeTimeoutClosesAtAllAvailableHandshakeLevels() throws Exception {
        assertServerHandshakeTimeoutClose(EnumSet.of(KeySpace.INITIAL, KeySpace.HANDSHAKE, KeySpace.ONE_RTT),
                                          List.of(KeySpace.ONE_RTT, KeySpace.HANDSHAKE, KeySpace.INITIAL));
    }

    @Test
    void handshakeTimeoutIsSilentOnUnvalidatedPathDespiteAmplificationCredit() throws Exception {
        try (ConnectionHarness harness =
                     ConnectionHarness.createServer(EnumSet.of(KeySpace.INITIAL, KeySpace.HANDSHAKE, KeySpace.ONE_RTT))) {
            TimeoutException timeout = new TimeoutException("test handshake timeout");
            harness.connection.pathManager().receive(harness.connection.peerAddress(), 10_000);

            harness.connection.terminate(QuicCloseCommand.serverHandshakeTimeout(timeout, timeout.getMessage()));

            QuicTermination termination = harness.connection.whenTerminated().toCompletableFuture().join();
            assertThat(termination.kind(), is(QuicTermination.Kind.SILENT));
            assertThat(termination.cause().orElseThrow(), sameInstance(timeout));
            assertThat(harness.engine.closeKeySpaces, is(List.of()));
        }
    }

    @Test
    void handshakeTimeoutIsSilentWhenNoKeysRemain() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.noneOf(KeySpace.class))) {
            TimeoutException timeout = new TimeoutException("test handshake timeout");
            harness.connection.pathManager().addressValidated(harness.connection.peerAddress());

            harness.connection.terminate(QuicCloseCommand.serverHandshakeTimeout(timeout, timeout.getMessage()));

            QuicTermination termination = harness.connection.whenTerminated().toCompletableFuture().join();
            assertThat(termination.kind(), is(QuicTermination.Kind.SILENT));
            assertThat(termination.cause().orElseThrow(), sameInstance(timeout));
            assertThat(harness.engine.closeKeySpaces, is(List.of()));
        }
    }

    @Test
    void handshakeTimeoutContinuesAtLowerLevelsAfterKeyDiscardRace() throws Exception {
        try (ConnectionHarness harness =
                     ConnectionHarness.createServer(EnumSet.of(KeySpace.INITIAL, KeySpace.HANDSHAKE, KeySpace.ONE_RTT));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, ConnectionTerminatorImpl.class)) {
            TimeoutException timeout = new TimeoutException("test handshake timeout");
            harness.connection.pathManager().addressValidated(harness.connection.peerAddress());
            harness.engine.unavailableOnEncrypt = KeySpace.ONE_RTT;

            harness.connection.terminate(QuicCloseCommand.serverHandshakeTimeout(timeout, timeout.getMessage()));

            QuicTermination termination = harness.connection.whenTerminated().toCompletableFuture().join();
            assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
            assertThat(termination.keySpace().orElseThrow(), is(KeySpace.ONE_RTT));
            assertThat(harness.engine.closeKeySpaces, is(List.of(KeySpace.HANDSHAKE, KeySpace.INITIAL)));
            assertThat(logs.records().stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
        }
    }

    @Test
    void shouldCompleteTerminationOnlyAfterCleanupFinishes() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            CountDownLatch closeIncomingStarted = new CountDownLatch(1);
            CountDownLatch continueCloseIncoming = new CountDownLatch(1);
            harness.connection.closeIncomingStarted = closeIncomingStarted;
            harness.connection.continueCloseIncoming = continueCloseIncoming;
            QuicCloseCommand command = QuicCloseCommand.silent("test cleanup ordering");
            CompletableFuture<Void> termination = CompletableFuture.runAsync(() -> harness.connection.terminate(command));
            try {
                assertThat(closeIncomingStarted.await(10, TimeUnit.SECONDS), is(true));
                assertThat(harness.connection.whenTerminated().toCompletableFuture().isDone(), is(false));
                assertThat(harness.connection.cleanupComplete().toCompletableFuture().isDone(), is(false));

                continueCloseIncoming.countDown();
                termination.get(10, TimeUnit.SECONDS);

                QuicTermination selected = harness.connection.whenTerminated().toCompletableFuture().join();
                assertThat(selected.origin(), is(QuicTermination.Origin.LOCAL));
                assertThat(selected.kind(), is(QuicTermination.Kind.SILENT));
                assertThat(selected.logMessage(), is("test cleanup ordering"));
                assertThat(harness.connection.cleanupComplete().toCompletableFuture().isDone(), is(true));
            } finally {
                continueCloseIncoming.countDown();
                harness.connection.closeIncomingStarted = null;
                harness.connection.continueCloseIncoming = null;
                termination.get(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void shouldCompleteTerminationWhenConnectionExecutorRejects() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            RejectedExecutionException rejection = new RejectedExecutionException("test rejection");
            harness.instance.executor = command -> {
                throw rejection;
            };
            QuicCloseCommand command = QuicCloseCommand.silent("test rejected executor");

            harness.connection.terminate(command);

            QuicTermination selected = harness.connection.whenTerminated().toCompletableFuture().join();
            assertThat(selected.origin(), is(QuicTermination.Origin.LOCAL));
            assertThat(selected.kind(), is(QuicTermination.Kind.SILENT));
            assertThat(selected.logMessage(), is("test rejected executor"));
            harness.instance.executor = Runnable::run;
        }
    }

    @Test
    void shouldFailHandshakeFutureWhenConnectionExecutorRejects() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            RejectedExecutionException rejection = new RejectedExecutionException("test rejection");
            harness.instance.executor = command -> {
                throw rejection;
            };

            harness.connection.completeHandshakeCF();

            CompletionException thrown = assertThrows(
                    CompletionException.class,
                    () -> harness.connection.handshakeFlow().handshakeCF().join());
            assertThat(thrown.getCause(), sameInstance(rejection));
            harness.instance.executor = Runnable::run;
        }
    }

    @Test
    void shouldKeepOneCleanupOwnerAcrossLocalAndPeerClose() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            CountDownLatch pushStarted = new CountDownLatch(1);
            CountDownLatch continuePush = new CountDownLatch(1);
            harness.connection.pushStarted = pushStarted;
            harness.connection.continuePush = continuePush;
            CompletableFuture<Void> localClose = CompletableFuture.runAsync(() ->
                    harness.connection.terminate(QuicCloseCommand.application(0, "test local close")));
            try {
                assertThat(pushStarted.await(10, TimeUnit.SECONDS), is(true));

                harness.connection.receiveConnectionClose(
                        ConnectionCloseFrame.create(QuicTransportErrors.NO_ERROR.code(),
                                                    0,
                                                    "test peer close"));

                assertThat(harness.connection.cleanupComplete().toCompletableFuture().isDone(), is(false));
                continuePush.countDown();
                localClose.get(10, TimeUnit.SECONDS);
                assertThat(harness.connection.cleanupComplete().toCompletableFuture().isDone(), is(true));
                assertThat(harness.connection.cleanupComplete().toCompletableFuture().isCompletedExceptionally(), is(false));
            } finally {
                continuePush.countDown();
                harness.connection.pushStarted = null;
                harness.connection.continuePush = null;
                localClose.get(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void shouldPreservePeerCloseWhenApplicationErrorFormatterFails() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            RuntimeException formatterFailure = new IllegalStateException("application error formatter failed");
            harness.instance.appErrorFailure = formatterFailure;
            ConnectionCloseFrame frame = ConnectionCloseFrame.create(0x100, "test peer close");

            harness.connection.receiveConnectionClose(frame);
            QuicTermination termination = harness.connection.whenTerminated().toCompletableFuture().join();

            assertThat(termination.origin(), is(QuicTermination.Origin.PEER));
            assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
            assertThat(termination.layer(), is(QuicTermination.Layer.APPLICATION));
            assertThat(termination.errorCode().orElseThrow(), is(0x100L));
            assertThat(termination.keySpace().orElseThrow(), is(KeySpace.ONE_RTT));
            assertThat(termination.peerReason().orElseThrow(), is("test peer close"));
            assertThat(termination.cause(), is(Optional.empty()));
            assertThat(harness.connection.isOpen(), is(false));
            assertThat(harness.connection.closeIncomingAttempts.get(), is(1));
            assertThat(harness.connection.streamTerminationAttempts.get(), is(1));
            assertThat(harness.connection.packetSpace(PacketNumberSpace.APPLICATION).isClosed(), is(true));
            assertThat(harness.connection.cleanupComplete().toCompletableFuture().isDone(), is(true));
            assertThat(harness.connection.cleanupComplete().toCompletableFuture().isCompletedExceptionally(), is(false));

            harness.connection.terminate(QuicCloseCommand.silent("later local termination"));
            assertThat(harness.connection.whenTerminated().toCompletableFuture().join(), sameInstance(termination));
            assertThat(harness.connection.cleanupComplete().toCompletableFuture().isCompletedExceptionally(), is(false));
        }
    }

    @Test
    void shouldRejectNullApplicationErrorDescription() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.connection.applicationErrors(errorCode -> null);

            NullPointerException exception = assertThrows(NullPointerException.class,
                                                          () -> harness.connection.appErrorToString(0x100));

            assertThat(exception.getMessage(), is("application error formatter result"));
        }
    }

    @Test
    void shouldRetainPeerObservationWhenCleanupFails() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            RuntimeException formatterFailure = new IllegalStateException("application error formatter failed");
            RuntimeException cleanupFailure = new IllegalStateException("fallback cleanup failed");
            harness.instance.appErrorFailure = formatterFailure;
            harness.connection.closeIncomingFailure = cleanupFailure;
            ConnectionCloseFrame frame = ConnectionCloseFrame.create(0x100, "test peer close");
            try {
                RuntimeException thrown = assertThrows(RuntimeException.class,
                                                         () -> harness.connection.receiveConnectionClose(frame));
                CompletionException completedCleanup = assertThrows(CompletionException.class,
                                                                      () -> harness.connection.cleanupComplete()
                                                                              .toCompletableFuture()
                                                                              .join());

                assertThat(thrown, sameInstance(cleanupFailure));
                assertThat(completedCleanup.getCause(), sameInstance(cleanupFailure));
                QuicTermination selected = harness.connection.termination().orElseThrow();
                assertThat(selected.origin(), is(QuicTermination.Origin.PEER));
                assertThat(selected.layer(), is(QuicTermination.Layer.APPLICATION));
                assertThat(selected.keySpace().orElseThrow(), is(KeySpace.ONE_RTT));
                assertThat(selected.peerReason().orElseThrow(), is("test peer close"));
                CompletionException connectionTermination = assertThrows(
                        CompletionException.class,
                        () -> harness.connection.whenTerminated().toCompletableFuture().join());
                assertThat(connectionTermination.getCause(), sameInstance(cleanupFailure));
                assertThat(harness.connection.cleanupComplete().toCompletableFuture().isCompletedExceptionally(), is(true));
            } finally {
                harness.connection.closeIncomingFailure = null;
            }
        }
    }

    @Test
    void shouldNotRepeatCompletedPeerDrainCleanup() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            RuntimeException cleanupFailure = new IllegalStateException("peer drain cleanup failed");
            harness.connection.closeIncomingFailure = cleanupFailure;
            ConnectionCloseFrame frame = ConnectionCloseFrame.create(QuicTransportErrors.NO_ERROR.code(),
                                                                      0,
                                                                      "test peer close");
            try {
                RuntimeException thrown = assertThrows(RuntimeException.class,
                                                         () -> harness.connection.receiveConnectionClose(frame));
                CompletionException completedCleanup = assertThrows(CompletionException.class,
                                                                      () -> harness.connection.cleanupComplete()
                                                                              .toCompletableFuture()
                                                                              .join());

                assertThat(thrown, sameInstance(cleanupFailure));
                assertThat(completedCleanup.getCause(), sameInstance(cleanupFailure));
                assertThat(harness.connection.closeIncomingAttempts.get(), is(1));
                assertThat(harness.connection.streamTerminationAttempts.get(), is(1));
                assertThat(harness.connection.cleanupComplete().toCompletableFuture().isCompletedExceptionally(), is(true));
            } finally {
                harness.connection.closeIncomingFailure = null;
            }
        }
    }

    @Test
    void shouldEnterDrainingWhenPeerCloseResponseExceedsPathBudget() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            QuicEndpoint endpoint = harness.connection.endpoint();
            endpoint.registerNewConnection(harness.connection);
            QuicConnectionId connectionId = harness.connection.connectionIds().getFirst();
            assertThat(endpoint.findQuicConnectionFor(harness.connection.peerAddress(),
                                                      connectionId.asReadOnlyBuffer(),
                                                      false),
                       sameInstance(harness.connection));
            harness.connection.receiveConnectionClose(
                    ConnectionCloseFrame.create(QuicTransportErrors.NO_ERROR.code(),
                                                0,
                                                "test peer close"));

            assertThat(endpoint.findQuicConnectionFor(harness.connection.peerAddress(),
                                                      connectionId.asReadOnlyBuffer(),
                                                      false),
                       not(sameInstance(harness.connection)));
        }
    }

    @Test
    void peerCloseReasonIsOnlyRetainedAsPeerVisibleData() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            ConnectionCloseFrame frame = ConnectionCloseFrame.create(QuicTransportErrors.NO_ERROR.code(),
                                                                      0,
                                                                      "secret reason");
            harness.connection.receiveConnectionClose(frame);

            QuicTermination termination = harness.connection.termination().orElseThrow();
            assertThat(termination.origin(), is(QuicTermination.Origin.PEER));
            assertThat(termination.layer(), is(QuicTermination.Layer.TRANSPORT));
            assertThat(termination.keySpace().orElseThrow(), is(KeySpace.ONE_RTT));
            assertThat(termination.logMessage(), not(containsString("secret reason")));
            assertThat(termination.closeCause().getMessage(), not(containsString("secret reason")));
            assertThat(termination.peerReason().orElseThrow(), is("secret reason"));
            assertThat(termination.outgoingDetail(), is(Optional.empty()));
        }
    }

    @Test
    void shouldPreserveLocalTransportFailureContextWithoutDisclosingItsMessage() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            QuicTransportException failure = new QuicTransportException("local secret",
                                                                          KeySpace.ONE_RTT,
                                                                          QuicFrame.STREAM,
                                                                          QuicTransportErrors.FINAL_SIZE_ERROR,
                                                                          17);

            harness.connection.terminate(QuicCloseCommand.transport(failure));

            QuicTermination termination = harness.connection.whenTerminated().toCompletableFuture().join();
            assertThat(termination.origin(), is(QuicTermination.Origin.LOCAL));
            assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
            assertThat(termination.layer(), is(QuicTermination.Layer.TRANSPORT));
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.FINAL_SIZE_ERROR.code()));
            assertThat(termination.frameType().orElseThrow(), is((long) QuicFrame.STREAM));
            assertThat(termination.keySpace().orElseThrow(), is(KeySpace.ONE_RTT));
            assertThat(termination.streamId().orElseThrow(), is(17L));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            assertThat(termination.closeCause().getCause(), sameInstance(failure));
            assertThat(termination.outgoingDetail(), is(Optional.empty()));
            assertThat(termination.peerReason(), is(Optional.empty()));
        }
    }

    @Test
    void shouldPreserveExplicitOutgoingDetailSeparatelyFromPeerReason() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();

            harness.connection.terminate(QuicCloseCommand.application(0x100).withPeerDetail("safe detail"));

            QuicTermination termination = harness.connection.whenTerminated().toCompletableFuture().join();
            assertThat(termination.origin(), is(QuicTermination.Origin.LOCAL));
            assertThat(termination.outgoingDetail().orElseThrow(), is("safe detail"));
            assertThat(termination.peerReason(), is(Optional.empty()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldTerminateWhenScheduledPacketExceedsAeadLimit(boolean retransmission) throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            PacketSpaceManager packetSpace =
                    (PacketSpaceManager) harness.connection.packetSpace(PacketNumberSpace.APPLICATION);
            if (retransmission) {
                QuicPacket packet = harness.connection.encoder()
                        .newOneRttPacket(harness.connection.peerConnectionId(),
                                         packetSpace.allocateNextPN(),
                                         -1,
                                         List.of(PingFrame.create()),
                                         harness.connection.codingContext(),
                                         "test");
                packetSpace.packetSent(packet, -1L, packet.packetNumber());
            }
            QuicTransportException failure = new QuicTransportException("test AEAD limit",
                                                                         KeySpace.ONE_RTT,
                                                                         0,
                                                                         QuicTransportErrors.AEAD_LIMIT_REACHED);
            harness.engine.encryptFailure = failure;

            if (retransmission) {
                packetSpace.fastRetransmit();
            } else {
                packetSpace.requestSendPing();
            }

            QuicTermination termination = harness.connection.whenTerminated().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.AEAD_LIMIT_REACHED.code()));
            assertThat(termination.keySpace().orElseThrow(), is(KeySpace.ONE_RTT));
            assertThat(termination.cause().orElseThrow(), sameInstance(failure));
            assertThat(harness.connection.isOpen(), is(false));
            harness.connection.cleanupComplete().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldObserveStatelessResetAsPeerTerminationWithoutCloseCode() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT));
             TestLogCapture logs = new TestLogCapture(ConnectionTerminatorImpl.class, QuicConnectionImpl.class)) {
            harness.connection.processStatelessReset();

            QuicTermination termination = harness.connection.whenTerminated().toCompletableFuture().join();
            assertThat(termination.origin(), is(QuicTermination.Origin.PEER));
            assertThat(termination.kind(), is(QuicTermination.Kind.STATELESS_RESET));
            assertThat(termination.layer(), is(QuicTermination.Layer.TRANSPORT));
            assertThat(termination.errorCode().isEmpty(), is(true));
            assertThat(termination.frameType().isEmpty(), is(true));
            assertThat(termination.keySpace(), is(Optional.empty()));
            assertThat(termination.peerReason(), is(Optional.empty()));
            List<LogRecord> records = logs.records();
            assertThat(records.stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
            assertThat(records.stream()
                               .filter(record -> record.getLevel() == Level.FINE)
                               .filter(record -> record.getMessage().contains("stateless reset from peer"))
                               .count(),
                       is(1L));
        }
    }

    @Test
    void shouldExposeStableSocketContextIdentityAndPeers() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            SocketContext context = harness.connection;
            InetSocketAddress remoteAddress = harness.connection.peerAddress();
            InetSocketAddress localAddress = (InetSocketAddress) harness.connection.localAddress();

            assertThat(context.socketId(), is("test"));
            assertThat(context.childSocketId(), is("1"));
            assertThat(harness.connection.logTag(), is("test 1"));
            assertThat(harness.connection.endpoint().connectionLogTag(harness.connection), is("test 1"));
            assertThat(context.isSecure(), is(true));

            assertThat(context.remotePeer().address(), is(remoteAddress));
            assertThat(context.remotePeer().host(), is(remoteAddress.getHostString()));
            assertThat(context.remotePeer().port(), is(remoteAddress.getPort()));
            assertThat(context.remotePeer().tlsPrincipal(), is(Optional.empty()));
            assertThat(context.remotePeer().tlsCertificates(), is(Optional.empty()));

            assertThat(context.localPeer().address(), is(localAddress));
            assertThat(context.localPeer().host(), is(localAddress.getHostString()));
            assertThat(context.localPeer().port(), is(localAddress.getPort()));
            assertThat(context.localPeer().tlsPrincipal(), is(Optional.empty()));
            assertThat(context.localPeer().tlsCertificates(), is(Optional.empty()));
        }
    }

    @Test
    void shouldApplyInitialRemoteStreamDataBeforePublishingStream() throws Exception {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxBidiStreams(4)
                .maxUniStreams(4)
                .buildPrototype();
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT), config)) {
            byte[] payload = {1, 2, 3, 4};
            AtomicReference<byte[]> observedPayload = new AtomicReference<>();
            AtomicInteger listenerCalls = new AtomicInteger();
            harness.connection.addRemoteStreamListener(stream -> {
                listenerCalls.incrementAndGet();
                QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
                }));
                reader.start();
                observedPayload.set(reader.poll().orElseThrow().readBytes());
                return true;
            });

            harness.connection.incoming1RTTFrame(
                    StreamFrame.create(0, 0, payload.length, false, ByteBuffer.wrap(payload)));

            assertThat(listenerCalls.get(), is(1));
            assertThat(observedPayload.get(), is(payload));
        }
    }

    @Test
    void shouldQueueInitialRemoteStreamDataForLateListener() throws Exception {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxBidiStreams(4)
                .maxUniStreams(4)
                .buildPrototype();
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT), config)) {
            byte[] payload = {1, 2, 3, 4};
            harness.connection.incoming1RTTFrame(
                    StreamFrame.create(0, 0, payload.length, false, ByteBuffer.wrap(payload)));

            AtomicReference<byte[]> observedPayload = new AtomicReference<>();
            AtomicInteger listenerCalls = new AtomicInteger();
            harness.connection.addRemoteStreamListener(stream -> {
                listenerCalls.incrementAndGet();
                QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
                }));
                reader.start();
                observedPayload.set(reader.poll().orElseThrow().readBytes());
                return true;
            });

            assertThat(listenerCalls.get(), is(1));
            assertThat(observedPayload.get(), is(payload));
        }
    }

    @Test
    void shouldApplyTargetDataBeforePublishingImpliedRemoteStreams() throws Exception {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxBidiStreams(4)
                .maxUniStreams(4)
                .buildPrototype();
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT), config)) {
            byte[] payload = {5, 6, 7, 8};
            List<Long> publishedStreams = new ArrayList<>();
            List<Long> emptyStreams = new ArrayList<>();
            AtomicReference<byte[]> observedPayload = new AtomicReference<>();
            harness.connection.addRemoteStreamListener(stream -> {
                publishedStreams.add(stream.streamId());
                QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
                }));
                reader.start();
                Optional<BufferData> data = reader.poll();
                if (stream.streamId() == 8) {
                    assertThat(stream.dataReceived(), is((long) payload.length));
                    assertThat(stream.receivingState(), is(QuicReceiverStream.ReceivingStreamState.DATA_RECVD));
                    observedPayload.set(data.orElseThrow().readBytes());
                    assertThat(reader.poll().orElseThrow(), sameInstance(QuicStreamReader.EOF));
                } else if (data.isEmpty()) {
                    emptyStreams.add(stream.streamId());
                }
                return true;
            });

            harness.connection.incoming1RTTFrame(
                    StreamFrame.create(8, 0, payload.length, true, ByteBuffer.wrap(payload)));

            assertThat(publishedStreams, is(List.of(0L, 4L, 8L)));
            assertThat(emptyStreams, is(List.of(0L, 4L)));
            assertThat(observedPayload.get(), is(payload));
        }
    }

    @Test
    void shouldNotPublishRemoteStreamBeforeValidatingInitialFrame() throws Exception {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .initialMaxStreamData(1)
                .maxBidiStreams(4)
                .maxUniStreams(4)
                .buildPrototype();
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT), config)) {
            AtomicInteger listenerCalls = new AtomicInteger();
            harness.connection.addRemoteStreamListener(stream -> {
                listenerCalls.incrementAndGet();
                return true;
            });
            StreamFrame invalid = StreamFrame.create(8, 0, 2, false, ByteBuffer.wrap(new byte[] {1, 2}));

            QuicTransportException failure = assertThrows(
                    QuicTransportException.class,
                    () -> harness.connection.incoming1RTTFrame(invalid));

            assertThat(failure.errorCode(), is(QuicTransportErrors.FLOW_CONTROL_ERROR.code()));
            assertThat(listenerCalls.get(), is(0));

            AtomicInteger lateListenerCalls = new AtomicInteger();
            harness.connection.addRemoteStreamListener(stream -> {
                lateListenerCalls.incrementAndGet();
                return true;
            });
            assertThat(lateListenerCalls.get(), is(0));
        }
    }

    @Test
    void shouldOwnRemoteStreamRegistrationsIndependently() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            AtomicInteger calls = new AtomicInteger();
            Predicate<QuicReceiverStream> listener = stream -> {
                calls.incrementAndGet();
                return true;
            };

            QuicRemoteStreamRegistration first = harness.connection.addRemoteStreamListener(listener);
            QuicRemoteStreamRegistration second = harness.connection.addRemoteStreamListener(listener);

            first.close();
            harness.connection.receiveRemoteStream(3);
            assertThat(calls.get(), is(1));

            second.close();
            harness.connection.receiveRemoteStream(7);
            assertThat(calls.get(), is(1));
        }
    }

    @Test
    void shouldRollBackRemoteStreamRegistrationWhenInitialDeliveryFails() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            AtomicInteger calls = new AtomicInteger();
            harness.connection.receiveRemoteStream(3);

            assertThrows(IllegalStateException.class,
                         () -> harness.connection.addRemoteStreamListener(stream -> {
                             calls.incrementAndGet();
                             throw new IllegalStateException("listener failed");
                         }));

            harness.connection.receiveRemoteStream(7);
            assertThat(calls.get(), is(1));
        }
    }

    @Test
    void shouldReplenishRemoteStreamCreditAcrossOneRttPackets() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            for (int ordinal = 0; ordinal < 8; ordinal++) {
                if ((ordinal & 1) == 0) {
                    harness.connection.applicationSendStarted = new CountDownLatch(1);
                    harness.connection.continueApplicationSend = new CountDownLatch(0);
                }
                long streamId = 3 + 4L * ordinal;
                harness.connection.receiveFinishedRemoteStream(streamId);
                assertThat(harness.connection.streams().findStream(streamId), is(Optional.empty()));
                if ((ordinal & 1) == 1) {
                    assertThat(harness.connection.applicationSendStarted.await(10, TimeUnit.SECONDS), is(true));
                    harness.connection.applicationSendStarted = null;
                    harness.connection.continueApplicationSend = null;
                    long advertisedCount = harness.connection.applicationPackets().stream()
                            .flatMap(packet -> packet.frames().stream())
                            .filter(MaxStreamsFrame.class::isInstance)
                            .count();
                    assertThat(advertisedCount, is((long) (ordinal + 1) / 2));
                }
            }

            List<Long> advertisedLimits = harness.connection.applicationPackets().stream()
                    .flatMap(packet -> packet.frames().stream())
                    .filter(MaxStreamsFrame.class::isInstance)
                    .map(MaxStreamsFrame.class::cast)
                    .filter(frame -> !frame.isBidi())
                    .map(MaxStreamsFrame::maxStreams)
                    .toList();
            assertThat(advertisedLimits, is(List.of(6L, 8L, 10L, 12L)));
        }
    }

    @Test
    void shouldReplenishServerRemoteBidiStreamCreditAcrossOneRttPackets() throws Exception {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxBidiStreams(4)
                .maxUniStreams(4)
                .buildPrototype();
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT), config)) {
            harness.connection.pathManager().addressValidated(harness.connection.peerAddress());
            for (int ordinal = 0; ordinal < 8; ordinal++) {
                long streamId = 4L * ordinal;
                harness.connection.receiveFinishedRemoteBidiStream(streamId);
                assertThat(harness.connection.streams().findStream(streamId), is(Optional.empty()));
            }

            List<Long> advertisedLimits = harness.connection.applicationPackets().stream()
                    .flatMap(packet -> packet.frames().stream())
                    .filter(MaxStreamsFrame.class::isInstance)
                    .map(MaxStreamsFrame.class::cast)
                    .filter(MaxStreamsFrame::isBidi)
                    .map(MaxStreamsFrame::maxStreams)
                    .toList();
            assertThat(advertisedLimits, is(List.of(6L, 8L, 10L, 12L)));
        }
    }

    @Test
    void shouldRejectNullRemoteStreamListener() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            NullPointerException exception = assertThrows(NullPointerException.class,
                                                          () -> harness.connection.addRemoteStreamListener(null));

            assertThat(exception.getMessage(), is("streamConsumer"));
        }
    }

    @Test
    void shouldLimitEachReassemblyBudgetTo1024Nodes() {
        QuicConnectionImpl.ReassemblyBudgetOwner owner = new QuicConnectionImpl.ReassemblyBudgetOwner();
        QuicConnectionImpl.ReassemblyBudget budget = owner.newBudget();

        acquireReassemblyPermits(budget, 1024);

        assertThat(budget.tryAcquire(), is(false));

        budget.release(1024);
        assertThat(budget.tryAcquire(), is(true));
        budget.release();
    }

    @Test
    void shouldLimitConnectionReassemblyBudgetTo4096Nodes() {
        QuicConnectionImpl.ReassemblyBudgetOwner owner = new QuicConnectionImpl.ReassemblyBudgetOwner();
        QuicConnectionImpl.ReassemblyBudget first = owner.newBudget();
        QuicConnectionImpl.ReassemblyBudget second = owner.newBudget();
        QuicConnectionImpl.ReassemblyBudget third = owner.newBudget();
        QuicConnectionImpl.ReassemblyBudget fourth = owner.newBudget();
        QuicConnectionImpl.ReassemblyBudget blocked = owner.newBudget();

        acquireReassemblyPermits(first, 1024);
        acquireReassemblyPermits(second, 1024);
        acquireReassemblyPermits(third, 1024);
        acquireReassemblyPermits(fourth, 1024);

        assertThat(blocked.tryAcquire(), is(false));

        first.release();
        assertThat(blocked.tryAcquire(), is(true));

        first.release(1023);
        second.release(1024);
        third.release(1024);
        fourth.release(1024);
        blocked.release();
    }

    @Test
    void shouldRejectExcessiveOneRttCryptoFragmentation() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            for (int i = 1; i <= 1024; i++) {
                harness.connection.receiveOneRttCrypto(
                        CryptoFrame.create(i * 2L, 1, ByteBuffer.allocate(1)));
            }

            QuicTransportException exception = assertThrows(
                    QuicTransportException.class,
                    () -> harness.connection.receiveOneRttCrypto(
                            CryptoFrame.create(2050L, 1, ByteBuffer.allocate(1))));

            assertThat(exception.errorCode(), is(QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED.code()));
            assertThat(exception.keySpace().orElseThrow(), is(KeySpace.ONE_RTT));
        }
    }

    @Test
    void shouldReleaseCryptoReassemblyNodesWhenKeySpaceCloses() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL))) {
            harness.connection.receiveInitialCrypto(CryptoFrame.create(2L, 1, ByteBuffer.allocate(1)));
            QuicConnectionImpl.ReassemblyBudget first = harness.connection.newReassemblyBudget();
            QuicConnectionImpl.ReassemblyBudget second = harness.connection.newReassemblyBudget();
            QuicConnectionImpl.ReassemblyBudget third = harness.connection.newReassemblyBudget();
            QuicConnectionImpl.ReassemblyBudget fourth = harness.connection.newReassemblyBudget();
            acquireReassemblyPermits(first, 1024);
            acquireReassemblyPermits(second, 1024);
            acquireReassemblyPermits(third, 1024);
            acquireReassemblyPermits(fourth, 1023);
            assertThat(fourth.tryAcquire(), is(false));

            harness.connection.packetSpace(PacketNumberSpace.INITIAL).close();

            assertThat(fourth.tryAcquire(), is(true));
            assertThat(harness.connection.receiveInitialCrypto(
                    CryptoFrame.create(0L, 1, ByteBuffer.allocate(1))), is(0));
            assertThat(harness.engine().consumedCryptoBytes.get(), is(0));

            first.release(1024);
            second.release(1024);
            third.release(1024);
            fourth.release(1024);
        }
    }

    @Test
    void shouldRejectOversizedOneRttCryptoBuffer() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            CryptoFrame cryptoFrame = CryptoFrame.create(0L,
                                                         MAX_INCOMING_CRYPTO_CAPACITY + 1,
                                                         ByteBuffer.allocate(MAX_INCOMING_CRYPTO_CAPACITY + 1));

            QuicTransportException exception = assertThrows(QuicTransportException.class,
                                                            () -> harness.connection.receiveOneRttCrypto(cryptoFrame));

            assertThat(exception.errorCode(), is(QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED.code()));
        }
    }

    @Test
    void shouldDropOneRttCryptoAfterSilentTermination() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.connection.receiveOneRttCrypto(CryptoFrame.create(2L, 1, ByteBuffer.allocate(1)));

            harness.connection.terminate(QuicCloseCommand.silent("test crypto cleanup"));

            assertThat(harness.connection.packetSpace(PacketNumberSpace.APPLICATION).isClosed(), is(true));
            harness.connection.receiveOneRttCrypto(CryptoFrame.create(0L, 1, ByteBuffer.allocate(1)));
            assertThat(harness.engine().consumedCryptoBytes.get(), is(0));
        }
    }

    @Test
    void shouldDiscardInitialSpaceWhenHandshakeDoneArrivesBeforeFirstHandshakePacketIsSent() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.INITIAL,
                                                                             KeySpace.HANDSHAKE,
                                                                             KeySpace.ONE_RTT))) {
            harness.engine().setHandshakeState(HandshakeState.NEED_RECV_HANDSHAKE_DONE);

            assertThat(harness.connection.packetSpace(PacketNumberSpace.INITIAL).isClosed(), is(false));
            assertThat(harness.connection.packetSpace(PacketNumberSpace.HANDSHAKE).isClosed(), is(false));
            assertThat(harness.engine().keysAvailable(KeySpace.INITIAL), is(true));
            assertThat(harness.engine().keysAvailable(KeySpace.HANDSHAKE), is(true));

            harness.connection.receiveHandshakeDone();

            assertThat(harness.connection.packetSpace(PacketNumberSpace.INITIAL).isClosed(), is(true));
            assertThat(harness.connection.packetSpace(PacketNumberSpace.HANDSHAKE).isClosed(), is(true));
            assertThat(harness.engine().keysAvailable(KeySpace.INITIAL), is(false));
            assertThat(harness.engine().keysAvailable(KeySpace.HANDSHAKE), is(false));
            assertThat(harness.engine().handshakeState(), is(HandshakeState.HANDSHAKE_CONFIRMED));
        }
    }

    @Test
    void shouldDiscardInitialSpaceWhenServerProcessesFirstHandshakePacket() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.INITIAL,
                                                                                   KeySpace.HANDSHAKE,
                                                                                   KeySpace.ONE_RTT))) {
            PingFrame ping = PingFrame.create();
            HandshakePacket packet = mock(HandshakePacket.class);
            when(packet.packetType()).thenReturn(PacketType.HANDSHAKE);
            when(packet.packetNumber()).thenReturn(0L);
            when(packet.frames()).thenReturn(List.of(ping));
            when(packet.payloadSize()).thenReturn(ping.size());

            assertThat(harness.connection.packetSpace(PacketNumberSpace.INITIAL).isClosed(), is(false));
            assertThat(harness.connection.packetSpace(PacketNumberSpace.HANDSHAKE).isClosed(), is(false));
            assertThat(harness.engine().keysAvailable(KeySpace.INITIAL), is(true));
            assertThat(harness.engine().keysAvailable(KeySpace.HANDSHAKE), is(true));

            harness.connection().processHandshakePacket(packet);

            assertThat(harness.connection.packetSpace(PacketNumberSpace.INITIAL).isClosed(), is(true));
            assertThat(harness.connection.packetSpace(PacketNumberSpace.HANDSHAKE).isClosed(), is(false));
            assertThat(harness.engine().keysAvailable(KeySpace.INITIAL), is(false));
            assertThat(harness.engine().keysAvailable(KeySpace.HANDSHAKE), is(true));
        }
    }

    @Test
    void shouldRejectHandshakeDoneReceivedByServerAsPeerProtocolViolation() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.INITIAL,
                                                                                   KeySpace.HANDSHAKE,
                                                                                   KeySpace.ONE_RTT));
             TestLogCapture logs = new TestLogCapture(QuicConnectionImpl.class, ConnectionTerminatorImpl.class)) {
            HandshakeDoneFrame frame = HandshakeDoneFrame.create();
            QuicPacket packet = mock(QuicPacket.class);
            when(packet.packetType()).thenReturn(PacketType.ONERTT);
            when(packet.frames()).thenReturn(List.of(frame));
            harness.engine().setHandshakeState(HandshakeState.NEED_RECV_HANDSHAKE_DONE);

            harness.connection().processOneRTTPacket(packet);

            QuicTermination termination = harness.connection().termination().orElseThrow();
            Throwable cause = termination.cause().orElseThrow();
            assertThat(cause instanceof QuicTransportException, is(true));
            QuicTransportException failure = (QuicTransportException) cause;
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
            assertThat(failure.errorCode(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
            assertThat(failure.keySpace().orElseThrow(), is(KeySpace.ONE_RTT));
            assertThat(failure.frameType(), is(frame.typeField()));
            assertThat(harness.engine().handshakeState(), is(HandshakeState.NEED_RECV_HANDSHAKE_DONE));
            assertThat(harness.connection().isOpen(), is(false));
            assertThat(harness.connection().closeIncomingAttempts.get(), is(1));
            assertThat(harness.connection().streamTerminationAttempts.get(), is(1));
            List<LogRecord> records = logs.records();
            assertThat(records.stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
            assertThat(records.stream()
                               .filter(record -> record.getLevel() == Level.FINE)
                               .filter(record -> record.getMessage().contains(
                                       "Closing connection after peer protocol violation"))
                               .count(),
                       is(1L));
        }
    }

    @Test
    void shouldAcceptMaximumStreamsBoundaryForBothStreamDirections() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            long maximum = QuicConnectionImpl.MAX_STREAMS_VALUE_LIMIT;
            for (boolean bidi : List.of(true, false)) {
                harness.connection().incoming1RTTFrame(MaxStreamsFrame.create(bidi, maximum - 1));
                harness.connection().incoming1RTTFrame(MaxStreamsFrame.create(bidi, maximum));

                MaxStreamsFrame invalid = MaxStreamsFrame.create(bidi, maximum + 1);
                QuicTransportException failure =
                        assertThrows(QuicTransportException.class,
                                     () -> harness.connection().incoming1RTTFrame(invalid));
                assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
                assertThat(failure.keySpace().orElseThrow(), is(KeySpace.ONE_RTT));
                assertThat(failure.frameType(), is(invalid.typeField()));
            }
        }
    }

    @Test
    void shouldRejectAckForNonexistentApplicationPacketNumber() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();

            harness.connection.enqueue1RTTFrame(PingFrame.create());
            harness.connection.runAppPacketSpaceTransmitter();

            PacketSpaceManager packetSpace = (PacketSpaceManager) harness.connection.packetSpace(PacketNumberSpace.APPLICATION);
            AckFrame frame = AckFrame.create(1L, 0L, List.of(AckRange.of(0L, 0L)));

            QuicTransportException exception = assertThrows(QuicTransportException.class,
                                                            () -> packetSpace.processAckFrame(frame));

            assertThat(exception.errorCode(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
        }
    }

    @Test
    void shouldNotRetransmitPathValidationFramesWhenDeclaredLost() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            QuicPathManager.ReceiveContext receiveContext =
                    harness.connection.pathManager().receive(harness.connection.peerAddress(), 0);
            harness.connection.pathManager()
                    .pathChallenge(receiveContext, ByteBuffer.allocate(Long.BYTES).putLong(1L).flip());
            harness.connection.runAppPacketSpaceTransmitter();

            PacketSpaceManager packetSpace =
                    (PacketSpaceManager) harness.connection.packetSpace(PacketNumberSpace.APPLICATION);

            for (int i = 0; i < 4; i++) {
                harness.connection.enqueue1RTTFrame(PingFrame.create());
                harness.connection.runAppPacketSpaceTransmitter();
            }
            List<QuicPacket> packetsBeforeAck = List.copyOf(harness.connection.applicationPackets());
            assertThat(packetsBeforeAck.getFirst().frames().stream()
                               .anyMatch(PathResponseFrame.class::isInstance),
                       is(true));

            packetSpace.processAckFrame(AckFrame.create(4L, 0L, List.of(AckRange.of(0L, 0L))));

            assertThat(harness.connection.applicationPackets(), is(packetsBeforeAck));
        }
    }

    @Test
    void sendsHandshakeWithinPartialAntiAmplificationBudget() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.HANDSHAKE))) {
            harness.connection.pathManager().receive(harness.connection.peerAddress(), 100);
            harness.engine.queueHandshakeFlight(KeySpace.HANDSHAKE, ByteBuffer.allocate(64));

            PacketSpaceManager handshakeSpace =
                    (PacketSpaceManager) harness.connection.packetSpace(PacketNumberSpace.HANDSHAKE);
            harness.connection.continueHandshake();

            assertThat(handshakeSpace.nextPacketNumber().get(), is(1L));
            assertThat(harness.engine().handshakeState(), is(HandshakeState.NEED_RECV_CRYPTO));
        }
    }

    @Test
    void defersInitialRetransmissionUntilFullAntiAmplificationBudgetIsAvailable() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.INITIAL))) {
            QuicPathManager pathManager = harness.connection.pathManager();
            PacketSpaceManager initialSpace =
                    (PacketSpaceManager) harness.connection.packetSpace(PacketNumberSpace.INITIAL);
            byte[] cryptoBytes = {1, 2, 3, 4};
            pathManager.receive(harness.connection.peerAddress(), 400);
            harness.engine.queueHandshakeFlight(KeySpace.INITIAL, ByteBuffer.wrap(cryptoBytes));
            harness.connection.continueHandshake();

            assertThat(initialSpace.nextPacketNumber().get(), is(1L));
            assertThat(harness.engine.encryptedCryptoFrames.size(), is(1));
            assertThat(pathManager.reserve(1), is(Optional.empty()));

            // A previous flight can leave less credit than the required 1200-byte Initial datagram.
            pathManager.receive(harness.connection.peerAddress(), 184);
            QuicPacket original = harness.connection.encoder()
                    .newInitialPacket(harness.connection.localConnectionId().orElseThrow(),
                                      harness.connection.peerConnectionId(),
                                      new byte[0],
                                      0,
                                      -1,
                                      List.of(CryptoFrame.create(0, cryptoBytes.length, ByteBuffer.wrap(cryptoBytes))),
                                      harness.connection.codingContext());

            assertThat(harness.connection.emitter().retransmit(initialSpace, original, 0), is(false));
            assertThat(initialSpace.nextPacketNumber().get(), is(1L));
            assertThat(harness.engine.encryptedCryptoFrames.size(), is(1));
            initialSpace.fastRetransmit();
            assertThat(harness.engine.encryptedCryptoFrames.size(), is(1));
            QuicPathManager.SendPermit remaining = pathManager.reserve(1200).orElseThrow();
            assertThat(remaining.size(), is(552));
            remaining.release();

            // Receiving another datagram wakes the packet spaces; their retry timer retains the queued CRYPTO.
            ByteBuffer incoming = harness.connection.encodeIncomingInitial(0, List.of(PingFrame.create()));
            harness.connection.processIncoming(harness.connection.peerAddress(),
                                                harness.connection.localConnectionId().orElseThrow().asReadOnlyBuffer(),
                                                QuicPacket.HeadersType.LONG,
                                                incoming);
            try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
                CompletableFuture<Void> retransmission = new CompletableFuture<>();
                scheduler.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Deadline next = harness.connection.endpoint().timer()
                                    .processEventsAndReturnNextDeadline(TimeSource.now(), Runnable::run);
                            if (harness.engine.encryptedCryptoFrames.size() >= 2) {
                                retransmission.complete(null);
                            } else if (next.equals(Deadline.MAX)) {
                                retransmission.completeExceptionally(
                                        new IllegalStateException("No retransmission scheduled"));
                            } else {
                                long delay = Math.max(0, Deadline.between(TimeSource.now(), next).toNanos());
                                scheduler.schedule(this, delay, TimeUnit.NANOSECONDS);
                            }
                        } catch (Throwable failure) {
                            retransmission.completeExceptionally(failure);
                        }
                    }
                });
                try {
                    retransmission.get(10, TimeUnit.SECONDS);
                } finally {
                    scheduler.shutdownNow();
                }
            }

            assertThat(harness.engine.encryptedCryptoFrames.size(), is(2));
            CryptoFrame retransmitted = harness.engine.encryptedCryptoFrames.getLast();
            assertThat(retransmitted.offset(), is(0L));
            assertThat(retransmitted.payload(), is(ByteBuffer.wrap(cryptoBytes)));
            assertThat(harness.connection.isOpen(), is(true));
        }
    }

    @Test
    void pathControlFlightsAreCongestionBoundedAndNotRetransmitted() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT))) {
            QuicPathManager pathManager = harness.connection.pathManager();
            harness.connection.receiveNewConnectionId(
                    NewConnectionIDFrame.create(1,
                                                0,
                                                ByteBuffer.wrap(new byte[] {0x55}),
                                                ByteBuffer.wrap(new byte[16])));
            InetSocketAddress candidate = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4444);
            int attempts = 0;
            for (; attempts < 2048; attempts++) {
                int sentBefore = harness.connection.applicationPackets().size();
                QuicPathManager.ReceiveContext context = pathManager.receive(candidate, 1200);
                pathManager.authenticated(context, attempts + 1, false, 100);
                pathManager.pathChallenge(context, ByteBuffer.allocate(Long.BYTES).putLong(attempts).flip());
                harness.connection.runAppPacketSpaceTransmitter();
                if (harness.connection.applicationPackets().size() == sentBefore
                        && (harness.connection.congestionController().isCwndLimited()
                                || harness.connection.congestionController().isPacerLimited())) {
                    break;
                }
            }

            PacketSpaceManager packetSpace =
                    (PacketSpaceManager) harness.connection.packetSpace(PacketNumberSpace.APPLICATION);
            List<QuicPacket> flights = harness.connection.pathControlPackets();
            assertThat(attempts < 2048, is(true));
            assertThat(flights.isEmpty(), is(false));
            assertThat(harness.connection.congestionController().isCwndLimited()
                               || harness.connection.congestionController().isPacerLimited(),
                       is(true));

            long largestPathControlPacket = flights.getLast().packetNumber();
            packetSpace.processAckFrame(AckFrame.create(largestPathControlPacket,
                                                        0L,
                                                        List.of(AckRange.of(0L, 0L))));

            List<Long> responseData = harness.connection.pathControlPackets().stream()
                    .flatMap(packet -> packet.frames().stream())
                    .filter(PathResponseFrame.class::isInstance)
                    .map(PathResponseFrame.class::cast)
                    .map(frame -> frame.data().getLong(frame.data().position()))
                    .toList();
            assertThat(Set.copyOf(responseData).size(), is(responseData.size()));
            assertThat(harness.connection.congestionController().isCwndLimited(), is(false));
        }
    }

    @Test
    void pathChangeDropsStalePathControlFlightsBeforeLateAck() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            QuicPathManager.ReceiveContext context =
                    harness.connection.pathManager().receive(harness.connection.peerAddress(), 0);
            harness.connection.pathManager()
                    .pathChallenge(context, ByteBuffer.allocate(Long.BYTES).putLong(1L).flip());
            harness.connection.runAppPacketSpaceTransmitter();

            PacketSpaceManager packetSpace =
                    (PacketSpaceManager) harness.connection.packetSpace(PacketNumberSpace.APPLICATION);
            long packetNumber = harness.connection.pathControlPackets().getFirst().packetNumber();
            PacketSpaceManager.PathRecoveryState recoveryState = harness.connection.pathRecoveryState();
            long nextGeneration = recoveryState.generation() + 1;

            recoveryState.transition(nextGeneration,
                                     () -> harness.connection.congestionController()
                                             .resetForPath(harness.connection.maxDatagramSize()));
            packetSpace.discardObsoletePathControlFlights(nextGeneration);
            long congestionWindow = harness.connection.congestionController().congestionWindow();

            packetSpace.processAckFrame(AckFrame.create(packetNumber,
                                                        0L,
                                                        List.of(AckRange.of(0L, 0L))));
            assertThat(harness.connection.congestionController().congestionWindow(), is(congestionWindow));
            assertThat(harness.connection.congestionController().isCwndLimited(), is(false));
        }
    }

    @Test
    void failedCandidateValidationReleasesPathControlCapacity() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT))) {
            QuicPathManager pathManager = harness.connection.pathManager();
            harness.connection.receiveNewConnectionId(
                    NewConnectionIDFrame.create(1,
                                                0,
                                                ByteBuffer.wrap(new byte[] {0x55}),
                                                ByteBuffer.wrap(new byte[16])));
            InetSocketAddress candidate = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4444);
            int attempts = 0;
            for (; attempts < 2048; attempts++) {
                int sentBefore = harness.connection.applicationPackets().size();
                QuicPathManager.ReceiveContext context = pathManager.receive(candidate, 1200);
                pathManager.authenticated(context, attempts + 1, false, 100);
                pathManager.pathChallenge(context, ByteBuffer.allocate(Long.BYTES).putLong(attempts).flip());
                harness.connection.runAppPacketSpaceTransmitter();
                if (harness.connection.applicationPackets().size() == sentBefore
                        && (harness.connection.congestionController().isCwndLimited()
                                || harness.connection.congestionController().isPacerLimited())) {
                    break;
                }
            }
            assertThat(attempts < 2048, is(true));
            assertThat(harness.connection.pathControlPackets().isEmpty(), is(false));
            assertThat(harness.connection.congestionController().isCwndLimited()
                               || harness.connection.congestionController().isPacerLimited(),
                       is(true));

            pathManager.validationTimedOut(Long.MAX_VALUE);
            harness.connection.discardRetiredPathControlFlights();

            assertThat(harness.connection.congestionController().isCwndLimited(), is(false));
        }
    }

    @Test
    void usesConfiguredActiveConnectionIdLimit() throws Exception {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxUniStreams(4)
                .transportParameters(QuicTransportParametersConfig.builder()
                                             .activeConnectionIdLimit(8)
                                             .build())
                .build();
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT), config)) {
            assertThat(harness.connection.localActiveConnectionIdLimit(), is(8L));
        }
    }

    @Test
    void rejectsDistantConnectionIdSequenceWithoutMaterializingGaps() throws Exception {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxUniStreams(4)
                .transportParameters(QuicTransportParametersConfig.builder()
                                             .activeConnectionIdLimit(
                                                     QuicTransportParametersConfigSupport.MAX_ACTIVE_CONNECTION_ID_LIMIT)
                                             .build())
                .build();
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT), config)) {
            harness.seedPeerConnectionId();
            NewConnectionIDFrame frame = NewConnectionIDFrame.create(QuicFrame.MAX_VL_INTEGER,
                                                                     0,
                                                                     ByteBuffer.wrap(new byte[] {0x55}),
                                                                     ByteBuffer.wrap(new byte[16]));

            QuicTransportException exception = assertThrows(
                    QuicTransportException.class,
                    () -> harness.connection.receiveNewConnectionId(frame));
            assertThat(exception.errorCode(), is(QuicTransportErrors.CONNECTION_ID_LIMIT_ERROR.code()));
        }
    }

    @Test
    void terminationUnschedulesPathValidationTimer() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT))) {
            QuicPathManager pathManager = harness.connection.pathManager();
            pathManager.addressValidated(pathManager.peerAddress());
            harness.connection.receiveNewConnectionId(
                    NewConnectionIDFrame.create(1,
                                                0,
                                                ByteBuffer.wrap(new byte[] {0x55}),
                                                ByteBuffer.wrap(new byte[16])));
            InetSocketAddress candidate = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4444);
            pathManager.authenticated(pathManager.receive(candidate, 1200), 1, false, TimeUnit.SECONDS.toNanos(1));
            harness.connection.runAppPacketSpaceTransmitter();

            QuicTimerQueue timerQueue = harness.connection.endpoint().timer();
            timerQueue.processEventsAndReturnNextDeadline(TimeSource.now(), Runnable::run);
            assertThat(timerQueue.nextDeadline(), not(Deadline.MAX));

            harness.connection.terminate(QuicCloseCommand.silent("test path timer cleanup"));
            timerQueue.processEventsAndReturnNextDeadline(TimeSource.now(), Runnable::run);

            assertThat(timerQueue.nextDeadline(), is(Deadline.MAX));
        }
    }

    @Test
    void shouldNotSpendEntireLimitedPathBudgetOnResponsePadding() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            QuicPathManager.ReceiveContext context =
                    harness.connection.pathManager().receive(harness.connection.peerAddress(), 40);
            harness.connection.pathManager()
                    .pathChallenge(context, ByteBuffer.allocate(Long.BYTES).putLong(1L).flip());
            harness.connection.pathManager()
                    .pathChallenge(context, ByteBuffer.allocate(Long.BYTES).putLong(2L).flip());

            harness.connection.runAppPacketSpaceTransmitter();

            long responses = harness.connection.applicationPackets().stream()
                    .flatMap(packet -> packet.frames().stream())
                    .filter(PathResponseFrame.class::isInstance)
                    .count();
            assertThat(responses, is(2L));
        }
    }

    @Test
    void shouldRejectExcessivePeerConnectionIdGaps() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.create(EnumSet.of(KeySpace.ONE_RTT))) {
            harness.seedPeerConnectionId();
            NewConnectionIDFrame frame = NewConnectionIDFrame.create(7,
                                                                     0,
                                                                     ByteBuffer.wrap(new byte[] {0x55}),
                                                                     ByteBuffer.wrap(new byte[16]));

            QuicTransportException exception = assertThrows(QuicTransportException.class,
                                                            () -> harness.connection.receiveNewConnectionId(frame));

            assertThat(exception.errorCode(), is(QuicTransportErrors.CONNECTION_ID_LIMIT_ERROR.code()));
        }
    }

    @Test
    void preparedConnectionIdTokenIsAddressScopedAndRolledBackIfUnsent() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT))) {
            QuicPathManager pathManager = harness.connection.pathManager();
            byte[] resetToken = new byte[16];
            Arrays.fill(resetToken, (byte) 2);
            harness.connection.receiveNewConnectionId(NewConnectionIDFrame.create(1,
                                                                                   0,
                                                                                   ByteBuffer.wrap(new byte[] {0x55}),
                                                                                   ByteBuffer.wrap(resetToken)));
            InetSocketAddress rebound = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4444);
            QuicPathManager.ReceiveResult migration =
                    pathManager.authenticated(pathManager.receive(rebound, 1200), 1, true, 100);
            pathManager.pathChangeCompleted(migration.generation());

            QuicPathManager.ProbeSend prepared = pathManager.pollSendableProbe(1200).orElseThrow();
            QuicPacketReceiver.PeerResetToken registration = harness.connection.activeResetTokens().stream()
                    .filter(token -> Arrays.equals(token.token(), resetToken))
                    .findFirst()
                    .orElseThrow();

            assertThat(registration.peerAddress(), is(rebound));
            prepared.permit().release();
            assertThat(harness.connection.activeResetTokens().stream()
                               .anyMatch(token -> Arrays.equals(token.token(), resetToken)), is(false));
        }
    }

    @Test
    void pathProbesUseDistinctPeerConnectionIds() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT))) {
            QuicPathManager pathManager = harness.connection.pathManager();
            InetSocketAddress initialPeer = pathManager.peerAddress();
            pathManager.addressValidated(initialPeer);
            harness.connection.receiveNewConnectionId(
                    NewConnectionIDFrame.create(1,
                                                0,
                                                ByteBuffer.wrap(new byte[] {0x55}),
                                                ByteBuffer.wrap(new byte[16])));
            InetSocketAddress rebound = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4444);
            QuicPathManager.ReceiveResult migration =
                    pathManager.authenticated(pathManager.receive(rebound, 1200), 1, true, 100);
            pathManager.pathChangeCompleted(migration.generation());

            QuicPathManager.ProbeSend newPathProbe = pathManager.pollSendableProbe(1200).orElseThrow();
            QuicPathManager.ProbeSend previousPathProbe = pathManager.pollSendableProbe(1200).orElseThrow();

            assertThat(newPathProbe.probe().destination(), is(rebound));
            assertThat(newPathProbe.binding().sequence(), is(1L));
            assertThat(previousPathProbe.probe().destination(), is(initialPeer));
            assertThat(previousPathProbe.binding().sequence(), is(0L));
            newPathProbe.permit().release();
            previousPathProbe.permit().release();
        }
    }

    @Test
    void cidBlockedPathDoesNotHideSendablePreviousPathProbe() throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(EnumSet.of(KeySpace.ONE_RTT))) {
            QuicPathManager pathManager = harness.connection.pathManager();
            InetSocketAddress initialPeer = pathManager.peerAddress();
            pathManager.addressValidated(initialPeer);
            InetSocketAddress rebound = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4444);
            QuicPathManager.ReceiveResult migration =
                    pathManager.authenticated(pathManager.receive(rebound, 1200), 1, true, 100);
            pathManager.pathChangeCompleted(migration.generation());

            QuicPathManager.ProbeSend sendable = pathManager.pollSendableProbe(1200).orElseThrow();

            assertThat(sendable.probe().destination(), is(initialPeer));
            assertThat(sendable.binding().sequence(), is(0L));
            sendable.permit().release();
        }
    }

    private static QuicTransportParameters peerTransportParameters(TestQuicConnection connection) {
        QuicTransportParameters parameters = QuicTransportParameters.create();
        parameters.parameter(ParameterId.initial_source_connection_id, PEER_CONNECTION_ID);
        if (connection.isClientConnection()) {
            parameters.parameter(ParameterId.original_destination_connection_id,
                                 connection.originalServerConnId().bufferData().readBytes());
        }
        return parameters;
    }

    private static ByteBuffer encodeParameters(QuicTransportParameters parameters) {
        ByteBuffer buffer = ByteBuffer.allocate(parameters.size());
        parameters.encode(buffer);
        return buffer.flip();
    }

    private static QuicTLSContext quicTlsContext(FakeQuicTLSEngine engine) {
        QuicTLSContext context = mock(QuicTLSContext.class);
        when(context.createEngine()).thenReturn(engine);
        when(context.createEngine("example.com", 4433)).thenReturn(engine);
        return context;
    }

    private static void assertServerHandshakeTimeoutClose(EnumSet<KeySpace> availableKeys,
                                                          List<KeySpace> expectedKeySpaces) throws Exception {
        try (ConnectionHarness harness = ConnectionHarness.createServer(availableKeys)) {
            TimeoutException timeout = new TimeoutException("test handshake timeout");
            harness.connection.pathManager().addressValidated(harness.connection.peerAddress());

            harness.connection.terminate(QuicCloseCommand.serverHandshakeTimeout(timeout, timeout.getMessage()));

            QuicTermination termination = harness.connection.whenTerminated().toCompletableFuture().join();
            assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
            assertThat(termination.layer(), is(QuicTermination.Layer.TRANSPORT));
            assertThat(termination.errorCode().orElseThrow(), is(QuicTransportErrors.NO_ERROR.code()));
            assertThat(termination.keySpace().orElseThrow(), is(expectedKeySpaces.getFirst()));
            assertThat(termination.cause().orElseThrow(), sameInstance(timeout));
            assertThat(harness.engine.closeKeySpaces, is(expectedKeySpaces));
            assertThat(harness.engine.closeFrames.size(), is(expectedKeySpaces.size()));
            for (ConnectionCloseFrame frame : harness.engine.closeFrames) {
                assertThat(frame.variant(), is(false));
                assertThat(frame.typeField(), is((long) QuicFrame.CONNECTION_CLOSE));
                assertThat(frame.errorCode(), is(QuicTransportErrors.NO_ERROR.code()));
                assertThat(frame.errorFrameType(), is(0L));
                assertThat(frame.reasonString().orElseThrow(), is(""));
            }
        }
    }

    private static void acquireReassemblyPermits(QuicConnectionImpl.ReassemblyBudget budget, int count) {
        for (int i = 0; i < count; i++) {
            assertThat("reassembly permit " + i, budget.tryAcquire(), is(true));
        }
    }

    private static CompletableFuture<? extends QuicSenderStream> openLocalStream(QuicConnection connection,
                                                                               boolean bidi,
                                                                               Duration timeout) {
        return bidi ? connection.openNewLocalBidiStream(timeout) : connection.openNewLocalUniStream(timeout);
    }

    private static ByteBuffer initialPacket() {
        ByteBuffer packet = ByteBuffer.allocate(30);
        packet.put((byte) 0xc0);
        packet.putInt(QuicVersion.QUIC_V1.versionNumber());
        packet.put((byte) 0);
        packet.put((byte) 0);
        packet.put((byte) 0);
        packet.put((byte) 21);
        packet.put((byte) 0);
        packet.put(new byte[20]);
        return packet.flip();
    }

    private static final class TestLogCapture implements AutoCloseable {
        private final List<LogRecord> records = new ArrayList<>();
        private final List<Logger> loggers = new ArrayList<>();
        private final List<Level> levels = new ArrayList<>();
        private final List<Boolean> parentHandlers = new ArrayList<>();
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        private TestLogCapture(Class<?>... sources) {
            this(Level.ALL, sources);
        }

        private TestLogCapture(Level level, Class<?>... sources) {
            handler.setLevel(Level.ALL);
            for (Class<?> source : sources) {
                Logger logger = Logger.getLogger(source.getName());
                loggers.add(logger);
                levels.add(logger.getLevel());
                parentHandlers.add(logger.getUseParentHandlers());
                logger.addHandler(handler);
                logger.setUseParentHandlers(false);
                logger.setLevel(level);
            }
        }

        @Override
        public void close() {
            for (int i = 0; i < loggers.size(); i++) {
                Logger logger = loggers.get(i);
                logger.removeHandler(handler);
                logger.setLevel(levels.get(i));
                logger.setUseParentHandlers(parentHandlers.get(i));
            }
            handler.close();
        }

        private List<LogRecord> records() {
            return List.copyOf(records);
        }
    }

    private record ConnectionHarness(TestQuicInstance instance,
                                     FakeQuicTLSEngine engine,
                                     TestQuicConnection connection)
            implements AutoCloseable {
        static ConnectionHarness create(EnumSet<KeySpace> availableKeys) throws Exception {
            QuicConfig config = QuicConfig.builder()
                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                    .maxUniStreams(4)
                    .buildPrototype();
            return create(availableKeys, config);
        }

        static ConnectionHarness create(EnumSet<KeySpace> availableKeys, QuicConfig config) throws Exception {
            return create(availableKeys, config, QuicRuntimeConfig.create(config));
        }

        static ConnectionHarness createWithDirectBufferPool(boolean useDirectBufferPool) throws Exception {
            QuicConfig config = QuicConfig.create();
            QuicRuntimeConfig defaults = QuicRuntimeConfig.create(config);
            QuicRuntimeConfig.Endpoint endpoint = defaults.endpoint();
            QuicRuntimeConfig runtimeConfig = new QuicRuntimeConfig(
                    config,
                    new QuicRuntimeConfig.Endpoint(endpoint.channelType(),
                                                   endpoint.selectorThreading(),
                                                   endpoint.pollerUsePlatformThreads(),
                                                   endpoint.maxEndpoints(),
                                                   endpoint.sendAsync(),
                                                   endpoint.maxBufferedHigh(),
                                                   endpoint.maxBufferedLow(),
                                                   useDirectBufferPool,
                                                   endpoint.defaultDatagramSize()),
                    defaults.recovery(),
                    defaults.transportParameters(),
                    defaults.confidentialityLimits());
            return create(EnumSet.of(KeySpace.ONE_RTT), config, runtimeConfig);
        }

        static ConnectionHarness create(EnumSet<KeySpace> availableKeys,
                                        QuicConfig config,
                                        QuicRuntimeConfig runtimeConfig) throws Exception {
            FakeQuicTLSEngine engine = new FakeQuicTLSEngine(availableKeys);
            TestQuicInstance instance = new TestQuicInstance(quicTlsContext(engine), true, config);
            TestQuicConnection connection = new TestQuicConnection(QuicVersion.QUIC_V1,
                                                                   instance,
                                                                   runtimeConfig);
            connection.seedPeerConnectionId(PEER_CONNECTION_ID);
            return new ConnectionHarness(instance, engine, connection);
        }

        static ConnectionHarness createServer(EnumSet<KeySpace> availableKeys) throws Exception {
            return createServer(availableKeys, null);
        }

        static ConnectionHarness createServer(EnumSet<KeySpace> availableKeys, QuicConfig config) throws Exception {
            FakeQuicTLSEngine engine = new FakeQuicTLSEngine(availableKeys);
            TestQuicInstance instance = new TestQuicInstance(quicTlsContext(engine), false, config);
            QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(instance.quicConfig());
            TestQuicConnection connection = new TestServerQuicConnection(QuicVersion.QUIC_V1, instance, runtimeConfig);
            connection.seedPeerConnectionId(PEER_CONNECTION_ID);
            return new ConnectionHarness(instance, engine, connection);
        }

        static ConnectionHarness createForVersion(QuicVersion version, boolean client) throws Exception {
            QuicConfig config = QuicConfig.builder()
                    .availableVersions(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1))
                    .maxUniStreams(4)
                    .buildPrototype();
            FakeQuicTLSEngine engine = new FakeQuicTLSEngine(EnumSet.of(KeySpace.INITIAL));
            TestQuicInstance instance = new TestQuicInstance(quicTlsContext(engine), client, config);
            QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(config);
            TestQuicConnection connection = client
                    ? new TestQuicConnection(version, instance, runtimeConfig)
                    : new TestServerQuicConnection(version, instance, runtimeConfig);
            if (!client) {
                connection.seedPeerConnectionId(PEER_CONNECTION_ID);
            }
            return new ConnectionHarness(instance, engine, connection);
        }

        @Override
        public void close() throws Exception {
            instance.close();
        }

        void seedPeerConnectionId() throws Exception {
            connection.seedPeerConnectionId(PEER_CONNECTION_ID);
        }
    }

    private static class TestQuicConnection extends QuicConnectionImpl {
        private final List<QuicPacket> applicationPackets = new ArrayList<>();
        private final AtomicInteger closeIncomingAttempts = new AtomicInteger();
        private final AtomicInteger streamTerminationAttempts = new AtomicInteger();
        private RuntimeException closeIncomingFailure;
        private Error closeIncomingError;
        private RuntimeException streamTerminationFailure;
        private CountDownLatch closeIncomingStarted;
        private CountDownLatch continueCloseIncoming;
        private CountDownLatch pushStarted;
        private CountDownLatch continuePush;
        private CountDownLatch applicationSendStarted;
        private CountDownLatch continueApplicationSend;

        protected TestQuicConnection(QuicVersion firstFlightVersion,
                                     TestQuicInstance quicInstance,
                                     QuicRuntimeConfig runtimeConfig) throws Exception {
            super(firstFlightVersion,
                  quicInstance,
                  runtimeConfig,
                  new InetSocketAddress(InetAddress.getLoopbackAddress(), 4433),
                  "example.com",
                  4433,
                  sslParameters(),
                  "%s",
                  1L);
            buildInitialParameters();
        }

        List<QuicPacket> applicationPackets() {
            return applicationPackets;
        }

        List<QuicPacket> pathControlPackets() {
            return applicationPackets.stream()
                    .filter(packet -> packet.frames().stream()
                            .anyMatch(frame -> frame instanceof PathChallengeFrame || frame instanceof PathResponseFrame))
                    .toList();
        }

        @Override
        public void closeIncoming() {
            closeIncomingAttempts.incrementAndGet();
            CountDownLatch started = closeIncomingStarted;
            CountDownLatch continueLatch = continueCloseIncoming;
            if (started != null && continueLatch != null) {
                started.countDown();
                try {
                    if (!continueLatch.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to continue incoming cleanup");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to continue incoming cleanup", e);
                }
            }
            super.closeIncoming();
            if (closeIncomingError != null) {
                throw closeIncomingError;
            }
            if (closeIncomingFailure != null) {
                throw closeIncomingFailure;
            }
        }

        @Override
        void terminateStreams(QuicTermination termination) {
            streamTerminationAttempts.incrementAndGet();
            super.terminateStreams(termination);
            if (streamTerminationFailure != null) {
                throw streamTerminationFailure;
            }
        }

        @Override
        void pushConnectionCloseDatagram(InetSocketAddress destination,
                                         ByteBuffer datagram,
                                         QuicPathManager.SendPermit permit) {
            CountDownLatch started = pushStarted;
            CountDownLatch continueLatch = continuePush;
            if (started != null && continueLatch != null) {
                started.countDown();
                try {
                    if (!continueLatch.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to continue test datagram push");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to continue test datagram push", e);
                }
            }
            super.pushConnectionCloseDatagram(destination, datagram, permit);
        }

        void seedPeerConnectionId(byte[] bytes) throws Exception {
            InitialPacket initial = mock(InitialPacket.class);
            when(initial.sourceId()).thenReturn(PeerConnectionId.create(bytes));
            updatePeerConnectionId(initial);
        }

        ByteBuffer encodeIncomingInitial(long packetNumber, List<QuicFrame> frames)
                throws QuicKeyUnavailableException, QuicTransportException {
            QuicPacket packet = encoder().newInitialPacket(PeerConnectionId.create(PEER_CONNECTION_ID),
                                                           localConnectionId().orElseThrow(),
                                                           new byte[0],
                                                           packetNumber,
                                                           -1,
                                                           frames,
                                                           codingContext());
            ByteBuffer encoded = ByteBuffer.allocate(packet.size());
            codingContext().writePacket(packet, encoded);
            return encoded.flip();
        }

        boolean isAcknowledged(PacketNumberSpace packetNumberSpace, long packetNumber) {
            return packetSpace(packetNumberSpace).isAcknowledged(packetNumber);
        }

        void installPeerTransportParameters(QuicTransportParameters peerParameters) {
            handleIncomingPeerTransportParams(peerParameters);
        }

        void receiveOneRttCrypto(CryptoFrame frame) throws QuicTransportException {
            incoming1RTTFrame(frame);
        }

        int receiveInitialCrypto(CryptoFrame frame) throws QuicTransportException {
            return incomingInitialFrame(frame);
        }

        void receiveHandshakeDone() throws QuicTransportException {
            incoming1RTTFrame(HandshakeDoneFrame.create());
        }

        void receiveNewConnectionId(NewConnectionIDFrame frame) throws QuicTransportException {
            incoming1RTTFrame(frame);
        }

        void receiveConnectionClose(ConnectionCloseFrame frame) throws QuicTransportException {
            incoming1RTTFrame(frame);
        }

        void receiveRemoteStream(long streamId) throws QuicTransportException {
            incoming1RTTFrame(StreamFrame.create(streamId, 0, 0, false, ByteBuffer.allocate(0)));
        }

        void receiveFinishedRemoteStream(long streamId) throws QuicTransportException {
            incoming1RTTFrame(StreamFrame.create(streamId, 0, 0, true, ByteBuffer.allocate(0)));
            QuicReceiverStream stream = (QuicReceiverStream) streams().findStream(streamId).orElseThrow();
            QuicStreamReader reader = stream.connectReader(SequentialScheduler.lockingScheduler(() -> {
            }));
            reader.start();
            reader.poll().orElseThrow();
        }

        void receiveFinishedRemoteBidiStream(long streamId) throws QuicTransportException {
            incoming1RTTFrame(StreamFrame.create(streamId, 0, 0, true, ByteBuffer.allocate(0)));
            QuicReceiverStream receiver = (QuicReceiverStream) streams().findStream(streamId).orElseThrow();
            QuicStreamReader reader = receiver.connectReader(SequentialScheduler.lockingScheduler(() -> {
            }));
            reader.start();
            assertThat(reader.poll().orElseThrow(), sameInstance(QuicStreamReader.EOF));

            int packetsBeforeFin = applicationPackets.size();
            QuicStreamWriter writer = ((QuicSenderStream) receiver)
                    .connectWriter(SequentialScheduler.lockingScheduler(() -> {
                    }));
            writer.scheduleForWritingAndGetDispatchCompletion(BufferData.create(0), true).join();
            QuicPacket finPacket = applicationPackets.subList(packetsBeforeFin, applicationPackets.size()).stream()
                    .filter(packet -> packet.frames().stream()
                            .filter(StreamFrame.class::isInstance)
                            .map(StreamFrame.class::cast)
                            .anyMatch(frame -> frame.streamId() == streamId && frame.isLast()))
                    .findFirst()
                    .orElseThrow();
            PacketSpaceManager packetSpace = (PacketSpaceManager) packetSpace(PacketNumberSpace.APPLICATION);
            packetSpace.processAckFrame(AckFrame.create(finPacket.packetNumber(),
                                                        0,
                                                        List.of(AckRange.of(0, finPacket.packetNumber()))));
        }

        @Override
        void sendApplicationPacket(QuicPacket packet, QuicPathManager.SendPermit permit)
                throws QuicKeyUnavailableException, QuicTransportException {
            recordApplicationPacket(packet, permit);
        }

        private static SSLParameters sslParameters() {
            SSLParameters parameters = new SSLParameters();
            parameters.setProtocols(new String[] {"TLSv1.3"});
            parameters.setApplicationProtocols(new String[] {"h3"});
            return parameters;
        }

        private void recordApplicationPacket(QuicPacket packet, QuicPathManager.SendPermit permit) {
            applicationPackets.add(packet);
            packetSpace(packet.numberSpace()).packetSent(packet, -1L, packet.packetNumber(), permit.generation());
            if (packet.size() < permit.size()) {
                permit.resize(packet.size());
            }
            permit.commit();
            CountDownLatch started = applicationSendStarted;
            CountDownLatch continueLatch = continueApplicationSend;
            if (started != null && continueLatch != null) {
                started.countDown();
                try {
                    if (!continueLatch.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to continue application packet send");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to continue application packet send", e);
                }
            }
            if (packet.packetType() == PacketType.ONERTT) {
                packet.frames().stream()
                        .filter(HandshakeDoneFrame.class::isInstance)
                        .findAny()
                        .ifPresent(_ -> onHandshakeDoneSent());
            }
        }
    }

    private static final class TestServerQuicConnection extends TestQuicConnection {
        private TestServerQuicConnection(QuicVersion version,
                                         TestQuicInstance quicInstance,
                                         QuicRuntimeConfig runtimeConfig) throws Exception {
            super(version, quicInstance, runtimeConfig);
        }

        @Override
        public boolean isClientConnection() {
            return false;
        }
    }

    private static final class TestQuicInstance implements QuicInstance, AutoCloseable {
        private final QuicTLSContext quicTLSContext;
        private final boolean client;
        private final QuicConfig quicConfig;
        private final List<QuicVersion> requestedTokenVersions = new ArrayList<>();
        private final QuicEndpoint endpoint;
        private Executor executor = Runnable::run;
        private RuntimeException appErrorFailure;

        private TestQuicInstance(QuicTLSContext quicTLSContext, boolean client, QuicConfig quicConfig) {
            this.quicTLSContext = quicTLSContext;
            this.client = client;
            this.quicConfig = quicConfig == null
                    ? QuicConfig.builder()
                            .availableVersions(List.of(QuicVersion.QUIC_V1))
                            .maxUniStreams(4)
                            .buildPrototype()
                    : quicConfig;
            this.endpoint = QuicEndpoint.QuicEndpointFactory.create()
                    .createVirtualThreadedEndpoint(this,
                                                   QuicRuntimeConfig.create(this.quicConfig),
                                                   "quic-test-endpoint",
                                                   new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                                   new QuicTimerQueue(() -> {
                                                   }, () -> "quic.test.timer"));
        }

        @Override
        public Executor executor() {
            return executor;
        }

        @Override
        public QuicEndpoint endpoint() {
            return endpoint;
        }

        @Override
        public void unmatchedQuicPacket(SocketAddress source, QuicPacket.HeadersType type, ByteBuffer buffer) {
        }

        @Override
        public void runtimeFailed(Throwable failure) {
            endpoint.abort(failure);
        }

        @Override
        public boolean isVersionAvailable(QuicVersion quicVersion) {
            return quicConfig.availableVersions().contains(quicVersion);
        }

        @Override
        public List<QuicVersion> availableVersions() {
            return quicConfig.availableVersions();
        }

        @Override
        public boolean isClient() {
            return client;
        }

        @Override
        public Optional<byte[]> initialTokenFor(InetSocketAddress peerAddress, QuicVersion version) {
            requestedTokenVersions.add(version);
            return Optional.empty();
        }

        @Override
        public String instanceId() {
            return "test";
        }

        @Override
        public QuicTLSContext quicTlsContext() {
            return quicTLSContext;
        }

        @Override
        public QuicConfig quicConfig() {
            return quicConfig;
        }

        @Override
        public String appErrorToString(long errorCode) {
            if (appErrorFailure != null) {
                throw appErrorFailure;
            }
            return QuicInstance.super.appErrorToString(errorCode);
        }

        @Override
        public void close() throws Exception {
            endpoint.close();
        }
    }

    private static final class FakeQuicTLSEngine implements QuicPacketTLSEngine {
        private final EnumSet<KeySpace> availableKeys;
        private final AtomicInteger consumedCryptoBytes = new AtomicInteger();
        private final List<KeySpace> closeKeySpaces = new ArrayList<>();
        private final List<ConnectionCloseFrame> closeFrames = new ArrayList<>();
        private final List<CryptoFrame> encryptedCryptoFrames = new ArrayList<>();
        private SSLParameters sslParameters = new SSLParameters();
        private QuicTransportParametersConsumer remoteTransportParametersConsumer = buffer -> {
        };
        private HandshakeState handshakeState = HandshakeState.NEED_RECV_CRYPTO;
        private KeySpace currentSendKeySpace = KeySpace.INITIAL;
        private boolean useClientMode;
        private QuicOneRttContext oneRttContext;
        private ByteBuffer outboundHandshakeBytes;
        private RuntimeException decryptFailure;
        private QuicTransportException encryptFailure;
        private RuntimeException handshakeFailure;
        private KeySpace unavailableOnEncrypt;

        private FakeQuicTLSEngine(EnumSet<KeySpace> availableKeys) {
            this.availableKeys = availableKeys.clone();
        }

        @Override
        public Set<QuicVersion> supportedQuicVersions() {
            return Set.of(QuicVersion.QUIC_V1);
        }

        @Override
        public boolean clientMode() {
            return useClientMode;
        }

        @Override
        public void clientMode(boolean mode) {
            useClientMode = mode;
        }

        @Override
        public SSLParameters sslParameters() {
            return sslParameters;
        }

        @Override
        public void sslParameters(SSLParameters sslParameters) {
            this.sslParameters = sslParameters;
        }

        @Override
        public Optional<String> applicationProtocol() {
            String[] protocols = sslParameters.getApplicationProtocols();
            return protocols.length == 0 ? Optional.empty() : Optional.of(protocols[0]);
        }

        @Override
        public SSLSession session() {
            return null;
        }

        @Override
        public Optional<SSLSession> handshakeSession() {
            return Optional.empty();
        }

        @Override
        public HandshakeState handshakeState() {
            if (handshakeFailure != null) {
                throw handshakeFailure;
            }
            return handshakeState;
        }

        @Override
        public boolean isTLSHandshakeComplete() {
            return false;
        }

        @Override
        public KeySpace currentSendKeySpace() {
            return currentSendKeySpace;
        }

        @Override
        public boolean keysAvailable(KeySpace keySpace) {
            return availableKeys.contains(keySpace);
        }

        @Override
        public void discardKeys(KeySpace keySpace) {
            availableKeys.remove(keySpace);
        }

        @Override
        public void localQuicTransportParametersBuffer(ByteBuffer params) {
        }

        @Override
        public void restartHandshake() {
        }

        @Override
        public void remoteQuicTransportParametersConsumer(QuicTransportParametersConsumer consumer) {
            remoteTransportParametersConsumer = consumer;
        }

        @Override
        public void deriveInitialKeysBuffer(QuicVersion quicVersion, ByteBuffer connectionId) {
            availableKeys.add(KeySpace.INITIAL);
        }

        @Override
        public int headerProtectionSampleSize(KeySpace keySpace) {
            return 16;
        }

        @Override
        public ByteBuffer computeHeaderProtectionMaskBuffer(KeySpace keySpace, boolean incoming, ByteBuffer sample) {
            return ByteBuffer.wrap(new byte[5]);
        }

        @Override
        public int authTagSize() {
            return 16;
        }

        @Override
        public void encryptPacketBuffer(KeySpace keySpace,
                                        long packetNumber,
                                        IntFunction<ByteBuffer> headerGenerator,
                                        ByteBuffer packetPayload,
                                        ByteBuffer output) throws QuicTransportException {
            if (keySpace == KeySpace.ONE_RTT && encryptFailure != null) {
                throw encryptFailure;
            }
            if (keySpace == unavailableOnEncrypt) {
                unavailableOnEncrypt = null;
                availableKeys.remove(keySpace);
                throw new QuicKeyUnavailableException("test key discard race", keySpace);
            }
            closeKeySpaces.add(keySpace);
            ByteBuffer framePayload = packetPayload.slice();
            while (framePayload.hasRemaining()) {
                QuicFrame frame = QuicFrame.decode(framePayload);
                if (frame instanceof ConnectionCloseFrame connectionCloseFrame) {
                    closeFrames.add(connectionCloseFrame);
                    break;
                } else if (frame instanceof CryptoFrame cryptoFrame) {
                    encryptedCryptoFrames.add(cryptoFrame);
                }
            }
            output.put(packetPayload.slice());
            output.put(new byte[authTagSize()]);
        }

        @Override
        public void decryptPacketBuffer(KeySpace keySpace,
                                        long packetNumber,
                                        int keyPhase,
                                        ByteBuffer packet,
                                        int headerLength,
                                        ByteBuffer output) {
            if (decryptFailure != null) {
                throw decryptFailure;
            }
            ByteBuffer payload = packet.slice(packet.position() + headerLength, packet.remaining() - headerLength);
            output.put(payload);
        }

        @Override
        public void signRetryPacketBuffer(QuicVersion version,
                                          ByteBuffer originalConnectionId,
                                          ByteBuffer packet,
                                          ByteBuffer output) {
            output.put(new byte[16]);
        }

        @Override
        public void verifyRetryPacketBuffer(QuicVersion version, ByteBuffer originalConnectionId, ByteBuffer packet) {
        }

        @Override
        public Optional<ByteBuffer> handshakeBytesBuffer(KeySpace keySpace) {
            if (keySpace != currentSendKeySpace || outboundHandshakeBytes == null) {
                return Optional.empty();
            }
            ByteBuffer result = outboundHandshakeBytes;
            outboundHandshakeBytes = null;
            handshakeState = HandshakeState.NEED_RECV_CRYPTO;
            return Optional.of(result);
        }

        @Override
        public void consumeHandshakeBytesBuffer(KeySpace keySpace, ByteBuffer payload) {
            consumedCryptoBytes.addAndGet(payload.remaining());
        }

        @Override
        public Optional<Runnable> delegatedTask() {
            return Optional.empty();
        }

        @Override
        public boolean tryMarkHandshakeDone() {
            return false;
        }

        @Override
        public boolean tryReceiveHandshakeDone() {
            if (handshakeState == HandshakeState.NEED_RECV_HANDSHAKE_DONE) {
                handshakeState = HandshakeState.HANDSHAKE_CONFIRMED;
                return true;
            }
            return false;
        }

        @Override
        public void versionNegotiated(QuicVersion quicVersion) {
        }

        @Override
        public void oneRttContext(QuicOneRttContext ctx) {
            oneRttContext = ctx;
        }

        private void setHandshakeState(HandshakeState handshakeState) {
            this.handshakeState = handshakeState;
        }

        private void queueHandshakeFlight(KeySpace keySpace, ByteBuffer bytes) {
            currentSendKeySpace = keySpace;
            outboundHandshakeBytes = bytes;
            handshakeState = HandshakeState.NEED_SEND_CRYPTO;
        }
    }

}
