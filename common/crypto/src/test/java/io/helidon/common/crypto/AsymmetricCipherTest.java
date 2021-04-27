/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.crypto.BadPaddingException;

import io.helidon.integrations.common.rest.Base64Value;

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
