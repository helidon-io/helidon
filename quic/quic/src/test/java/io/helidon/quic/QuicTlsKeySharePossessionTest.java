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
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.security.auth.DestroyFailedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
        PrivateKey privateKey = mock(ProviderPrivateKey.class);
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

    @ParameterizedTest
    @MethodSource("destructionFailures")
    void shouldConsumePrivateKeyWhenProviderDestructionFails(Exception destructionFailure) throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(ProviderPrivateKey.class);
        byte[] sharedSecret = {1, 2, 3};
        when(group.generateKeyPair(any(SecureRandom.class)))
                .thenReturn(new KeyPair(mock(PublicKey.class), privateKey));
        when(group.deriveSharedSecret(eq(privateKey), any(byte[].class))).thenReturn(sharedSecret);
        doThrow(destructionFailure).when(privateKey).destroy();
        QuicTlsKeySharePossession possession = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(group, new byte[] {4, 5, 6});

        assertThat(possession.sharedSecret(peer), sameInstance(sharedSecret));

        verify(privateKey).destroy();
        assertThrows(IllegalStateException.class, () -> possession.sharedSecret(peer));
    }

    @ParameterizedTest
    @MethodSource("destructionFailures")
    void shouldPreserveDerivationFailureWhenProviderDestructionFails(Exception destructionFailure) throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(ProviderPrivateKey.class);
        QuicTransportException failure = QuicTlsHandshakeMessages.illegalParameter("Invalid peer key share");
        when(group.generateKeyPair(any(SecureRandom.class)))
                .thenReturn(new KeyPair(mock(PublicKey.class), privateKey));
        when(group.deriveSharedSecret(eq(privateKey), any(byte[].class))).thenThrow(failure);
        doThrow(destructionFailure).when(privateKey).destroy();
        QuicTlsKeySharePossession possession = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(group, new byte[] {4, 5, 6});

        assertThat(assertThrows(QuicTransportException.class, () -> possession.sharedSecret(peer)), sameInstance(failure));

        verify(privateKey).destroy();
        assertThrows(IllegalStateException.class, () -> possession.sharedSecret(peer));
    }

    @ParameterizedTest
    @MethodSource("destructionFailures")
    void shouldDiscardPrivateKeyOnceWhenProviderDestructionFails(Exception destructionFailure) throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = mock(ProviderPrivateKey.class);
        PublicKey publicKey = mock(PublicKey.class);
        when(group.generateKeyPair(any(SecureRandom.class))).thenReturn(new KeyPair(publicKey, privateKey));
        when(group.encodePublicKey(publicKey)).thenReturn(new byte[] {1, 2, 3});
        doThrow(destructionFailure).when(privateKey).destroy();
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
        PrivateKey privateKey = mock(ProviderPrivateKey.class);
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
        PrivateKey privateKey = mock(ProviderPrivateKey.class);
        when(group.generateKeyPair(any(SecureRandom.class))).thenReturn(new KeyPair(null, privateKey));

        assertThrows(NullPointerException.class, () -> QuicTlsKeySharePossession.create(group, new SecureRandom()));

        verify(privateKey).destroy();
    }

    @ParameterizedTest
    @MethodSource("destructionFailures")
    void shouldAttemptDestructionForAnotherKeyInstanceAfterProviderFailure(Exception destructionFailure) throws Exception {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey failingPrivateKey = mock(ProviderPrivateKey.class);
        PrivateKey nextPrivateKey = mock(ProviderPrivateKey.class);
        PublicKey publicKey = mock(PublicKey.class);
        byte[] sharedSecret = {1, 2, 3};
        assertThat(failingPrivateKey.getClass(), sameInstance(nextPrivateKey.getClass()));
        when(group.generateKeyPair(any(SecureRandom.class)))
                .thenReturn(new KeyPair(publicKey, failingPrivateKey), new KeyPair(publicKey, nextPrivateKey));
        when(group.deriveSharedSecret(any(PrivateKey.class), any(byte[].class))).thenReturn(sharedSecret);
        doThrow(destructionFailure).when(failingPrivateKey).destroy();
        QuicTlsKeySharePossession failing = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeySharePossession next = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(group, new byte[] {4, 5, 6});

        assertThat(failing.sharedSecret(peer), sameInstance(sharedSecret));
        assertThat(next.sharedSecret(peer), sameInstance(sharedSecret));

        verify(failingPrivateKey).destroy();
        verify(nextPrivateKey).destroy();
    }

    @Test
    void shouldInvokeProviderDestructionInheritedFromSuperclass() {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        DestroyablePrivateKey privateKey = new InheritedDestructionPrivateKey();
        byte[] sharedSecret = {1, 2, 3};
        when(group.generateKeyPair(any(SecureRandom.class)))
                .thenReturn(new KeyPair(mock(PublicKey.class), privateKey));
        when(group.deriveSharedSecret(eq(privateKey), any(byte[].class))).thenReturn(sharedSecret);
        QuicTlsKeySharePossession possession = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(group, new byte[] {4, 5, 6});

        assertThat(possession.sharedSecret(peer), sameInstance(sharedSecret));
        possession.discard();

        assertThat(privateKey.destroyCalls, is(1));
        assertThrows(IllegalStateException.class, () -> possession.sharedSecret(peer));
    }

    @Test
    void shouldConsumePrivateKeyWithDefaultUnsupportedDestruction() {
        QuicTlsNamedGroup group = mock(QuicTlsNamedGroup.class);
        PrivateKey privateKey = new UndestroyablePrivateKey();
        byte[] sharedSecret = {1, 2, 3};
        when(group.generateKeyPair(any(SecureRandom.class)))
                .thenReturn(new KeyPair(mock(PublicKey.class), privateKey));
        when(group.deriveSharedSecret(eq(privateKey), any(byte[].class))).thenReturn(sharedSecret);
        QuicTlsKeySharePossession possession = QuicTlsKeySharePossession.create(group, new SecureRandom());
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(group, new byte[] {4, 5, 6});

        assertThat(possession.sharedSecret(peer), sameInstance(sharedSecret));
        possession.discard();

        assertThrows(IllegalStateException.class, () -> possession.sharedSecret(peer));
        assertThrows(DestroyFailedException.class, privateKey::destroy);
    }

    private static Stream<Exception> destructionFailures() {
        return Stream.of(new DestroyFailedException("Provider does not support destruction"),
                         new ProviderException("Provider destruction failure"),
                         new UnsupportedOperationException("Provider does not support destruction"));
    }

    interface ProviderPrivateKey extends PrivateKey {
        @Override
        void destroy() throws DestroyFailedException;
    }

    private static class UndestroyablePrivateKey implements PrivateKey {
        @Override
        public String getAlgorithm() {
            return "Test";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[] {1, 2, 3};
        }
    }

    private static class DestroyablePrivateKey extends UndestroyablePrivateKey {
        private int destroyCalls;

        @Override
        public void destroy() {
            destroyCalls++;
        }
    }

    private static final class InheritedDestructionPrivateKey extends DestroyablePrivateKey {
    }
}
