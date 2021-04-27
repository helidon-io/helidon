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

public class PlainDigestTest {

    private static final Base64Value DATA_TO_SIGN_1 = Base64Value.create("Some data to be signed1");
    private static final Base64Value DATA_TO_SIGN_2 = Base64Value.create("Some data to be signed2");

    private static Stream<ParameterWrapper> initParams() throws IllegalAccessException {
        List<ParameterWrapper> params = new ArrayList<>();
        List<Field> fields = Arrays.stream(PlainDigest.class.getDeclaredFields())
                .filter(it -> Modifier.isStatic(it.getModifiers()))
                .filter(it -> it.getName().startsWith("ALGORITHM_"))
                .collect(Collectors.toList());
        for (Field field : fields) {
            String digestType = (String) field.get(null);
            PlainDigest digest = PlainDigest.create(digestType);
            params.add(new ParameterWrapper(digestType, digest));
        }
        return params.stream();
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testSignAndVerify(ParameterWrapper params) {
        PlainDigest plainDigest = params.digest;
        Base64Value digest = plainDigest.digest(DATA_TO_SIGN_1);
        Base64Value digestSecond = plainDigest.digest(DATA_TO_SIGN_1);
        assertThat(digest.toBytes(), not(DATA_TO_SIGN_1.toBytes()));
        assertThat(digest.toBytes(), is(digestSecond.toBytes()));

        assertThat(plainDigest.verify(DATA_TO_SIGN_1, digest), is(true));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testVerificationFailure(ParameterWrapper params) {
        PlainDigest plainDigest = params.digest;
        Base64Value digest = plainDigest.digest(DATA_TO_SIGN_1);

        assertThat(plainDigest.verify(DATA_TO_SIGN_2, digest), is(false));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testStringFormat(ParameterWrapper params) {
        PlainDigest plainDigest = params.digest;
        String digest = plainDigest.digestString(DATA_TO_SIGN_1);
        assertThat(digest, startsWith(CryptoCommonConstants.PREFIX));
        assertThat(plainDigest.verifyString(DATA_TO_SIGN_1, digest), is(true));
    }

    private static class ParameterWrapper {

        private final String digestType;
        private final PlainDigest digest;

        private ParameterWrapper(String digestType, PlainDigest digest) {
            this.digestType = digestType;
            this.digest = digest;
        }

        @Override
        public String toString() {
            return digestType;
        }

    }

}
