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

import io.helidon.common.Base64Value;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

public class HashDigestTest {

    private static final Base64Value DATA_TO_SIGN_1 = Base64Value.create("Some data to be signed1");
    private static final Base64Value DATA_TO_SIGN_2 = Base64Value.create("Some data to be signed2");

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
