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

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.security.auth.DestroyFailedException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuicTlsKeySharePossessionTest {
    @Test
    void shouldDestroyPrivateKeyAfterDerivationAndKeepPublicEntry() throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        PublicKey publicKey = mock(PublicKey.class);
        byte[] sharedSecret = {1, 2, 3};
        when(group.generateKeyPair(any(SecureRandom.class))).thenReturn(new KeyPair(publicKey, privateKey));
        when(group.encodePublicKey(publicKey)).thenReturn(new byte[] {4, 5, 6});
        when(group.deriveSharedSecret(eq(privateKey), any(byte[].class))).thenReturn(sharedSecret);
        QuicTlsKeySharePossession possession = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry publicEntry = possession.keyShareEntry();
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(group, new byte[] {7, 8, 9});

        assertThat(possession.sharedSecret(peer), sameInstance(sharedSecret));

        verify(privateKey).destroy();
        assertThat(possession.keyShareEntry(), equalTo(publicEntry));
        assertThrows(IllegalStateException.class, () -> possession.sharedSecret(peer));
    }

    @Test
    void shouldConsumePrivateKeyWhenProviderDoesNotSupportDestruction() throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        byte[] sharedSecret = {1, 2, 3};
        when(group.generateKeyPair(any(SecureRandom.class)))
                .thenReturn(new KeyPair(mock(PublicKey.class), privateKey));
        when(group.deriveSharedSecret(eq(privateKey), any(byte[].class))).thenReturn(sharedSecret);
        doThrow(new DestroyFailedException("Provider does not support destruction")).when(privateKey).destroy();
        QuicTlsKeySharePossession possession = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(group, new byte[] {4, 5, 6});

        assertThat(possession.sharedSecret(peer), sameInstance(sharedSecret));

        verify(privateKey).destroy();
        assertThrows(IllegalStateException.class, () -> possession.sharedSecret(peer));
    }

    @Test
    void shouldPreserveDerivationFailureAndDestroyPrivateKey() throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        QuicTransportException failure = QuicTlsHandshakeMessages.illegalParameter("Invalid peer key share");
        when(group.generateKeyPair(any(SecureRandom.class)))
                .thenReturn(new KeyPair(mock(PublicKey.class), privateKey));
        when(group.deriveSharedSecret(eq(privateKey), any(byte[].class))).thenThrow(failure);
        doThrow(new DestroyFailedException("Provider does not support destruction")).when(privateKey).destroy();
        QuicTlsKeySharePossession possession = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(group, new byte[] {4, 5, 6});

        assertThat(assertThrows(QuicTransportException.class, () -> possession.sharedSecret(peer)), sameInstance(failure));

        verify(privateKey).destroy();
        assertThrows(IllegalStateException.class, () -> possession.sharedSecret(peer));
    }

    @Test
    void shouldDiscardPrivateKeyOnceAndKeepPublicEntry() throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        PublicKey publicKey = mock(PublicKey.class);
        when(group.generateKeyPair(any(SecureRandom.class))).thenReturn(new KeyPair(publicKey, privateKey));
        when(group.encodePublicKey(publicKey)).thenReturn(new byte[] {1, 2, 3});
        doThrow(new DestroyFailedException("Provider does not support destruction")).when(privateKey).destroy();
        QuicTlsKeySharePossession possession = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry publicEntry = possession.keyShareEntry();

        possession.discard();
        possession.discard();

        verify(privateKey).destroy();
        assertThat(possession.keyShareEntry(), equalTo(publicEntry));
        assertThrows(IllegalStateException.class, () -> possession.sharedSecret(publicEntry));
        verify(group, never()).deriveSharedSecret(any(PrivateKey.class), any(byte[].class));
    }

    @Test
    void shouldKeepActiveDerivationPrivateKeyOwnedUntilDerivationFinishes() throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        byte[] sharedSecret = {1, 2, 3};
        CountDownLatch derivationStarted = new CountDownLatch(1);
        CountDownLatch finishDerivation = new CountDownLatch(1);
        when(group.generateKeyPair(any(SecureRandom.class)))
                .thenReturn(new KeyPair(mock(PublicKey.class), privateKey));
        when(group.deriveSharedSecret(eq(privateKey), any(byte[].class))).thenAnswer(_ -> {
            derivationStarted.countDown();
            assertThat("Derivation was released", finishDerivation.await(10, TimeUnit.SECONDS), is(true));
            return sharedSecret;
        });
        QuicTlsKeySharePossession possession = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(group, new byte[] {4, 5, 6});

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> derivation = executor.submit(() -> possession.sharedSecret(peer));
            try {
                assertThat("Derivation started", derivationStarted.await(10, TimeUnit.SECONDS), is(true));
                possession.discard();

                verify(privateKey, never()).destroy();
                assertThrows(IllegalStateException.class, () -> possession.sharedSecret(peer));
            } finally {
                finishDerivation.countDown();
            }
            assertThat(derivation.get(10, TimeUnit.SECONDS), sameInstance(sharedSecret));
        }
        verify(privateKey).destroy();
    }

    @Test
    void shouldDestroyGeneratedPrivateKeyWhenPublicKeyIsMissing() throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        when(group.generateKeyPair(any(SecureRandom.class))).thenReturn(new KeyPair(null, privateKey));

        assertThrows(NullPointerException.class, () -> QuicTlsKeySharePossession.create(group, new SecureRandom()));

        verify(privateKey).destroy();
    }
}
