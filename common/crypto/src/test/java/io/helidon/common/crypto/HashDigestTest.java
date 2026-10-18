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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
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
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HashDigestTest {

    private static final Base64Value DATA_TO_SIGN_1 = Base64Value.create("Some data to be signed1");
    private static final Base64Value DATA_TO_SIGN_2 = Base64Value.create("Some data to be signed2");

    @Test
    void testBuilderDefaultsAndFactory() {
        HashDigest.Builder builder = HashDigest.builder();
        assertThat(builder.algorithm(), is(HashDigest.ALGORITHM_SHA_256));
        assertThat(builder.provider(), is(Optional.empty()));

        Base64Value input = Base64Value.create("abc");
        byte[] expected = HexFormat.of().parseHex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(builder.build().digest(input).toBytes(), is(expected));
        assertThat(HashDigest.create(HashDigest.ALGORITHM_SHA_256).digest(input).toBytes(), is(expected));
    }

    @Test
    void testExplicitProviderAndBuilderReuse() throws NoSuchAlgorithmException {
        String provider = MessageDigest.getInstance(HashDigest.ALGORITHM_SHA_256).getProvider().getName();
        HashDigest.Builder builder = HashDigest.builder().provider(provider);
        HashDigest original = builder.build();
        byte[] expected = HashDigest.create(HashDigest.ALGORITHM_SHA_256).digest(DATA_TO_SIGN_1).toBytes();
        assertThat(original.digest(DATA_TO_SIGN_1).toBytes(), is(expected));

        builder.algorithm(HashDigest.ALGORITHM_SHA_384);
        assertThat(builder.algorithm(), is(HashDigest.ALGORITHM_SHA_384));
        assertThat(builder.provider(), is(Optional.of(provider)));
        builder.clearProvider();
        assertThat(builder.build().digest(DATA_TO_SIGN_1).toBytes(),
                   is(HashDigest.create(HashDigest.ALGORITHM_SHA_384).digest(DATA_TO_SIGN_1).toBytes()));
        assertThat(original.digest(DATA_TO_SIGN_1).toBytes(), is(expected));
    }

    @Test
    void testNullSettersPreserveConfiguration() throws NoSuchAlgorithmException {
        String provider = MessageDigest.getInstance(HashDigest.ALGORITHM_SHA_256).getProvider().getName();
        HashDigest.Builder builder = HashDigest.builder().provider(provider);

        assertThrows(NullPointerException.class, () -> builder.algorithm(null));
        assertThrows(NullPointerException.class, () -> builder.provider(null));
        assertThat(builder.algorithm(), is(HashDigest.ALGORITHM_SHA_256));
        assertThat(builder.provider(), is(Optional.of(provider)));
        assertThat(builder.build().digest(DATA_TO_SIGN_1).toBytes(),
                   is(HashDigest.create(HashDigest.ALGORITHM_SHA_256).digest(DATA_TO_SIGN_1).toBytes()));
    }

    @Test
    void testClearProviderRestoresDefaultLookup() {
        HashDigest.Builder builder = HashDigest.builder().provider("HelidonMissingHashDigestTestProvider");
        assertThrows(IllegalStateException.class, builder::build);

        assertThat(builder.clearProvider(), is(builder));
        assertThat(builder.provider(), is(Optional.empty()));
        assertThat(builder.build().digest(DATA_TO_SIGN_1).toBytes(),
                   is(HashDigest.create(HashDigest.ALGORITHM_SHA_256).digest(DATA_TO_SIGN_1).toBytes()));
    }

    @Test
    void testConstructionValidatesAlgorithmAndProvider() {
        IllegalStateException algorithmFailure = assertThrows(IllegalStateException.class,
                () -> HashDigest.builder().algorithm("HelidonMissingHashDigestTestAlgorithm").build());
        assertThat(algorithmFailure.getMessage(), is("Could not create digest"));
        assertThat(algorithmFailure.getCause(), instanceOf(NoSuchAlgorithmException.class));

        IllegalStateException providerFailure = assertThrows(IllegalStateException.class,
                () -> HashDigest.builder().provider("HelidonMissingHashDigestTestProvider").build());
        assertThat(providerFailure.getMessage(), is("Could not create digest"));
        assertThat(providerFailure.getCause(), instanceOf(NoSuchProviderException.class));
        assertThrows(IllegalArgumentException.class, () -> HashDigest.builder().provider("").build());
    }

    private static Stream<ParameterWrapper> initParams() throws IllegalAccessException {
        List<ParameterWrapper> params = new ArrayList<>();
        List<Field> fields = Arrays.stream(HashDigest.class.getDeclaredFields())
                .filter(it -> Modifier.isStatic(it.getModifiers()))
                .filter(it -> it.getName().startsWith("ALGORITHM_"))
                .collect(Collectors.toList());
        for (Field field : fields) {
            String digestType = (String) field.get(null);
            HashDigest digest = HashDigest.create(digestType);
            params.add(new ParameterWrapper(digestType, digest));
        }
        return params.stream();
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testSignAndVerify(ParameterWrapper params) {
        HashDigest hashDigest = params.digest;
        Base64Value digest = hashDigest.digest(DATA_TO_SIGN_1);
        Base64Value digestSecond = hashDigest.digest(DATA_TO_SIGN_1);
        assertThat(digest.toBytes(), not(DATA_TO_SIGN_1.toBytes()));
        assertThat(digest.toBytes(), is(digestSecond.toBytes()));

        assertThat(hashDigest.verify(DATA_TO_SIGN_1, digest), is(true));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testVerificationFailure(ParameterWrapper params) {
        HashDigest hashDigest = params.digest;
        Base64Value digest = hashDigest.digest(DATA_TO_SIGN_1);

        assertThat(hashDigest.verify(DATA_TO_SIGN_2, digest), is(false));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testStringFormat(ParameterWrapper params) {
        HashDigest hashDigest = params.digest;
        String digest = hashDigest.digestString(DATA_TO_SIGN_1);
        assertThat(digest, startsWith(CryptoCommonConstants.PREFIX));
        assertThat(hashDigest.verifyString(DATA_TO_SIGN_1, digest), is(true));
    }

    private static class ParameterWrapper {

        private final String digestType;
        private final HashDigest digest;

        private ParameterWrapper(String digestType, HashDigest digest) {
            this.digestType = digestType;
            this.digest = digest;
        }

        @Override
        public String toString() {
            return digestType;
        }

    }

}
