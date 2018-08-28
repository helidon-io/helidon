/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.tools.config;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Encryption utility test.
 */
public class CryptUtilTest {
    private static final String TEST_SECRET = "Jaja uzh :( berglengele";
    private static final char[] MASTER_PASSWORD = "myComplicatePassword".toCharArray();

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    @BeforeAll
    public static void staticInit() {
        KeyConfig kc = KeyConfig.keystoreBuilder()
                .keystore(Resource.from(".ssh/keystore.p12"))
                .keystorePassphrase("j4c".toCharArray())
                .keyAlias("1")
                .certAlias("1")
                .build();

        privateKey = kc.getPrivateKey().orElseThrow(AssertionError::new);
        publicKey = kc.getPublicKey().orElseThrow(AssertionError::new);
    }

    @Test
    public void testEncryptAndDecryptRsaPrivate() {
        testPki(privateKey, publicKey, false);
    }

    @Test
    public void testEncryptAndDecryptRsaPublic() {
        testPki(publicKey, privateKey, true);
    }

    @Test
    public void testEncryptWrongKey() throws NoSuchAlgorithmException {
        PrivateKey privateKey = generateDsaPrivateKey();
        try {
            CryptUtil.encryptRsa(privateKey, TEST_SECRET);
            fail("We have fed DSA private key to RSA decryption. This should have failed");
        } catch (SecureConfigException e) {
            Throwable cause = e.getCause();
            //our message
            assertEquals("Failed to encrypt using RSA key", e.getMessage());
            assertSame(InvalidKeyException.class, cause.getClass());
        }
    }

    private PrivateKey generateDsaPrivateKey() throws NoSuchAlgorithmException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
        gen.initialize(1024);
        KeyPair keyPair = gen.generateKeyPair();
        return keyPair.getPrivate();
    }

    private void testPki(Key encryptionKey, Key decryptionKey, boolean mustBeSeeded) {
        String encryptedBase64 = CryptUtil.encryptRsa(encryptionKey, TEST_SECRET);
        String decrypted = CryptUtil.decryptRsa(decryptionKey, encryptedBase64);

        assertEquals(TEST_SECRET, decrypted);

        String encryptedAgain = CryptUtil.encryptRsa(encryptionKey, TEST_SECRET);

        if (mustBeSeeded) {
            assertNotEquals(encryptedBase64, encryptedAgain);
        }

        decrypted = CryptUtil.decryptRsa(decryptionKey, encryptedAgain);

        assertEquals(TEST_SECRET, decrypted);
    }

    @Test
    public void testEncryptedRsaPrivate() {
        assertThrows(SecureConfigException.class, () -> testPki(privateKey, privateKey, false));
    }

    @Test
    public void testEncryptedRsaPublic() {
        assertThrows(SecureConfigException.class, () -> testPki(publicKey, publicKey, true));
    }

    @Test
    public void testEncryptAndDecryptAes() {
        String encryptedBase64 = CryptUtil.encryptAes(MASTER_PASSWORD, TEST_SECRET);
        String decrypted = CryptUtil.decryptAes(MASTER_PASSWORD, encryptedBase64);

        assertEquals(TEST_SECRET, decrypted);

        String encryptedAgain = CryptUtil.encryptAes(MASTER_PASSWORD, TEST_SECRET);
        assertNotEquals(encryptedBase64, encryptedAgain);
        decrypted = CryptUtil.decryptAes(MASTER_PASSWORD, encryptedAgain);

        assertEquals(TEST_SECRET, decrypted);
    }

    @Test
    public void testEncryptedAes() {
        String encryptedBase64 = CryptUtil.encryptAes(MASTER_PASSWORD, TEST_SECRET);

        String encryptedString = new String(Base64.getDecoder().decode(encryptedBase64), StandardCharsets.UTF_8);
        //must not be just base64 encoded
        assertNotEquals(TEST_SECRET, encryptedString);

        try {
            String decrypted = CryptUtil.decryptAes("anotherPassword".toCharArray(), encryptedBase64);
            assertThat(decrypted, is(not(TEST_SECRET)));
        } catch (Exception e) {
            //this is OK
        }
    }
}
