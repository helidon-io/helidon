/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.Base64;

import io.helidon.common.Base64Value;
import io.helidon.common.configurable.Resource;
import io.helidon.common.crypto.CryptoException;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.common.pki.KeyConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertThat(e.getMessage(), is("Failed to encrypt using RSA key"));
            assertThat(cause.getClass(), sameInstance(CryptoException.class));
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

        assertThat(decrypted, is(TEST_SECRET));

        String encryptedAgain = EncryptionUtil.encryptRsa(encryptionKey, TEST_SECRET);

        if (mustBeSeeded) {
            assertThat(encryptedAgain, is(not((encryptedBase64))));
        }

        decrypted = EncryptionUtil.decryptRsa(decryptionKey, encryptedAgain);

        assertThat(decrypted, is(TEST_SECRET));
    }

    @Test
    public void testEncryptAndDecryptAes() {
        String encryptedBase64 = EncryptionUtil.encryptAes(MASTER_PASSWORD, TEST_SECRET);
        String decrypted = EncryptionUtil.decryptAes(MASTER_PASSWORD, encryptedBase64);

        assertThat(decrypted, is(TEST_SECRET));

        String encryptedAgain = EncryptionUtil.encryptAes(MASTER_PASSWORD, TEST_SECRET);
        assertThat(encryptedAgain, is(not(encryptedBase64)));
        decrypted = EncryptionUtil.decryptAes(MASTER_PASSWORD, encryptedAgain);

        assertThat(decrypted, is(TEST_SECRET));
    }

    @Test
    public void testEncryptAndDecryptAesEnvelope() {
        String encryptedBase64 = EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, TEST_SECRET);
        String decrypted = EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD, encryptedBase64);

        assertThat(decrypted, is(TEST_SECRET));

        EncryptionUtil.AesEnvelope envelope = EncryptionUtil.decodeAesEnvelope(encryptedBase64);
        assertThat(envelope.version(), is(1));
        assertThat(envelope.iterations(), is(EncryptionUtil.ENVELOPE_HASH_ITERATIONS));

        String encryptedAgain = EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, TEST_SECRET);
        assertThat(encryptedAgain, is(not(encryptedBase64)));
        assertThat(EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD, encryptedAgain), is(TEST_SECRET));
    }

    @Test
    public void testEncryptAndDecryptAesEnvelopeEmptySecret() {
        String encryptedBase64 = EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, "");

        assertThat(EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD, encryptedBase64), is(""));
    }

    @Test
    public void testAesEnvelopeSymmetricCipherBytes() {
        String encryptedBase64 = EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, TEST_SECRET);
        EncryptionUtil.AesEnvelope envelope = EncryptionUtil.decodeAesEnvelope(encryptedBase64);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedBase64);
        byte[] header = new byte[Byte.BYTES + Integer.BYTES];
        byte[] encrypted = new byte[decodedBytes.length - header.length];
        System.arraycopy(decodedBytes, 0, header, 0, header.length);
        System.arraycopy(decodedBytes, header.length, encrypted, 0, encrypted.length);

        assertArrayEquals(envelope.header(), header);
        assertArrayEquals(envelope.encrypted(), encrypted);

        assertThat(Byte.toUnsignedInt(envelope.header()[0]), is(1));
        assertThat(readInt32(envelope.header(), Byte.BYTES), is(EncryptionUtil.ENVELOPE_HASH_ITERATIONS));

        SymmetricCipher cipher = SymmetricCipher.builder()
                .password(MASTER_PASSWORD)
                .numberOfIterations(envelope.iterations())
                .keySize(256)
                .additionalAuthenticatedData(envelope.header())
                .build();

        assertThat(cipher.decrypt(Base64Value.create(envelope.encrypted())).toDecodedString(), is(TEST_SECRET));
    }

    @Test
    public void testAesEnvelopeRequiresAuthenticatedHeader() {
        String encryptedBase64 = EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, TEST_SECRET);
        EncryptionUtil.AesEnvelope envelope = EncryptionUtil.decodeAesEnvelope(encryptedBase64);
        SymmetricCipher cipher = SymmetricCipher.builder()
                .password(MASTER_PASSWORD)
                .numberOfIterations(envelope.iterations())
                .keySize(256)
                .build();

        CryptoException exception = assertThrows(CryptoException.class,
                                                () -> cipher.decrypt(Base64Value.create(envelope.encrypted())));
        assertThat(exception.getMessage(), is("Failed to decrypt the message"));
    }

    @Test
    public void testAesEnvelopeTamperingFails() {
        String encryptedBase64 = EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, TEST_SECRET);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedBase64);

        byte[] tamperedIterations = decodedBytes.clone();
        tamperedIterations[4] ^= 1;
        assertThrows(ConfigEncryptionException.class,
                     () -> EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD,
                                                             Base64.getEncoder().encodeToString(tamperedIterations)));

        byte[] tamperedEncrypted = decodedBytes.clone();
        tamperedEncrypted[Byte.BYTES + Integer.BYTES] ^= 1;
        assertThrows(ConfigEncryptionException.class,
                     () -> EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD,
                                                             Base64.getEncoder().encodeToString(tamperedEncrypted)));

        byte[] tamperedCiphertext = decodedBytes.clone();
        tamperedCiphertext[tamperedCiphertext.length - 1] ^= 1;
        assertThrows(ConfigEncryptionException.class,
                     () -> EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD,
                                                             Base64.getEncoder().encodeToString(tamperedCiphertext)));
    }

    @Test
    public void testAesEnvelopeRejectsInvalidInput() {
        assertThrows(ConfigEncryptionException.class,
                     () -> EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD, "not really base64"));

        assertThrows(ConfigEncryptionException.class,
                     () -> EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD,
                                                             Base64.getEncoder().encodeToString(new byte[8])));

        String encryptedBase64 = EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, TEST_SECRET);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedBase64);

        byte[] unsupportedVersion = decodedBytes.clone();
        unsupportedVersion[0] = 2;
        assertThrows(ConfigEncryptionException.class,
                     () -> EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD,
                                                             Base64.getEncoder().encodeToString(unsupportedVersion)));

        byte[] tooFewIterations = decodedBytes.clone();
        Arrays.fill(tooFewIterations, 1, 5, (byte) 0);
        tooFewIterations[4] = 1;
        assertThrows(ConfigEncryptionException.class,
                     () -> EncryptionUtil.decryptAesEnvelope(MASTER_PASSWORD,
                                                             Base64.getEncoder().encodeToString(tooFewIterations)));

        assertThrows(ConfigEncryptionException.class,
                     () -> EncryptionUtil.decryptAesEnvelope("anotherPassword".toCharArray(), encryptedBase64));
    }

    @Test
    public void testEncryptedAes() {
        String encryptedBase64 = EncryptionUtil.encryptAes(MASTER_PASSWORD, TEST_SECRET);

        String encryptedString = new String(Base64.getDecoder().decode(encryptedBase64), StandardCharsets.UTF_8);
        //must not be just base64 encoded
        assertThat(encryptedString, is(not(TEST_SECRET)));

        try {
            String decrypted = EncryptionUtil.decryptAes("anotherPassword".toCharArray(), encryptedBase64);
            assertThat(decrypted, is(not(TEST_SECRET)));
        } catch (Exception e) {
            //this is OK
        }
    }

    private static int readInt32(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}
