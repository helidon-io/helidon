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

package io.helidon.json.tests;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.helidon.json.JsonException;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class Rfc8259BindingNumberTest {

    private final JsonBinding jsonBinding;

    Rfc8259BindingNumberTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWritesBigDecimalAndBigIntegerRootValuesAsJsonNumbers(BindingMethod bindingMethod) {
        assertAll(
                () -> {
                    BigDecimal value = new BigDecimal("123.456");
                    String json = bindingMethod.serialize(jsonBinding, value);
                    assertThat(json, is("123.456"));
                    assertThat(bindingMethod.deserialize(jsonBinding, json, BigDecimal.class), is(value));
                },
                () -> {
                    BigInteger value = new BigInteger("123456789012345678901234567890");
                    String json = bindingMethod.serialize(jsonBinding, value);
                    assertThat(json, is("123456789012345678901234567890"));
                    assertThat(bindingMethod.deserialize(jsonBinding, json, BigInteger.class), is(value));
                }
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWritesBigDecimalAndBigIntegerObjectMembersAsJsonNumbers(BindingMethod bindingMethod) {
        BigNumberBean bean = new BigNumberBean(new BigDecimal("123.456"),
                                               new BigInteger("123456789012345678901234567890"));

        String json = bindingMethod.serialize(jsonBinding, bean);
        assertThat(json, is("{\"decimal\":123.456,\"integer\":123456789012345678901234567890}"));
        assertThat(bindingMethod.deserialize(jsonBinding, json, BigNumberBean.class), is(bean));
    }

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWritesLongMinimumValueAsJsonNumber(BindingMethod bindingMethod) {
        String json = bindingMethod.serialize(jsonBinding, Long.MIN_VALUE);
        assertThat(json, is("-9223372036854775808"));
        assertThat(bindingMethod.deserialize(jsonBinding, json, Long.class), is(Long.MIN_VALUE));
    }

    @Json.Entity
    record BigNumberBean(BigDecimal decimal, BigInteger integer) {

    }

    @Json.Entity
    record FloatNumberBean(Float number) {

    }

    @Json.Entity
    record DoubleNumberBean(Double number) {

    }
}
