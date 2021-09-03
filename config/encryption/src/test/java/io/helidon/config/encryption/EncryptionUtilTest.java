/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.encryption;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import io.helidon.common.configurable.Resource;
import io.helidon.common.crypto.CryptoException;
import io.helidon.common.pki.KeyConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Encryption utility test.
 */
public class EncryptionUtilTest {
    private static final String TEST_SECRET = "Jaja uzh :( berglengele";
    private static final char[] MASTER_PASSWORD = "myComplicatePassword".toCharArray();

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    @BeforeAll
    public static void staticInit() {
        KeyConfig kc = KeyConfig.keystoreBuilder()
                .keystore(Resource.create(".ssh/keystore.p12"))
                .keystorePassphrase("j4c".toCharArray())
                .keyAlias("1")
                .certAlias("1")
                .build();

        privateKey = kc.privateKey().orElseThrow(AssertionError::new);
        publicKey = kc.publicKey().orElseThrow(AssertionError::new);
    }

    @Test
    public void testEncryptAndDecryptRsaPublic() {
        testPki(publicKey, privateKey, true);
    }

    @Test
    public void testEncryptWrongKey() throws NoSuchAlgorithmException {
        PublicKey publicKey = generateDsaPublicKey();
        try {
            EncryptionUtil.encryptRsa(publicKey, TEST_SECRET);
            fail("We have fed DSA private key to RSA decryption. This should have failed");
        } catch (ConfigEncryptionException e) {
            Throwable cause = e.getCause();
            //our message
            assertEquals("Failed to encrypt using RSA key", e.getMessage());
            assertSame(CryptoException.class, cause.getClass());
        }
    }

    private PublicKey generateDsaPublicKey() throws NoSuchAlgorithmException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
        gen.initialize(1024);
        KeyPair keyPair = gen.generateKeyPair();
        return keyPair.getPublic();
    }

    private void testPki(PublicKey encryptionKey, PrivateKey decryptionKey, boolean mustBeSeeded) {
        String encryptedBase64 = EncryptionUtil.encryptRsa(encryptionKey, TEST_SECRET);
        String decrypted = EncryptionUtil.decryptRsa(decryptionKey, encryptedBase64);

        assertEquals(TEST_SECRET, decrypted);

        String encryptedAgain = EncryptionUtil.encryptRsa(encryptionKey, TEST_SECRET);

        if (mustBeSeeded) {
            assertNotEquals(encryptedBase64, encryptedAgain);
        }

        decrypted = EncryptionUtil.decryptRsa(decryptionKey, encryptedAgain);

        assertEquals(TEST_SECRET, decrypted);
    }

    @Test
    public void testEncryptAndDecryptAes() {
        String encryptedBase64 = EncryptionUtil.encryptAes(MASTER_PASSWORD, TEST_SECRET);
        String decrypted = EncryptionUtil.decryptAes(MASTER_PASSWORD, encryptedBase64);

        assertEquals(TEST_SECRET, decrypted);

        String encryptedAgain = EncryptionUtil.encryptAes(MASTER_PASSWORD, TEST_SECRET);
        assertNotEquals(encryptedBase64, encryptedAgain);
        decrypted = EncryptionUtil.decryptAes(MASTER_PASSWORD, encryptedAgain);

        assertEquals(TEST_SECRET, decrypted);
    }

    @Test
    public void testEncryptedAes() {
        String encryptedBase64 = EncryptionUtil.encryptAes(MASTER_PASSWORD, TEST_SECRET);

        String encryptedString = new String(Base64.getDecoder().decode(encryptedBase64), StandardCharsets.UTF_8);
        //must not be just base64 encoded
        assertNotEquals(TEST_SECRET, encryptedString);

        try {
            String decrypted = EncryptionUtil.decryptAes("anotherPassword".toCharArray(), encryptedBase64);
            assertThat(decrypted, is(not(TEST_SECRET)));
        } catch (Exception e) {
            //this is OK
        }
    }
}
