/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;

import io.helidon.common.Base64Value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AsymmetricCipherTest {

    private static final Base64Value DATA = Base64Value.create("Some message to encrypt!");
    private static final Map<Integer, Pair> GENERATED_KEYS = new HashMap<>();

    static {
        for (Integer keySize : List.of(1024, 2048, 3072)) {
            try {
                GENERATED_KEYS.put(keySize, new Pair(keyPair(keySize), keyPair(keySize)));
            } catch (NoSuchAlgorithmException e) {
                throw new CryptoException("Could not generate RSA keys", e);
            }
        }
    }

    private static Stream<ParameterWrapper> initParams() throws IllegalAccessException, NoSuchAlgorithmException {
        List<ParameterWrapper> params = new ArrayList<>();
        List<Field> fields = Arrays.stream(AsymmetricCipher.class.getDeclaredFields())
                .filter(it -> Modifier.isStatic(it.getModifiers()))
                .filter(it -> it.getName().startsWith("ALGORITHM_"))
                .collect(Collectors.toList());

        for (Field field : fields) {
            String algorithm = (String) field.get(null);
            for (Map.Entry<Integer, Pair> entry : GENERATED_KEYS.entrySet()) {
                Pair pair = entry.getValue();
                AsymmetricCipher firstAsymCipher = AsymmetricCipher.builder()
                        .algorithm(algorithm)
                        .privateKey(pair.first.getPrivate())
                        .publicKey(pair.first.getPublic())
                        .build();
                AsymmetricCipher secondAsymCipher = AsymmetricCipher.builder()
                        .algorithm(algorithm)
                        .privateKey(pair.second.getPrivate())
                        .publicKey(pair.second.getPublic())
                        .build();
                params.add(new ParameterWrapper(algorithm, entry.getKey(), firstAsymCipher, secondAsymCipher));
            }

        }
        return params.stream();
    }

    private static KeyPair keyPair(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(keySize, random);
        return keyGen.generateKeyPair();
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testPositiveScenario(ParameterWrapper param) {
        AsymmetricCipher asymmetricCipher = param.correct;
        Base64Value encrypted = asymmetricCipher.encrypt(DATA);
        assertThat(encrypted.toBytes(), not(DATA.toBytes()));
        Base64Value decrypted = asymmetricCipher.decrypt(encrypted);
        assertThat(decrypted.toBytes(), is(DATA.toBytes()));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testNegativeScenario(ParameterWrapper param) {
        AsymmetricCipher correct = param.correct;
        AsymmetricCipher incorrect = param.incorrect;
        Base64Value encrypted = correct.encrypt(DATA);
        assertThat(encrypted.toBytes(), not(DATA.toBytes()));
        CryptoException cryptoException = assertThrows(CryptoException.class, () -> incorrect.decrypt(encrypted));
        assertThat(cryptoException.getMessage(), is("Message could not be decrypted"));
        assertThat(cryptoException.getCause(), instanceOf(BadPaddingException.class));
    }

    @Test
    @SuppressWarnings("removal")
    void testDefaultProviderInteroperability() {
        KeyPair keys = GENERATED_KEYS.get(2048).first;
        String algorithm = AsymmetricCipher.ALGORITHM_RSA_ECB_OAEP256;
        AsymmetricCipher cipher = AsymmetricCipher.builder()
                .algorithm(algorithm)
                .privateKey(keys.getPrivate())
                .publicKey(keys.getPublic())
                .build();

        Base64Value shortcutEncrypted = AsymmetricCipher.encrypt(algorithm, keys.getPublic(), DATA);
        assertThat(AsymmetricCipher.decrypt(algorithm, null, keys.getPrivate(), shortcutEncrypted).toBytes(),
                   is(DATA.toBytes()));

        Base64Value legacyEncrypted = AsymmetricCipher.encrypt(algorithm, null, keys.getPublic(), DATA);
        assertThat(cipher.decrypt(legacyEncrypted).toBytes(), is(DATA.toBytes()));

        Base64Value builderEncrypted = cipher.encrypt(DATA);
        assertThat(AsymmetricCipher.decrypt(algorithm, keys.getPrivate(), builderEncrypted).toBytes(), is(DATA.toBytes()));
    }

    @Test
    @SuppressWarnings("removal")
    void testExplicitProviderInteroperability() throws Exception {
        KeyPair keys = GENERATED_KEYS.get(2048).first;
        String algorithm = AsymmetricCipher.ALGORITHM_RSA_ECB_OAEP256;
        String provider = Cipher.getInstance(algorithm).getProvider().getName();
        AsymmetricCipher cipher = AsymmetricCipher.builder()
                .provider(provider)
                .algorithm(algorithm)
                .privateKey(keys.getPrivate())
                .publicKey(keys.getPublic())
                .build();

        Base64Value legacyEncrypted = AsymmetricCipher.encrypt(algorithm, provider, keys.getPublic(), DATA);
        assertThat(cipher.decrypt(legacyEncrypted).toBytes(), is(DATA.toBytes()));
        assertThat(AsymmetricCipher.decrypt(algorithm, provider, keys.getPrivate(), cipher.encrypt(DATA)).toBytes(),
                   is(DATA.toBytes()));
    }

    @Test
    @SuppressWarnings("removal")
    void testLegacyInvalidProviderDoesNotFallBack() {
        KeyPair keys = GENERATED_KEYS.get(2048).first;
        String algorithm = AsymmetricCipher.ALGORITHM_RSA_ECB_OAEP256;
        Base64Value encrypted = AsymmetricCipher.encrypt(algorithm, keys.getPublic(), DATA);

        CryptoException encryptFailure = assertThrows(CryptoException.class,
                () -> AsymmetricCipher.encrypt(algorithm, "nonexistent-test-provider", keys.getPublic(), DATA));
        assertThat(encryptFailure.getCause(), instanceOf(NoSuchProviderException.class));
        CryptoException decryptFailure = assertThrows(CryptoException.class,
                () -> AsymmetricCipher.decrypt(algorithm, "nonexistent-test-provider", keys.getPrivate(), encrypted));
        assertThat(decryptFailure.getCause(), instanceOf(NoSuchProviderException.class));
    }

    @Test
    void testAlgorithmPreservesProviderUntilCleared() {
        KeyPair keys = GENERATED_KEYS.get(2048).first;
        AsymmetricCipher.Builder builder = AsymmetricCipher.builder()
                .provider("nonexistent-test-provider")
                .algorithm(AsymmetricCipher.ALGORITHM_RSA_ECB_OAEP256)
                .privateKey(keys.getPrivate())
                .publicKey(keys.getPublic());

        assertThat(builder.provider(), is(Optional.of("nonexistent-test-provider")));
        CryptoException failure = assertThrows(CryptoException.class, () -> builder.build().encrypt(DATA));
        assertThat(failure.getCause(), instanceOf(NoSuchProviderException.class));

        builder.clearProvider();
        assertThat(builder.provider(), is(Optional.empty()));
        AsymmetricCipher cipher = builder.build();
        assertThat(cipher.decrypt(cipher.encrypt(DATA)).toBytes(), is(DATA.toBytes()));
    }

    @Test
    void testClearKeysRestrictsOperations() {
        KeyPair keys = GENERATED_KEYS.get(2048).first;
        AsymmetricCipher.Builder builder = AsymmetricCipher.builder();
        assertThat(builder.algorithm(), is(AsymmetricCipher.ALGORITHM_RSA_ECB_OAEP256));
        assertThat(builder.provider(), is(Optional.empty()));
        assertThat(builder.privateKey(), is(Optional.empty()));
        assertThat(builder.publicKey(), is(Optional.empty()));

        builder.privateKey(keys.getPrivate()).publicKey(keys.getPublic());
        Base64Value encrypted = builder.build().encrypt(DATA);
        builder.clearPublicKey();
        assertThat(builder.publicKey(), is(Optional.empty()));
        AsymmetricCipher decryptOnly = builder.build();
        assertThat(decryptOnly.decrypt(encrypted).toBytes(), is(DATA.toBytes()));
        assertThrows(CryptoException.class, () -> decryptOnly.encrypt(DATA));

        builder.publicKey(keys.getPublic()).clearPrivateKey();
        assertThat(builder.privateKey(), is(Optional.empty()));
        AsymmetricCipher encryptOnly = builder.build();
        assertThat(decryptOnly.decrypt(encryptOnly.encrypt(DATA)).toBytes(), is(DATA.toBytes()));
        assertThrows(CryptoException.class, () -> encryptOnly.decrypt(encrypted));

        builder.clearPublicKey();
        assertThrows(CryptoException.class, builder::build);
    }

    @Test
    void testNullSettersPreserveConfiguredState() throws Exception {
        KeyPair keys = GENERATED_KEYS.get(2048).first;
        String algorithm = AsymmetricCipher.ALGORITHM_RSA_ECB_OAEP256;
        String provider = Cipher.getInstance(algorithm).getProvider().getName();
        AsymmetricCipher.Builder builder = AsymmetricCipher.builder()
                .algorithm(algorithm)
                .provider(provider)
                .privateKey(keys.getPrivate())
                .publicKey(keys.getPublic());

        assertThrows(NullPointerException.class, () -> builder.algorithm(null));
        assertThrows(NullPointerException.class, () -> builder.provider(null));
        assertThrows(NullPointerException.class, () -> builder.privateKey(null));
        assertThrows(NullPointerException.class, () -> builder.publicKey(null));
        assertThat(builder.algorithm(), is(algorithm));
        assertThat(builder.provider(), is(Optional.of(provider)));
        assertThat(builder.privateKey(), is(Optional.of(keys.getPrivate())));
        assertThat(builder.publicKey(), is(Optional.of(keys.getPublic())));
        AsymmetricCipher cipher = builder.build();
        assertThat(cipher.decrypt(cipher.encrypt(DATA)).toBytes(), is(DATA.toBytes()));
    }

    private static final class ParameterWrapper {

        private final String algorithm;
        private final Integer keySize;
        private final AsymmetricCipher correct;
        private final AsymmetricCipher incorrect;

        private ParameterWrapper(String algorithm, Integer keySize, AsymmetricCipher correct, AsymmetricCipher incorrect) {
            this.algorithm = algorithm;
            this.keySize = keySize;
            this.correct = correct;
            this.incorrect = incorrect;
        }

        @Override
        public String toString() {
            return algorithm + " with key size " + keySize;
        }
    }

    private static final class Pair {

        private final KeyPair first;
        private final KeyPair second;

        private Pair(KeyPair first, KeyPair second) {
            this.first = first;
            this.second = second;
        }
    }

}
