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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.crypto.spec.SecretKeySpec;

import io.helidon.quic.QuicPacketProtectionJmhBenchmark.PacketState;
import io.helidon.quic.QuicPacketProtectionJmhBenchmark.SharedProtectionState;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class QuicPacketCounterBenchmarkTest {
    private static final int HEADER_LENGTH = 24;
    private static final int SENDERS = 4;
    private static final int PACKETS_PER_SENDER = 8;

    @ParameterizedTest
    @MethodSource("packetScenarios")
    void threadLocalManagersRoundTripEachPacket(String cipherSuite, int datagramSize) {
        var benchmark = new QuicPacketProtectionJmhBenchmark();
        List<PacketState> senders = createSenders(cipherSuite, datagramSize);
        QuicPacketProtection receiver = createReceiver(cipherSuite);
        for (int packet = 0; packet < PACKETS_PER_SENDER; packet++) {
            for (PacketState sender : senders) {
                long previousPacketNumber = sender.encryptionPacketNumber();
                sender.setUpInvocation();
                if (packet > 0) {
                    assertThat("next nonce within a sender's range",
                               sender.encryptionPacketNumber(),
                               is(previousPacketNumber + 1));
                }
                verifyEncryptedPacket(sender, receiver, benchmark.threadLocalOneRttEncrypt(sender));
            }
        }
        assertDisjointPacketNumbers(senders);
    }

    @ParameterizedTest
    @MethodSource("packetScenarios")
    void sharedManagerRoundTripsConcurrentSenders(String cipherSuite, int datagramSize) throws Exception {
        var benchmark = new QuicPacketProtectionJmhBenchmark();
        var shared = new SharedProtectionState();
        shared.setUp();
        List<PacketState> senders = createSenders(cipherSuite, datagramSize);
        var start = new CyclicBarrier(SENDERS);
        var executor = Executors.newFixedThreadPool(SENDERS,
                                                   Thread.ofPlatform().daemon().name("quic-counter-test-", 0).factory());
        List<Future<?>> results = new ArrayList<>();
        try {
            for (PacketState sender : senders) {
                results.add(executor.submit(() -> {
                    QuicPacketProtection receiver = createReceiver(cipherSuite);
                    start.await(10, TimeUnit.SECONDS);
                    for (int packet = 0; packet < PACKETS_PER_SENDER; packet++) {
                        long previousPacketNumber = sender.encryptionPacketNumber();
                        sender.setUpInvocation();
                        if (packet > 0) {
                            assertThat("next nonce within a sender's range",
                                       sender.encryptionPacketNumber(),
                                       is(previousPacketNumber + 1));
                        }
                        verifyEncryptedPacket(sender, receiver, benchmark.sharedOneRttEncrypt(sender, shared));
                    }
                    return null;
                }));
            }
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            for (Future<?> result : results) {
                result.cancel(true);
            }
            executor.shutdownNow();
            assertThat("benchmark senders terminate after cancellation",
                       executor.awaitTermination(5, TimeUnit.SECONDS),
                       is(true));
        }
        assertDisjointPacketNumbers(senders);
    }

    @ParameterizedTest
    @MethodSource("packetScenarios")
    void rawProtectionStillRoundTripsWithTheSameFixture(String cipherSuite, int datagramSize) {
        var benchmark = new QuicPacketProtectionJmhBenchmark();
        var shared = new SharedProtectionState();
        shared.setUp();
        List<PacketState> senders = createSenders(cipherSuite, datagramSize);
        QuicPacketProtection receiver = createReceiver(cipherSuite);
        for (PacketState sender : senders) {
            sender.setUpInvocation();
            verifyEncryptedPacket(sender, receiver, benchmark.threadLocalEncrypt(sender));
            sender.setUpInvocation();
            verifyEncryptedPacket(sender, receiver, benchmark.sharedEncrypt(sender, shared));
        }
        assertDisjointPacketNumbers(senders);
    }

    private static Stream<Arguments> packetScenarios() {
        return Stream.of("TLS_AES_128_GCM_SHA256", "TLS_CHACHA20_POLY1305_SHA256")
                .flatMap(cipher -> Stream.of(64, 1200, 1452, 65527)
                        .map(size -> Arguments.of(cipher, size)));
    }

    private static List<PacketState> createSenders(String cipherSuite, int datagramSize) {
        List<PacketState> senders = new ArrayList<>();
        for (int sender = 0; sender < SENDERS; sender++) {
            var state = new PacketState();
            state.cipherSuite = cipherSuite;
            state.datagramSize = datagramSize;
            state.setUpTrial();
            senders.add(state);
        }
        return senders;
    }

    private static QuicPacketProtection createReceiver(String cipherSuiteName) {
        QuicTls13CipherSuite cipherSuite = QuicTls13CipherSuite.forName(cipherSuiteName);
        byte[] secret = new byte[cipherSuite.hashLength()];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (0x11 + i * 31);
        }
        QuicPacketProtectionKeys keys = QuicPacketProtectionKeys.derive(QuicVersion.QUIC_V1,
                                                                     cipherSuite,
                                                                     new SecretKeySpec(secret, "TlsSecret"));
        return QuicPacketProtection.create(cipherSuite, keys);
    }

    private static void verifyEncryptedPacket(PacketState sender, QuicPacketProtection receiver, long evidence) {
        ByteBuffer ciphertext = sender.encryptionOutput();
        assertThat("complete protected datagram", ciphertext.remaining(), is(sender.datagramSize));
        assertThat("consumed output size", evidence >>> 32, is((long) sender.datagramSize));
        assertThat("consumed header byte", (evidence >>> 8) & 0xff, is((long) ciphertext.get(0) & 0xff));
        assertThat("consumed tag byte", evidence & 0xff, is((long) ciphertext.get(ciphertext.limit() - 1) & 0xff));
        byte[] expected = new byte[sender.datagramSize - HEADER_LENGTH - QuicPacketProtection.AUTH_TAG_SIZE];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (0x71 + i * 31);
        }
        ByteBuffer plaintext = ByteBuffer.allocate(expected.length);
        receiver.decryptPacket(sender.encryptionPacketNumber(), ciphertext, HEADER_LENGTH, plaintext);
        assertThat("authenticated original payload", plaintext.flip(), is(ByteBuffer.wrap(expected)));
    }

    private static void assertDisjointPacketNumbers(List<PacketState> senders) {
        assertThat("each sender owns a distinct 2^32 packet-number range",
                   senders.stream().map(sender -> sender.encryptionPacketNumber() >>> 32).distinct().count(),
                   is((long) senders.size()));
    }
}
