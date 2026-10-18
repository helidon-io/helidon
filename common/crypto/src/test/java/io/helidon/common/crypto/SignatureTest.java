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
import java.security.InvalidKeyException;
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

import io.helidon.common.Base64Value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SignatureTest {
    private static final SecureRandom RANDOM;
    static {
        try {
            RANDOM = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot create secure random", e);
        }
    }

    private static Stream<ParameterWrapper> initParams() throws IllegalAccessException, NoSuchAlgorithmException {
        List<ParameterWrapper> params = new ArrayList<>();
        Map<String, Map<Integer, KeyPair>> generatedKeys = new HashMap<>();
        for (Integer keySize : List.of(256, 384, 521)) {
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
        keyGen.initialize(keySize, RANDOM);
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

    @Test
    void testBuilderDefaultsAndRequiredKeys() {
        Signature.Builder builder = Signature.builder();
        assertThat(builder.algorithm(), is(Signature.ALGORITHM_SHA256_RSA));
        assertThat(builder.provider(), is(Optional.empty()));
        assertThat(builder.privateKey(), is(Optional.empty()));
        assertThat(builder.publicKey(), is(Optional.empty()));
        CryptoException exception = assertThrows(CryptoException.class, builder::build);
        assertThat(exception.getMessage(), is("At least private or public key has to be set"));
    }

    @Test
    void testSignOnlyAndVerifyOnlyInstances() throws NoSuchAlgorithmException {
        KeyPair keys = keyPair("RSA", 1024);
        Signature signer = Signature.builder().privateKey(keys.getPrivate()).build();
        Signature verifier = Signature.builder().publicKey(keys.getPublic()).build();
        Base64Value value = Base64Value.create("Sign-only and verify-only instances");
        Base64Value digest = signer.digest(value);
        assertThat(verifier.verify(value, digest), is(true));
        assertThat(assertThrows(CryptoException.class, () -> signer.verify(value, digest)).getMessage(),
                   is("Public key not set. This object cannot verify the signatures"));
        assertThat(assertThrows(CryptoException.class, () -> verifier.digest(value)).getMessage(),
                   is("Private key not set. This object cannot create new signatures"));
    }

    @Test
    void testBuilderNullSettersPreserveConfiguration() throws NoSuchAlgorithmException {
        KeyPair keys = keyPair("RSA", 1024);
        Signature.Builder builder = Signature.builder()
                .algorithm(Signature.ALGORITHM_SHA384_RSA)
                .provider("HelidonMissingSignatureTestProvider")
                .privateKey(keys.getPrivate())
                .publicKey(keys.getPublic());
        assertThrows(NullPointerException.class, () -> builder.algorithm(null));
        assertThrows(NullPointerException.class, () -> builder.provider(null));
        assertThrows(NullPointerException.class, () -> builder.privateKey(null));
        assertThrows(NullPointerException.class, () -> builder.publicKey(null));
        assertThat(builder.algorithm(), is(Signature.ALGORITHM_SHA384_RSA));
        assertThat(builder.provider(), is(Optional.of("HelidonMissingSignatureTestProvider")));
        assertThat(builder.privateKey(), is(Optional.of(keys.getPrivate())));
        assertThat(builder.publicKey(), is(Optional.of(keys.getPublic())));
        builder.clearProvider();
        Signature signature = builder.build();
        Base64Value value = Base64Value.create("Configuration survives rejected null setters");
        assertThat(signature.verify(value, signature.digest(value)), is(true));
    }

    @Test
    void testBuilderClearsAndReusePreserveBuiltInstances() throws NoSuchAlgorithmException {
        KeyPair keys = keyPair("RSA", 1024);
        Signature.Builder builder = Signature.builder().privateKey(keys.getPrivate()).publicKey(keys.getPublic());
        Signature both = builder.build();
        builder.clearPrivateKey();
        assertThat(builder.privateKey(), is(Optional.empty()));
        Signature verifier = builder.build();
        builder.privateKey(keys.getPrivate()).clearPublicKey();
        assertThat(builder.publicKey(), is(Optional.empty()));
        Signature signer = builder.build();
        builder.clearPrivateKey();
        assertThrows(CryptoException.class, builder::build);
        Base64Value value = Base64Value.create("Existing instances retain their keys");
        assertThat(both.verify(value, both.digest(value)), is(true));
        assertThat(verifier.verify(value, signer.digest(value)), is(true));
    }

    @Test
    void testProviderSelectionAndClear() throws Exception {
        KeyPair keys = keyPair("RSA", 1024);
        java.security.Signature engine = java.security.Signature.getInstance(Signature.ALGORITHM_SHA256_RSA);
        engine.initSign(keys.getPrivate());
        String provider = engine.getProvider().getName();
        Signature.Builder builder = Signature.builder()
                .privateKey(keys.getPrivate())
                .publicKey(keys.getPublic())
                .provider(provider)
                .algorithm(Signature.ALGORITHM_SHA256_RSA);
        assertThat(builder.provider(), is(Optional.of(provider)));
        Signature explicit = builder.build();
        builder.provider("HelidonMissingSignatureTestProvider");
        CryptoException missing = assertThrows(CryptoException.class, builder::build);
        assertThat(missing.getMessage(), is("Could not initialize signature"));
        assertThat(missing.getCause(), instanceOf(NoSuchProviderException.class));
        builder.provider("");
        CryptoException empty = assertThrows(CryptoException.class, builder::build);
        assertThat(empty.getMessage(), is("Could not initialize signature"));
        assertThat(empty.getCause(), instanceOf(IllegalArgumentException.class));
        builder.clearProvider();
        assertThat(builder.provider(), is(Optional.empty()));
        Signature normalLookup = builder.build();
        Base64Value value = Base64Value.create("Explicit and default provider lookup");
        assertThat(normalLookup.verify(value, explicit.digest(value)), is(true));
        assertThat(explicit.verify(value, normalLookup.digest(value)), is(true));
    }

    @Test
    void testInvalidAlgorithmAndKeysFailConstruction() throws NoSuchAlgorithmException {
        KeyPair rsa = keyPair("RSA", 1024);
        Signature.Builder builder = Signature.builder()
                .privateKey(rsa.getPrivate())
                .algorithm("HelidonMissingSignatureAlgorithm");
        CryptoException algorithm = assertThrows(CryptoException.class, builder::build);
        assertThat(algorithm.getMessage(), is("Could not initialize signature"));
        assertThat(algorithm.getCause(), instanceOf(NoSuchAlgorithmException.class));
        KeyPair ec = keyPair("EC", 256);
        CryptoException privateKey = assertThrows(CryptoException.class,
                                                () -> Signature.builder().privateKey(ec.getPrivate()).build());
        assertThat(privateKey.getMessage(), is("Could not initialize signature"));
        assertThat(privateKey.getCause(), instanceOf(InvalidKeyException.class));
        CryptoException publicKey = assertThrows(CryptoException.class,
                                               () -> Signature.builder()
                                                       .privateKey(rsa.getPrivate())
                                                       .publicKey(ec.getPublic())
                                                       .build());
        assertThat(publicKey.getMessage(), is("Could not initialize signature"));
        assertThat(publicKey.getCause(), instanceOf(InvalidKeyException.class));
    }

    @Test
    void testIndividuallyValidKeysNeedNotBePaired() throws NoSuchAlgorithmException {
        KeyPair first = keyPair("RSA", 1024);
        KeyPair second = keyPair("RSA", 1024);
        Signature signature = Signature.builder().privateKey(first.getPrivate()).publicKey(second.getPublic()).build();
        Base64Value value = Base64Value.create("Mismatched keys remain valid configuration");
        assertThat(signature.verify(value, signature.digest(value)), is(false));
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
