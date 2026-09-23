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
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldConsumeAllLocalSharesAfterSelectingOne() {
        SecureRandom secureRandom = new SecureRandom();
        QuicTlsLocalKeyShares local = QuicTlsLocalKeyShares.create(
                List.of(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1), secureRandom);
        QuicTlsKeySharePossession peer = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, secureRandom);
        QuicTlsKeySharePossession alternative = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.SECP256_R1, secureRandom);
        List<QuicTlsKeyShareEntry> publicEntries = local.keyShareEntries();

        byte[] sharedSecret = local.sharedSecret(peer.keyShareEntry());

        assertThat(sharedSecret, equalTo(peer.sharedSecret(local.keyShareEntry(QuicTlsNamedGroup.X25519))));
        assertThat(local.keyShareEntries(), equalTo(publicEntries));
        assertThrows(IllegalStateException.class, () -> local.sharedSecret(alternative.keyShareEntry()));
    }

    @Test
    void shouldConsumeAllLocalSharesAfterDerivationFailure() {
        SecureRandom secureRandom = new SecureRandom();
        QuicTlsLocalKeyShares local = QuicTlsLocalKeyShares.create(
                List.of(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1), secureRandom);
        QuicTlsKeyShareEntry malformed = QuicTlsKeyShareEntry.create(QuicTlsNamedGroup.X25519, new byte[31]);
        QuicTlsKeySharePossession alternative = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.SECP256_R1, secureRandom);

        QuicTransportException thrown = assertThrows(QuicTransportException.class, () -> local.sharedSecret(malformed));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 47));
        assertThrows(IllegalStateException.class, () -> local.sharedSecret(alternative.keyShareEntry()));
    }

    @Test
    void shouldDestroyCreatedKeysWhenLaterGroupIsInvalid() throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        when(group.generateKeyPair(any(SecureRandom.class)))
                .thenReturn(new KeyPair(mock(PublicKey.class), privateKey));

        assertThrows(NullPointerException.class,
                     () -> QuicTlsLocalKeyShares.create(Arrays.asList(group, null), new SecureRandom()));

        verify(privateKey).destroy();
    }

    @Test
    void shouldDestroyCreatedKeysWhenNamedGroupIsDuplicated() throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        List<PrivateKey> generatedKeys = new ArrayList<>();
        when(group.generateKeyPair(any(SecureRandom.class))).thenAnswer(_ -> {
            PrivateKey privateKey = mock(PrivateKey.class);
            generatedKeys.add(privateKey);
            return new KeyPair(mock(PublicKey.class), privateKey);
        });

        assertThrows(IllegalArgumentException.class,
                     () -> QuicTlsLocalKeyShares.create(List.of(group, group), new SecureRandom()));

        assertThat(generatedKeys.size(), greaterThan(0));
        for (PrivateKey privateKey : generatedKeys) {
            verify(privateKey).destroy();
        }
    }

    @Test
    void shouldDestroyCreatedKeysWhenPublicKeyEncodingFails() throws Exception {
        QuicTlsNamedGroup firstGroup = mock(QuicTlsNamedGroup.class);
        QuicTlsNamedGroup secondGroup = mock(QuicTlsNamedGroup.class);
        PrivateKey firstPrivateKey = mock(PrivateKey.class);
        PrivateKey secondPrivateKey = mock(PrivateKey.class);
        PublicKey firstPublicKey = mock(PublicKey.class);
        PublicKey secondPublicKey = mock(PublicKey.class);
        when(firstGroup.generateKeyPair(any(SecureRandom.class))).thenReturn(new KeyPair(firstPublicKey, firstPrivateKey));
        when(secondGroup.generateKeyPair(any(SecureRandom.class))).thenReturn(new KeyPair(secondPublicKey, secondPrivateKey));
        when(firstGroup.encodePublicKey(firstPublicKey)).thenReturn(new byte[] {1, 2, 3});
        when(secondGroup.encodePublicKey(secondPublicKey)).thenThrow(new IllegalArgumentException("Invalid public key"));

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                () -> QuicTlsLocalKeyShares.create(List.of(firstGroup, secondGroup), new SecureRandom()));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        verify(firstPrivateKey).destroy();
        verify(secondPrivateKey).destroy();
    }

    @Test
    void shouldDiscardAllLocalPrivateKeysOnce() throws Exception {
        QuicTlsNamedGroup firstGroup = mock(QuicTlsNamedGroup.class);
        QuicTlsNamedGroup secondGroup = mock(QuicTlsNamedGroup.class);
        PrivateKey firstPrivateKey = mock(PrivateKey.class);
        PrivateKey secondPrivateKey = mock(PrivateKey.class);
        PublicKey firstPublicKey = mock(PublicKey.class);
        PublicKey secondPublicKey = mock(PublicKey.class);
        when(firstGroup.generateKeyPair(any(SecureRandom.class))).thenReturn(new KeyPair(firstPublicKey, firstPrivateKey));
        when(secondGroup.generateKeyPair(any(SecureRandom.class))).thenReturn(new KeyPair(secondPublicKey, secondPrivateKey));
        when(firstGroup.encodePublicKey(firstPublicKey)).thenReturn(new byte[] {1, 2, 3});
        when(secondGroup.encodePublicKey(secondPublicKey)).thenReturn(new byte[] {4, 5, 6});
        QuicTlsLocalKeyShares local = QuicTlsLocalKeyShares.create(List.of(firstGroup, secondGroup), new SecureRandom());
        List<QuicTlsKeyShareEntry> publicEntries = local.keyShareEntries();

        local.discard();
        local.discard();

        verify(firstPrivateKey).destroy();
        verify(secondPrivateKey).destroy();
        assertThat(local.keyShareEntries(), equalTo(publicEntries));
        assertThrows(IllegalStateException.class, () -> local.sharedSecret(publicEntries.getFirst()));
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
