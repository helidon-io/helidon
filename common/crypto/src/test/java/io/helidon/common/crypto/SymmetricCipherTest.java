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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.integrations.common.rest.Base64Value;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

public class SymmetricCipherTest {

    private static final String TEST_VALUE = "some value";
    private static final char[] DEFAULT_PASSWORD = "test".toCharArray();
    private static final char[] INCORRECT_PASSWORD = "incorrect".toCharArray();

    private static Stream<ParameterWrapper> initParams() throws IllegalAccessException {
        List<ParameterWrapper> symmetricCiphers = new ArrayList<>();
        List<Field> fields = Arrays.stream(SymmetricCipher.class.getDeclaredFields())
                .filter(it -> Modifier.isStatic(it.getModifiers()))
                .filter(it -> it.getName().startsWith("ALGORITHM_"))
                .collect(Collectors.toList());
        for (Field field : fields) {
            String algorithm = (String) field.get(null);
            List<Integer> keySizes;
            if (algorithm.startsWith("AES")) {
                keySizes = List.of(128, 192, 256);
            } else {
                keySizes = List.of(256);
            }
            for (int keySize : keySizes) {
                SymmetricCipher correct = SymmetricCipher.builder()
                        .algorithm(algorithm)
                        .keySize(keySize)
                        .password(DEFAULT_PASSWORD)
                        .build();
                SymmetricCipher incorrect = SymmetricCipher.builder()
                        .algorithm(algorithm)
                        .keySize(keySize)
                        .password(INCORRECT_PASSWORD)
                        .build();
                symmetricCiphers.add(new ParameterWrapper(algorithm, keySize, correct, incorrect));
            }
        }
        return symmetricCiphers.stream();
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testBasicEncryptionAndDecryption(ParameterWrapper parameters) {
        SymmetricCipher symmetricCipher = parameters.correct;

        Base64Value value1 = symmetricCipher.encrypt(Base64Value.create(TEST_VALUE));
        Base64Value value2 = symmetricCipher.encrypt(Base64Value.create(TEST_VALUE));
        //Two equivalent values should not be the same when encrypted
        assertThat(value1.toBytes(), not(value2.toBytes()));

        //Two equivalent values should be the same again when decrypted
        assertThat(symmetricCipher.decrypt(value1).toDecodedString(), is(TEST_VALUE));
        assertThat(symmetricCipher.decrypt(value2).toDecodedString(), is(TEST_VALUE));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testStringFormat(ParameterWrapper parameters) {
        SymmetricCipher symmetricCipher = parameters.correct;

        String value1 = symmetricCipher.encryptToString(Base64Value.create(TEST_VALUE));
        String value2 = symmetricCipher.encryptToString(Base64Value.create(TEST_VALUE));
        //Two equivalent values should not be the same when encrypted
        assertThat(value1, not(value2));
        assertThat(value1, startsWith(CryptoCommonConstants.PREFIX));
        assertThat(value2, startsWith(CryptoCommonConstants.PREFIX));

        //Two equivalent values should be the same again when decrypted
        assertThat(symmetricCipher.decryptFromString(value1).toDecodedString(), is(TEST_VALUE));
        assertThat(symmetricCipher.decryptFromString(value2).toDecodedString(), is(TEST_VALUE));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testWrongKey(ParameterWrapper parameters) {
        SymmetricCipher correct = parameters.correct;
        SymmetricCipher incorrect = parameters.incorrect;

        Base64Value value = correct.encrypt(Base64Value.create(TEST_VALUE));

        try {
            Base64Value decrypted = incorrect.decrypt(value);
            assertThat(decrypted.toDecodedString(), not(TEST_VALUE));
        } catch (CryptoException e) {
            assertThat(e.getMessage(), is("Failed to decrypt the message"));
        }
    }

    private static class ParameterWrapper {

        private final String algorithm;
        private final int keySize;
        private final SymmetricCipher correct;
        private final SymmetricCipher incorrect;

        ParameterWrapper(String algorithm,
                         int keySize,
                         SymmetricCipher correct,
                         SymmetricCipher incorrect) {
            this.algorithm = algorithm;
            this.keySize = keySize;
            this.correct = correct;
            this.incorrect = incorrect;
        }

        @Override
        public String toString() {
            return algorithm + " with " + keySize + " bit key size";
        }

    }

}
