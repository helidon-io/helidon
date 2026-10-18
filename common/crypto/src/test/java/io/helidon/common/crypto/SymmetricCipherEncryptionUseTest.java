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

package io.helidon.common.crypto;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.crypto.IllegalBlockSizeException;

import io.helidon.common.Base64Value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SymmetricCipherEncryptionUseTest {
    private static final Base64Value MESSAGE = Base64Value.create("raw key message");
    private static final String REUSED_MESSAGE = "Raw-key cipher can only be used for one encryption attempt";
    private static final String ECB = "AES/ECB/PKCS5Padding";

    @ParameterizedTest
    @MethodSource("algorithms")
    void testOneEncryptionAndRepeatableDecryption(String algorithm) {
        SymmetricCipher cipher = rawCipher(algorithm);
        assertThrows(NullPointerException.class, () -> cipher.encrypt(null));

        Base64Value encrypted = cipher.encrypt(MESSAGE);
        assertThat(cipher.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
        assertThat(cipher.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
        CryptoException repeated = assertThrows(CryptoException.class, () -> cipher.encrypt(MESSAGE));
        assertThat(repeated.getMessage(), is(REUSED_MESSAGE));
        CryptoException different = assertThrows(CryptoException.class,
                                                () -> cipher.encrypt(Base64Value.create("another message")));
        assertThat(different.getMessage(), is(REUSED_MESSAGE));
        assertThat(cipher.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));

        SymmetricCipher decryptFirst = rawCipher(algorithm);
        assertThat(decryptFirst.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
        assertThat(decryptFirst.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
        assertThat(decryptFirst.encrypt(MESSAGE).toBytes(), is(encrypted.toBytes()));
    }

    @Test
    void testFixedIvGcmRejectsSecondMessage() {
        byte[] known = "attacker-chosen!".getBytes(StandardCharsets.US_ASCII);
        byte[] victim = "sensitive-secret".getBytes(StandardCharsets.US_ASCII);
        SymmetricCipher cipher = SymmetricCipher.builder()
                .algorithm(SymmetricCipher.ALGORITHM_AES_GCM)
                .key(new byte[16])
                .iv(new byte[12])
                .build();

        Base64Value knownCiphertext = cipher.encrypt(Base64Value.create(known));
        assertThat(cipher.decrypt(knownCiphertext).toBytes(), is(known));
        // Reusing this key and IV would expose the victim through XOR with the known plaintext and ciphertext.
        CryptoException failure = assertThrows(CryptoException.class, () -> cipher.encrypt(Base64Value.create(victim)));
        assertThat(failure.getMessage(), is(REUSED_MESSAGE));
    }

    @Test
    void testFailedEncryptionConsumesAttempt() {
        SymmetricCipher cipher = SymmetricCipher.builder()
                .algorithm("AES/CBC/NoPadding")
                .key(new byte[16])
                .iv(new byte[16])
                .build();

        CryptoException invalidLength = assertThrows(CryptoException.class,
                                                    () -> cipher.encrypt(Base64Value.create(new byte[15])));
        assertThat(invalidLength.getCause(), instanceOf(IllegalBlockSizeException.class));
        CryptoException consumed = assertThrows(CryptoException.class,
                                               () -> cipher.encrypt(Base64Value.create(new byte[16])));
        assertThat(consumed.getMessage(), is(REUSED_MESSAGE));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void testConcurrentEncryptionAllowsExactlyOneAttempt(String algorithm) throws Exception {
        int taskCount = 16;
        SymmetricCipher cipher = rawCipher(algorithm);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat("Concurrent encryption start", start.await(10, TimeUnit.SECONDS), is(true));
                    Base64Value encrypted;
                    try {
                        encrypted = cipher.encrypt(MESSAGE);
                    } catch (CryptoException e) {
                        assertThat(e.getMessage(), is(REUSED_MESSAGE));
                        return false;
                    }
                    assertThat(cipher.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
                    return true;
                }));
            }
            try {
                assertThat("All encryption tasks ready", ready.await(10, TimeUnit.SECONDS), is(true));
            } finally {
                start.countDown();
            }
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(algorithm + " successful encryptions", successes, is(1));
        }
    }

    @Test
    void testPasswordCipherRemainsReusable() {
        SymmetricCipher cipher = SymmetricCipher.builder().password("password".toCharArray()).build();
        Base64Value first = cipher.encrypt(MESSAGE);
        Base64Value secondMessage = Base64Value.create("another message");
        Base64Value second = cipher.encrypt(secondMessage);
        Base64Value third = cipher.encrypt(MESSAGE);

        assertThat(third.toBytes(), is(not(first.toBytes())));
        assertThat(cipher.decrypt(first).toBytes(), is(MESSAGE.toBytes()));
        assertThat(cipher.decrypt(second).toBytes(), is(secondMessage.toBytes()));
        assertThat(cipher.decrypt(third).toBytes(), is(MESSAGE.toBytes()));
        assertThat(cipher.decrypt(first).toBytes(), is(MESSAGE.toBytes()));
    }

    private static Stream<String> algorithms() {
        return Stream.of(SymmetricCipher.ALGORITHM_AES_CBC,
                         SymmetricCipher.ALGORITHM_AES_CTR,
                         SymmetricCipher.ALGORITHM_AES_GCM,
                         SymmetricCipher.ALGORITHM_CHA_CHA,
                         SymmetricCipher.ALGORITHM_CHA_CHA_POLY1305,
                         ECB);
    }

    private static SymmetricCipher rawCipher(String algorithm) {
        SymmetricCipher.Builder builder = SymmetricCipher.builder().algorithm(algorithm).key(new byte[32]);
        if (!algorithm.equals(ECB)) {
            int ivLength = algorithm.equals(SymmetricCipher.ALGORITHM_AES_CBC)
                    || algorithm.equals(SymmetricCipher.ALGORITHM_AES_CTR) ? 16 : 12;
            builder.iv(new byte[ivLength]);
        }
        return builder.build();
    }
}
