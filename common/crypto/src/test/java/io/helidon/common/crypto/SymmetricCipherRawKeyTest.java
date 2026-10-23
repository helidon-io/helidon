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
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.Base64Value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SymmetricCipherRawKeyTest {
    private static final Base64Value MESSAGE = Base64Value.create("raw key message");

    @ParameterizedTest
    @MethodSource("algorithms")
    @SuppressWarnings("removal")
    void testLegacyAndBuilderInteroperate(String algorithm) {
        byte[] key = key();
        byte[] iv = iv(algorithm);
        AlgorithmParameterSpec parameters = parameters(algorithm, iv);
        SymmetricCipher fromIv = SymmetricCipher.builder().algorithm(algorithm).key(key).iv(iv).build();
        SymmetricCipher fromParameters = SymmetricCipher.builder()
                .algorithm(algorithm).key(key).parameters(parameters).build();

        Base64Value legacyIv = SymmetricCipher.encrypt(algorithm, null, key, iv, MESSAGE);
        Base64Value legacyParameters = SymmetricCipher.encrypt(algorithm, null, key, parameters, MESSAGE);
        Base64Value builtIv = fromIv.encrypt(MESSAGE);
        Base64Value builtParameters = fromParameters.encrypt(MESSAGE);

        assertThat(algorithm + " IV ciphertext", builtIv.toBytes(), is(legacyIv.toBytes()));
        assertThat(algorithm + " parameter ciphertext", builtParameters.toBytes(), is(legacyParameters.toBytes()));
        assertThat(fromIv.decrypt(legacyParameters).toBytes(), is(MESSAGE.toBytes()));
        assertThat(fromParameters.decrypt(legacyIv).toBytes(), is(MESSAGE.toBytes()));
        assertThat(SymmetricCipher.decrypt(algorithm, null, key, iv, builtParameters).toBytes(), is(MESSAGE.toBytes()));
        assertThat(SymmetricCipher.decrypt(algorithm, null, key, parameters, builtIv).toBytes(), is(MESSAGE.toBytes()));
        assertThat(SymmetricCipher.encrypt(algorithm, key, iv, MESSAGE).toBytes(), is(builtIv.toBytes()));
        assertThat(SymmetricCipher.decrypt(algorithm, key, iv, builtIv).toBytes(), is(MESSAGE.toBytes()));
    }

    @Test
    @SuppressWarnings("removal")
    void testExplicitProviderAndClearProvider() throws Exception {
        String algorithm = SymmetricCipher.ALGORITHM_AES_GCM;
        String provider = Cipher.getInstance(algorithm).getProvider().getName();
        byte[] key = key();
        byte[] iv = iv(algorithm);
        SymmetricCipher.Builder builder = SymmetricCipher.builder()
                .algorithm(algorithm).key(key).iv(iv).provider(provider);
        SymmetricCipher explicit = builder.build();
        Base64Value encrypted = explicit.encrypt(MESSAGE);
        AlgorithmParameterSpec parameters = parameters(algorithm, iv);
        SymmetricCipher explicitParameters = SymmetricCipher.builder()
                .algorithm(algorithm).key(key).parameters(parameters).provider(provider).build();

        assertThat(encrypted.toBytes(), is(SymmetricCipher.encrypt(algorithm, provider, key, iv, MESSAGE).toBytes()));
        assertThat(explicit.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
        assertThat(SymmetricCipher.decrypt(algorithm, provider, key, iv, encrypted).toBytes(), is(MESSAGE.toBytes()));
        assertThat(explicitParameters.encrypt(MESSAGE).toBytes(),
                   is(SymmetricCipher.encrypt(algorithm, provider, key, parameters, MESSAGE).toBytes()));
        assertThat(SymmetricCipher.decrypt(algorithm, provider, key, parameters, encrypted).toBytes(),
                   is(MESSAGE.toBytes()));
        assertThat(explicitParameters.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));

        builder.provider("HelidonMissingRawKeyTestProvider");
        assertThrows(CryptoException.class, builder::build);
        SymmetricCipher cleared = builder.clearProvider().build();
        assertThat(cleared.encrypt(MESSAGE).toBytes(), is(encrypted.toBytes()));
        assertThat(cleared.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
    }

    @Test
    @SuppressWarnings("removal")
    void testCustomGcmTagLengthAndChaChaCounter() {
        String gcm = SymmetricCipher.ALGORITHM_AES_GCM;
        String chaCha = SymmetricCipher.ALGORITHM_CHA_CHA;
        AlgorithmParameterSpec gcmParameters = new GCMParameterSpec(96, iv(gcm));
        AlgorithmParameterSpec chaChaParameters = new ChaCha20ParameterSpec(iv(chaCha), 7);

        for (String algorithm : new String[] {gcm, chaCha}) {
            AlgorithmParameterSpec parameters = algorithm.equals(gcm) ? gcmParameters : chaChaParameters;
            SymmetricCipher cipher = SymmetricCipher.builder()
                    .algorithm(algorithm).key(key()).parameters(parameters).build();
            Base64Value encrypted = cipher.encrypt(MESSAGE);
            assertThat(algorithm, encrypted.toBytes(),
                       is(SymmetricCipher.encrypt(algorithm, null, key(), parameters, MESSAGE).toBytes()));
            assertThat(cipher.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
            assertThat(SymmetricCipher.decrypt(algorithm, null, key(), parameters, encrypted).toBytes(),
                       is(MESSAGE.toBytes()));
            if (algorithm.equals(gcm)) {
                assertThat(encrypted.toBytes().length, is(MESSAGE.toBytes().length + 12));
            }
        }
    }

    @Test
    @SuppressWarnings("removal")
    void testParameterlessEncryptionAndDecryption() throws Exception {
        String algorithm = "AES/ECB/PKCS5Padding";
        byte[] key = key();
        Cipher jca = Cipher.getInstance(algorithm);
        jca.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        Base64Value encrypted = Base64Value.create(jca.doFinal(MESSAGE.toBytes()));
        SymmetricCipher cipher = SymmetricCipher.builder().algorithm(algorithm).key(key).build();

        assertThat(SymmetricCipher.decrypt(algorithm, null, key, (AlgorithmParameterSpec) null, encrypted).toBytes(),
                   is(MESSAGE.toBytes()));
        assertThat(cipher.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
        Base64Value builtEncrypted = cipher.encrypt(MESSAGE);
        assertThat(builtEncrypted.toBytes(), is(encrypted.toBytes()));
        assertThat(cipher.decrypt(builtEncrypted).toBytes(), is(MESSAGE.toBytes()));
        assertThrows(NullPointerException.class,
                     () -> SymmetricCipher.encrypt(algorithm, null, key, (AlgorithmParameterSpec) null, MESSAGE));
    }

    @Test
    void testInvalidRawKeyConfigurationFailsDuringBuild() {
        assertThrows(CryptoException.class, () -> SymmetricCipher.builder().key(key()).build(),
                     "GCM requires explicit parameters for both operations");
        assertThrows(CryptoException.class,
                     () -> SymmetricCipher.builder().algorithm("HelidonMissingCipher").key(key()).build(),
                     "Unknown algorithm");
        assertThrows(CryptoException.class,
                     () -> SymmetricCipher.builder().key(new byte[15]).iv(iv(SymmetricCipher.ALGORITHM_AES_GCM)).build(),
                     "Invalid AES key size");
        assertThrows(CryptoException.class,
                     () -> SymmetricCipher.builder().algorithm(SymmetricCipher.ALGORITHM_AES_CBC)
                             .key(key()).iv(new byte[12]).build(),
                     "CBC requires a block-sized IV");
        assertThrows(CryptoException.class,
                     () -> SymmetricCipher.builder().key(key()).parameters(new IvParameterSpec(new byte[12])).build(),
                     "GCM requires GCM parameters");
        assertThrows(CryptoException.class,
                     () -> SymmetricCipher.builder().algorithm(SymmetricCipher.ALGORITHM_AES_CBC)
                             .key(key()).iv(iv(SymmetricCipher.ALGORITHM_AES_CBC))
                             .additionalAuthenticatedData(new byte[] {1}).build(),
                     "CBC does not support additional authenticated data");
    }

    @Test
    @SuppressWarnings("removal")
    void testLegacyInvalidConfigurationFailsDuringStaticCall() {
        assertThrows(CryptoException.class,
                     () -> SymmetricCipher.encrypt(SymmetricCipher.ALGORITHM_AES_GCM,
                                                   "HelidonMissingRawKeyTestProvider", key(),
                                                   iv(SymmetricCipher.ALGORITHM_AES_GCM), MESSAGE));
        assertThrows(CryptoException.class,
                     () -> SymmetricCipher.decrypt(SymmetricCipher.ALGORITHM_AES_GCM,
                                                   "HelidonMissingRawKeyTestProvider", key(),
                                                   iv(SymmetricCipher.ALGORITHM_AES_GCM), MESSAGE));
    }

    @Test
    void testInputDependentErrorsRemainOperationFailures() {
        SymmetricCipher noPadding = SymmetricCipher.builder().algorithm("AES/CBC/NoPadding")
                .key(key()).iv(iv(SymmetricCipher.ALGORITHM_AES_CBC)).build();
        Base64Value block = Base64Value.create(new byte[16]);
        assertThat(noPadding.decrypt(noPadding.encrypt(block)).toBytes(), is(block.toBytes()));
        SymmetricCipher invalidLength = SymmetricCipher.builder().algorithm("AES/CBC/NoPadding")
                .key(key()).iv(iv(SymmetricCipher.ALGORITHM_AES_CBC)).build();
        assertThrows(CryptoException.class, () -> invalidLength.encrypt(Base64Value.create(new byte[15])));
        assertThrows(CryptoException.class, () -> noPadding.decrypt(Base64Value.create(new byte[15])));

        SymmetricCipher authenticated = SymmetricCipher.builder()
                .key(key()).iv(iv(SymmetricCipher.ALGORITHM_AES_GCM)).build();
        byte[] tampered = authenticated.encrypt(MESSAGE).toBytes();
        tampered[0] ^= 1;
        assertThrows(CryptoException.class, () -> authenticated.decrypt(Base64Value.create(tampered)));
    }

    @Test
    void testModeValidationAndClearTransitions() {
        SymmetricCipher.Builder builder = SymmetricCipher.builder();
        assertThat(builder.key(), is(Optional.empty()));
        assertThat(builder.iv(), is(Optional.empty()));
        assertThat(builder.parameters(), is(Optional.empty()));
        assertThrows(CryptoException.class, builder::build);

        builder.password("password".toCharArray()).key(key());
        assertThrows(CryptoException.class, builder::build);
        builder.clearPassword().iv(iv(SymmetricCipher.ALGORITHM_AES_GCM));
        builder.build();
        builder.parameters(new GCMParameterSpec(128, iv(SymmetricCipher.ALGORITHM_AES_GCM)));
        assertThrows(CryptoException.class, builder::build);
        builder.clearIv().build();
        assertThat(builder.iv(), is(Optional.empty()));
        builder.clearKey().password("password".toCharArray());
        assertThat(builder.key(), is(Optional.empty()));
        assertThrows(CryptoException.class, builder::build);
        builder.clearParameters().build();
        assertThat(builder.parameters(), is(Optional.empty()));
        builder.iv(iv(SymmetricCipher.ALGORITHM_AES_GCM));
        assertThrows(CryptoException.class, builder::build);
        builder.clearIv().clearPassword();
        assertThat(builder.password(), is(Optional.empty()));
        assertThrows(CryptoException.class, builder::build);
    }

    @Test
    void testIvUsesFinalAlgorithm() {
        String algorithm = SymmetricCipher.ALGORITHM_AES_CBC;
        byte[] iv = iv(algorithm);
        SymmetricCipher cipher = SymmetricCipher.builder().key(key()).iv(iv).algorithm(algorithm).build();

        assertThat(cipher.encrypt(MESSAGE).toBytes(), is(SymmetricCipher.encrypt(algorithm, key(), iv, MESSAGE).toBytes()));
    }

    @Test
    void testArrayCopiesAndNullSettersPreserveConfiguration() {
        String algorithm = SymmetricCipher.ALGORITHM_AES_GCM;
        byte[] key = key();
        byte[] iv = iv(algorithm);
        SymmetricCipher.Builder builder = SymmetricCipher.builder().key(key).iv(iv);
        SymmetricCipher original = builder.build();
        Base64Value expected = SymmetricCipher.encrypt(algorithm, key(), iv(algorithm), MESSAGE);

        key[0]++;
        iv[0]++;
        builder.key().orElseThrow()[0]++;
        builder.iv().orElseThrow()[0]++;
        assertThat(builder.key().orElseThrow(), is(key()));
        assertThat(builder.iv().orElseThrow(), is(iv(algorithm)));
        assertThrows(NullPointerException.class, () -> builder.key(null));
        assertThrows(NullPointerException.class, () -> builder.iv(null));
        assertThat(builder.build().encrypt(MESSAGE).toBytes(), is(expected.toBytes()));

        builder.key(new byte[32]).iv(new byte[12]);
        assertThat(original.encrypt(MESSAGE).toBytes(), is(expected.toBytes()));
        assertThat(original.decrypt(expected).toBytes(), is(MESSAGE.toBytes()));
        AlgorithmParameterSpec parameters = new GCMParameterSpec(96, iv(algorithm));
        builder.clearIv().parameters(parameters);
        assertThrows(NullPointerException.class, () -> builder.parameters(null));
        assertThat(builder.parameters().orElseThrow(), is(parameters));
    }

    @Test
    void testAdditionalAuthenticatedDataInteroperatesWithJca() throws Exception {
        String algorithm = SymmetricCipher.ALGORITHM_AES_GCM;
        byte[] aad = "authenticated header".getBytes(StandardCharsets.UTF_8);
        byte[] key = key();
        GCMParameterSpec parameters = new GCMParameterSpec(128, iv(algorithm));
        SymmetricCipher cipher = SymmetricCipher.builder()
                .key(key).parameters(parameters).additionalAuthenticatedData(aad).build();
        Cipher jca = Cipher.getInstance(algorithm);
        jca.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), parameters);
        jca.updateAAD(aad);
        Base64Value encrypted = Base64Value.create(jca.doFinal(MESSAGE.toBytes()));

        Base64Value builtEncrypted = cipher.encrypt(MESSAGE);
        assertThat(builtEncrypted.toBytes(), is(encrypted.toBytes()));
        assertThat(cipher.decrypt(encrypted).toBytes(), is(MESSAGE.toBytes()));
        jca.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), parameters);
        jca.updateAAD(aad);
        assertThat(jca.doFinal(builtEncrypted.toBytes()), is(MESSAGE.toBytes()));
        SymmetricCipher mismatched = SymmetricCipher.builder()
                .key(key).parameters(parameters).additionalAuthenticatedData(new byte[] {1}).build();
        assertThrows(CryptoException.class, () -> mismatched.decrypt(encrypted));
    }

    private static Stream<String> algorithms() {
        return Stream.of(SymmetricCipher.ALGORITHM_AES_CBC,
                         SymmetricCipher.ALGORITHM_AES_CTR,
                         SymmetricCipher.ALGORITHM_AES_GCM,
                         SymmetricCipher.ALGORITHM_CHA_CHA,
                         SymmetricCipher.ALGORITHM_CHA_CHA_POLY1305);
    }

    private static byte[] key() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 13);
        return key;
    }

    private static byte[] iv(String algorithm) {
        byte[] iv = new byte[algorithm.equals(SymmetricCipher.ALGORITHM_AES_CBC)
                || algorithm.equals(SymmetricCipher.ALGORITHM_AES_CTR) ? 16 : 12];
        Arrays.fill(iv, (byte) 7);
        return iv;
    }

    private static AlgorithmParameterSpec parameters(String algorithm, byte[] iv) {
        return switch (algorithm) {
        case SymmetricCipher.ALGORITHM_AES_GCM -> new GCMParameterSpec(128, iv);
        case SymmetricCipher.ALGORITHM_CHA_CHA -> new ChaCha20ParameterSpec(iv, 1);
        default -> new IvParameterSpec(iv);
        };
    }
}
