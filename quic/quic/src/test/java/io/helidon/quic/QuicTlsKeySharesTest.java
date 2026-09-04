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
import java.security.SecureRandom;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTlsKeySharesTest {
    @Test
    void shouldRoundTripSupportedGroupsExtension() {
        List<QuicTlsNamedGroup> namedGroups = List.of(
                QuicTlsNamedGroup.X25519,
                QuicTlsNamedGroup.SECP256_R1,
                QuicTlsNamedGroup.SECP384_R1);

        List<QuicTlsNamedGroup> decoded = QuicTlsSupportedGroups.decode(QuicTlsSupportedGroups.encode(namedGroups));

        assertThat(decoded, equalTo(namedGroups));
    }

    @Test
    void shouldRoundTripClientHelloKeyShares() {
        SecureRandom secureRandom = new SecureRandom();
        QuicTlsKeySharePossession x25519 = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, secureRandom);
        QuicTlsKeySharePossession secp256r1 =
                QuicTlsKeySharePossession.create(QuicTlsNamedGroup.SECP256_R1, secureRandom);

        List<QuicTlsKeyShareEntry> expected = List.of(x25519.keyShareEntry(), secp256r1.keyShareEntry());
        List<QuicTlsKeyShareEntry> decoded = QuicTlsKeyShares.decodeClientHello(QuicTlsKeyShares.encodeClientHello(expected));

        assertThat(decoded, equalTo(expected));
    }

    @Test
    void shouldRoundTripServerHelloKeyShare() {
        QuicTlsKeyShareEntry expected = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, new SecureRandom())
                .keyShareEntry();

        QuicTlsKeyShareEntry decoded = QuicTlsKeyShares.decodeServerHello(QuicTlsKeyShares.encodeServerHello(expected));

        assertThat(decoded, equalTo(expected));
    }

    @Test
    void shouldRoundTripHelloRetryRequestSelectedGroup() {
        QuicTlsNamedGroup decoded =
                QuicTlsKeyShares.decodeHelloRetryRequest(QuicTlsKeyShares.encodeHelloRetryRequest(QuicTlsNamedGroup.X25519));

        assertThat(decoded, is(QuicTlsNamedGroup.X25519));
    }

    @Test
    void shouldDeriveMatchingSharedSecretForX25519() {
        assertSharedSecretMatches(QuicTlsNamedGroup.X25519);
    }

    @Test
    void shouldDeriveMatchingSharedSecretForSecp256r1() {
        assertSharedSecretMatches(QuicTlsNamedGroup.SECP256_R1);
    }

    @Test
    void shouldIgnoreUnsupportedClientHelloKeyShares() {
        QuicTlsKeyShareEntry supported = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, new SecureRandom())
                .keyShareEntry();
        ByteBuffer encoded = ByteBuffer.allocate(2 + 5 + supported.encodedLength());
        encoded.putShort((short) (5 + supported.encodedLength()));
        encoded.putShort((short) 0x0100);
        encoded.putShort((short) 1);
        encoded.put((byte) 0);
        supported.encodeTo(encoded);

        List<QuicTlsKeyShareEntry> decoded = QuicTlsKeyShares.decodeClientHello(encoded.flip());

        assertThat(decoded, equalTo(List.of(supported)));
    }

    @Test
    void shouldRejectMalformedPeerKeyShareAsIllegalParameter() {
        QuicTlsKeySharePossession local =
                QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, new SecureRandom());
        QuicTlsKeyShareEntry malformed = QuicTlsKeyShareEntry.create(QuicTlsNamedGroup.X25519, new byte[31]);

        QuicTransportException thrown = assertThrows(QuicTransportException.class, () -> local.sharedSecret(malformed));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 47));
    }

    @Test
    void shouldRejectMismatchedPeerKeyShareAsIllegalParameter() {
        QuicTlsKeySharePossession local =
                QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, new SecureRandom());
        QuicTlsKeyShareEntry peer =
                QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X448, new SecureRandom()).keyShareEntry();

        QuicTransportException thrown = assertThrows(QuicTransportException.class, () -> local.sharedSecret(peer));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 47));
    }

    private static void assertSharedSecretMatches(QuicTlsNamedGroup namedGroup) {
        SecureRandom secureRandom = new SecureRandom();
        QuicTlsKeySharePossession local = QuicTlsKeySharePossession.create(namedGroup, secureRandom);
        QuicTlsKeySharePossession peer = QuicTlsKeySharePossession.create(namedGroup, secureRandom);

        byte[] localSecret = local.sharedSecret(peer.keyShareEntry());
        byte[] peerSecret = peer.sharedSecret(local.keyShareEntry());

        assertThat(localSecret.length, greaterThan(0));
        assertThat(localSecret, not(equalTo(new byte[localSecret.length])));
        assertThat(localSecret, equalTo(peerSecret));
    }
}
