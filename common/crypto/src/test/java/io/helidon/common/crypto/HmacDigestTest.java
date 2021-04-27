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
import java.nio.charset.StandardCharsets;
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
import static org.hamcrest.MatcherAssert.assertThat;

public class HmacDigestTest {

    private static final Base64Value DATA_TO_SIGN = Base64Value.create("Some data to be signed");

    private static Stream<ParameterWrapper> initParams() throws IllegalAccessException {
        List<ParameterWrapper> params = new ArrayList<>();
        byte[] correctSecret = "someCorrectSecret".getBytes(StandardCharsets.UTF_8);
        byte[] incorrectSecret = "someIncorrectSecret".getBytes(StandardCharsets.UTF_8);

        List<Field> fields = Arrays.stream(HmacDigest.class.getDeclaredFields())
                .filter(it -> Modifier.isStatic(it.getModifiers()))
                .filter(it -> it.getName().startsWith("ALGORITHM_"))
                .collect(Collectors.toList());
        for (Field field : fields) {
            String hmacType = (String) field.get(null);
            HmacDigest correct = HmacDigest.builder()
                    .algorithm(hmacType)
                    .hmacSecret(correctSecret)
                    .build();
            HmacDigest incorrect = HmacDigest.builder()
                    .algorithm(hmacType)
                    .hmacSecret(incorrectSecret)
                    .build();
            params.add(new ParameterWrapper(hmacType, correct, incorrect));
        }
        return params.stream();
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testSignAndVerify(ParameterWrapper params) {
        HmacDigest hmacDigest = params.correct;
        Base64Value digest = hmacDigest.digest(DATA_TO_SIGN);
        assertThat(digest.toBytes(), not(DATA_TO_SIGN.toBytes()));

        assertThat(hmacDigest.verify(DATA_TO_SIGN, digest), is(true));
    }

    @ParameterizedTest
    @MethodSource("initParams")
    public void testVerificationFailure(ParameterWrapper params) {
        HmacDigest correct = params.correct;
        HmacDigest incorrect = params.incorrect;
        Base64Value digest = correct.digest(DATA_TO_SIGN);

        assertThat(incorrect.verify(DATA_TO_SIGN, digest), is(false));
    }

    private static class ParameterWrapper {

        private final String hmacType;
        private final HmacDigest correct;
        private final HmacDigest incorrect;

        private ParameterWrapper(String hmacType,
                                 HmacDigest correct,
                                 HmacDigest incorrect) {
            this.hmacType = hmacType;
            this.correct = correct;
            this.incorrect = incorrect;
        }

        @Override
        public String toString() {
            return hmacType;
        }

    }

}
