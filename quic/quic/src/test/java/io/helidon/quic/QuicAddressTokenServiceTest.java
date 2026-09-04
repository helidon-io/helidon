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
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicAddressTokenServiceTest {
    private static final InetSocketAddress PEER = address(7777, 1);
    private static final QuicConnectionId ORIGINAL_DESTINATION = connectionId(8, 11);
    private static final QuicConnectionId RETRY_SOURCE = connectionId(12, 31);
    private static final QuicConnectionId CLIENT_SOURCE = connectionId(9, 51);

    @ParameterizedTest
    @EnumSource(QuicVersion.class)
    void validatesRetryContext(QuicVersion version) {
        try (QuicAddressTokenService service = QuicAddressTokenService.create()) {
            byte[] token = service.retryToken(PEER,
                                              version,
                                              ORIGINAL_DESTINATION,
                                              RETRY_SOURCE,
                                              CLIENT_SOURCE)
                    .orElseThrow();

            QuicAddressTokenService.ValidatedToken validated = service.validate(PEER,
                                                                                 version,
                                                                                 RETRY_SOURCE,
                                                                                 CLIENT_SOURCE,
                                                                                 token)
                    .orElseThrow();
            assertThat(validated.kind(), is(QuicAddressTokenService.TokenKind.RETRY));
            assertThat(validated.originalDestinationId(), is(ORIGINAL_DESTINATION));
            assertThat(validated.retrySourceId(), is(RETRY_SOURCE));
            assertThat(token.length, lessThanOrEqualTo(QuicAddressTokenService.MAX_TOKEN_SIZE));

            assertThat(service.validate(address(7778, 1), version, RETRY_SOURCE, CLIENT_SOURCE, token).isEmpty(),
                       is(true));
            assertThat(service.validate(address(7777, 2), version, RETRY_SOURCE, CLIENT_SOURCE, token).isEmpty(),
                       is(true));
            assertThat(service.validate(PEER, other(version), RETRY_SOURCE, CLIENT_SOURCE, token).isEmpty(), is(true));
            assertThat(service.validate(PEER,
                                        version,
                                        connectionId(RETRY_SOURCE.length(), 71),
                                        CLIENT_SOURCE,
                                        token).isEmpty(),
                       is(true));
            assertThat(service.validate(PEER,
                                        version,
                                        RETRY_SOURCE,
                                        connectionId(CLIENT_SOURCE.length(), 91),
                                        token).isEmpty(),
                       is(true));
        }
    }

    @ParameterizedTest
    @EnumSource(QuicVersion.class)
    void validatesNewTokenAcrossPortChanges(QuicVersion version) {
        try (QuicAddressTokenService service = QuicAddressTokenService.create()) {
            byte[] token = service.newToken(PEER, version).orElseThrow();

            QuicAddressTokenService.ValidatedToken validated = service.validate(address(8888, 1),
                                                                                 version,
                                                                                 RETRY_SOURCE,
                                                                                 CLIENT_SOURCE,
                                                                                 token)
                    .orElseThrow();
            assertThat(validated.kind(), is(QuicAddressTokenService.TokenKind.NEW_TOKEN));
            assertThat(validated.originalDestinationId(), is((QuicConnectionId) null));
            assertThat(validated.retrySourceId(), is((QuicConnectionId) null));
            assertThat(service.validate(address(8888, 2), version, RETRY_SOURCE, CLIENT_SOURCE, token).isEmpty(),
                       is(true));
            assertThat(service.validate(PEER, other(version), RETRY_SOURCE, CLIENT_SOURCE, token).isEmpty(), is(true));
        }
    }

    @Test
    void rejectsTamperingTruncationOversizeAndWrongKind() {
        try (QuicAddressTokenService service = QuicAddressTokenService.create()) {
            byte[] retry = service.retryToken(PEER,
                                              QuicVersion.QUIC_V1,
                                              ORIGINAL_DESTINATION,
                                              RETRY_SOURCE,
                                              CLIENT_SOURCE)
                    .orElseThrow();
            byte[] tampered = retry.clone();
            tampered[tampered.length - 1] ^= 1;
            assertThat(service.validate(PEER,
                                        QuicVersion.QUIC_V1,
                                        RETRY_SOURCE,
                                        CLIENT_SOURCE,
                                        tampered).isEmpty(),
                       is(true));
            assertThat(service.validate(PEER,
                                        QuicVersion.QUIC_V1,
                                        RETRY_SOURCE,
                                        CLIENT_SOURCE,
                                        Arrays.copyOf(retry, retry.length - 1)).isEmpty(),
                       is(true));
            assertThat(service.validate(PEER,
                                        QuicVersion.QUIC_V1,
                                        RETRY_SOURCE,
                                        CLIENT_SOURCE,
                                        new byte[QuicAddressTokenService.MAX_TOKEN_SIZE + 1]).isEmpty(),
                       is(true));

            byte[] kindChanged = retry.clone();
            kindChanged[1] = QuicAddressTokenService.TokenKind.NEW_TOKEN.marker();
            assertThat(service.validate(PEER,
                                        QuicVersion.QUIC_V1,
                                        RETRY_SOURCE,
                                        CLIENT_SOURCE,
                                        kindChanged).isEmpty(),
                       is(true));
        }
    }

    @Test
    void expiresTokensAndAcceptsThePreviousKeyEpoch() {
        AtomicLong time = new AtomicLong();
        try (QuicAddressTokenService service = QuicAddressTokenService.create(Duration.ofNanos(20),
                                                                               Duration.ofNanos(100),
                                                                               Duration.ofNanos(100),
                                                                               time::get,
                                                                               new SecureRandom())) {
            time.set(90);
            byte[] token = service.newToken(PEER, QuicVersion.QUIC_V1).orElseThrow();
            time.set(100);
            service.newToken(PEER, QuicVersion.QUIC_V1).orElseThrow();
            assertThat(service.validate(PEER,
                                        QuicVersion.QUIC_V1,
                                        RETRY_SOURCE,
                                        CLIENT_SOURCE,
                                        token).isPresent(),
                       is(true));

            time.set(190);
            assertThat(service.validate(PEER,
                                        QuicVersion.QUIC_V1,
                                        RETRY_SOURCE,
                                        CLIENT_SOURCE,
                                        token).isEmpty(),
                       is(true));
        }
    }

    @Test
    void rejectsAClockValueBeforeIssueAndInvalidatesTokensOnClose() {
        AtomicLong time = new AtomicLong(10);
        QuicAddressTokenService service = QuicAddressTokenService.create(Duration.ofNanos(100),
                                                                          Duration.ofNanos(100),
                                                                          Duration.ofNanos(100),
                                                                          time::get,
                                                                          new SecureRandom());
        byte[] token = service.newToken(PEER, QuicVersion.QUIC_V1).orElseThrow();
        time.set(9);
        assertThat(service.validate(PEER,
                                    QuicVersion.QUIC_V1,
                                    RETRY_SOURCE,
                                    CLIENT_SOURCE,
                                    token).isEmpty(),
                   is(true));

        service.close();
        service.close();
        assertThat(service.newToken(PEER, QuicVersion.QUIC_V1).isEmpty(), is(true));
        assertThat(service.validate(PEER,
                                    QuicVersion.QUIC_V1,
                                    RETRY_SOURCE,
                                    CLIENT_SOURCE,
                                    token).isEmpty(),
                   is(true));
    }

    @Test
    void generatesUniqueTokens() {
        try (QuicAddressTokenService service = QuicAddressTokenService.create()) {
            Set<String> tokens = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                tokens.add(HexFormat.of().formatHex(service.newToken(PEER, QuicVersion.QUIC_V1).orElseThrow()));
            }
            assertThat(tokens.size(), is(100));
        }
    }

    @Test
    void validatesRotationBounds() {
        assertThrows(IllegalArgumentException.class,
                     () -> QuicAddressTokenService.create(Duration.ofSeconds(1),
                                                          Duration.ofSeconds(2),
                                                          Duration.ofSeconds(1),
                                                          System::nanoTime,
                                                          new SecureRandom()));
    }

    private static QuicVersion other(QuicVersion version) {
        return version == QuicVersion.QUIC_V1 ? QuicVersion.QUIC_V2 : QuicVersion.QUIC_V1;
    }

    private static InetSocketAddress address(int port, int lastOctet) {
        try {
            return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, (byte) lastOctet}), port);
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    private static QuicConnectionId connectionId(int length, int start) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (start + i);
        }
        return PeerConnectionId.create(bytes);
    }
}
