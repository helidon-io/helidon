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

import io.helidon.integrations.common.rest.Base64Value;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class SignatureTest {

    private static Stream<ParameterWrapper> initParams() throws IllegalAccessException, NoSuchAlgorithmException {
        List<ParameterWrapper> params = new ArrayList<>();
        Map<String, Map<Integer, KeyPair>> generatedKeys = new HashMap<>();
        for (Integer keySize : List.of(160, 224, 256, 384, 521)) {
            generatedKeys.computeIfAbsent("ECDSA", mapKey -> new HashMap<>())
                    .put(keySize, keyPair("EC", keySize));
        }
        for (Integer keySize : List.of(1024, 2048, 3072)) {
            generatedKeys.computeIfAbsent("RSA", mapKey -> new HashMap<>())
                    .put(keySize, keyPair("RSA", keySize));
        }
        List<Field> fields = Arrays.stream(Signature.class.getDeclaredFields())
                .filter(it -> Modifier.isStatic(it.getModifiers()))
                .filter(it -> it.getName().startsWith("ALGORITHM_"))
                .collect(Collectors.toList());
        for (Map.Entry<String, Map<Integer, KeyPair>> generatedEntry : generatedKeys.entrySet()) {
            List<Field> filteredFields = fields.stream()
                    .filter(it -> it.getName().endsWith(generatedEntry.getKey()))
                    .collect(Collectors.toList());
            for (Field field : filteredFields) {
                String digestType = (String) field.get(null);
                for (Map.Entry<Integer, KeyPair> entry : generatedEntry.getValue().entrySet()) {
                    KeyPair keyPair = entry.getValue();
                    Signature signature = Signature.builder()
                            .algorithm(digestType)
                            .privateKey(keyPair.getPrivate())
                            .publicKey(keyPair.getPublic())
                            .build();
                    params.add(new ParameterWrapper(digestType, entry.getKey(), signature));
                }
            }
        }
        return params.stream();
    }

    private static KeyPair keyPair(String generator, int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(generator);
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(keySize, random);
        return keyGen.generateKeyPair();
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void signatureTest(ParameterWrapper parameterWrapper) {
        Signature signature = parameterWrapper.signature;
        Base64Value value = Base64Value.create("Some data to be signed");
        Base64Value digest = signature.digest(value);
        assertThat(digest.toBytes(), not(value.toBytes()));
        signature.verify(value, digest);
        assertThat(signature.verify(value, digest), is(true));
    }

    private static class ParameterWrapper {

        private final String digestType;
        private final Integer keySize;
        private final Signature signature;

        public ParameterWrapper(String digestType,
                                Integer key,
                                Signature signature) {
            this.digestType = digestType;
            this.keySize = key;
            this.signature = signature;
        }

        @Override
        public String toString() {
            return digestType + " with key size " + keySize;
        }

    }

}
