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

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRejectsNumbersWithLeadingPlusAtRoot(BindingMethod bindingMethod) {
        assertAll(
                () -> assertRootNumberRejected(bindingMethod, "+1"),
                () -> assertRootNumberRejected(bindingMethod, "+0.5"),
                () -> assertRootNumberRejected(bindingMethod, "+1e2")
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "int = zero / ( digit1-9 *DIGIT )"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRejectsNumbersWithoutIntegerComponentAtRoot(BindingMethod bindingMethod) {
        assertAll(
                () -> assertRootNumberRejected(bindingMethod, ".1"),
                () -> assertRootNumberRejected(bindingMethod, "-.1")
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "A fraction part is a decimal point followed by one or more digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRejectsNumbersWithoutFractionDigitsAtRoot(BindingMethod bindingMethod) {
        assertAll(
                () -> assertRootNumberRejected(bindingMethod, "1."),
                () -> assertRootNumberRejected(bindingMethod, "0."),
                () -> assertRootNumberRejected(bindingMethod, "-0.")
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "The E and optional sign are followed by one or more digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRejectsNumbersWithoutExponentDigitsAtRoot(BindingMethod bindingMethod) {
        assertAll(
                () -> assertRootNumberRejected(bindingMethod, "1e"),
                () -> assertRootNumberRejected(bindingMethod, "1e+"),
                () -> assertRootNumberRejected(bindingMethod, "1e-")
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "A fraction part is a decimal point followed by one or more digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRejectsMalformedNumberBodiesAtRoot(BindingMethod bindingMethod) {
        assertAll(
                () -> assertRootNumberRejected(bindingMethod, "1.2.3"),
                () -> assertRootNumberRejected(bindingMethod, "1e2e3")
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "Numeric values that cannot be represented in the grammar below (such as Infinity and NaN) are not permitted."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testQuotedSpecialFloatValuesRoundTrip(BindingMethod bindingMethod) {
        assertAll(
                () -> assertQuotedFloatRoundTrip(bindingMethod, Float.NaN, "\"NaN\""),
                () -> assertQuotedFloatRoundTrip(bindingMethod, Float.POSITIVE_INFINITY, "\"Infinity\""),
                () -> assertQuotedFloatRoundTrip(bindingMethod, Float.NEGATIVE_INFINITY, "\"-Infinity\"")
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "Numeric values that cannot be represented in the grammar below (such as Infinity and NaN) are not permitted."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testQuotedSpecialDoubleValuesRoundTrip(BindingMethod bindingMethod) {
        assertAll(
                () -> assertQuotedDoubleRoundTrip(bindingMethod, Double.NaN, "\"NaN\""),
                () -> assertQuotedDoubleRoundTrip(bindingMethod, Double.POSITIVE_INFINITY, "\"Infinity\""),
                () -> assertQuotedDoubleRoundTrip(bindingMethod, Double.NEGATIVE_INFINITY, "\"-Infinity\"")
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "Numeric values that cannot be represented in the grammar below (such as Infinity and NaN) are not permitted."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testQuotedSpecialPrimitiveFloatValuesRoundTrip(BindingMethod bindingMethod) {
        assertAll(
                () -> assertQuotedPrimitiveFloatRoundTrip(bindingMethod, Float.NaN, "\"NaN\""),
                () -> assertQuotedPrimitiveFloatRoundTrip(bindingMethod, Float.POSITIVE_INFINITY, "\"Infinity\""),
                () -> assertQuotedPrimitiveFloatRoundTrip(bindingMethod, Float.NEGATIVE_INFINITY, "\"-Infinity\"")
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "Numeric values that cannot be represented in the grammar below (such as Infinity and NaN) are not permitted."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testQuotedSpecialPrimitiveDoubleValuesRoundTrip(BindingMethod bindingMethod) {
        assertAll(
                () -> assertQuotedPrimitiveDoubleRoundTrip(bindingMethod, Double.NaN, "\"NaN\""),
                () -> assertQuotedPrimitiveDoubleRoundTrip(bindingMethod, Double.POSITIVE_INFINITY, "\"Infinity\""),
                () -> assertQuotedPrimitiveDoubleRoundTrip(bindingMethod, Double.NEGATIVE_INFINITY, "\"-Infinity\"")
        );
    }

    private void assertQuotedFloatRoundTrip(BindingMethod bindingMethod, float value, String jsonLiteral) {
        String rootJson = bindingMethod.serialize(jsonBinding, value);
        assertThat(rootJson, is(jsonLiteral));
        assertSameFloat(bindingMethod.deserialize(jsonBinding, jsonLiteral, Float.class), value);

        FloatNumberBean bean = new FloatNumberBean(value);
        String beanJson = bindingMethod.serialize(jsonBinding, bean);
        assertThat(beanJson, is("{\"number\":" + jsonLiteral + "}"));
        assertSameFloat(bindingMethod.deserialize(jsonBinding, beanJson, FloatNumberBean.class).number(), value);
    }

    private void assertQuotedDoubleRoundTrip(BindingMethod bindingMethod, double value, String jsonLiteral) {
        String rootJson = bindingMethod.serialize(jsonBinding, value);
        assertThat(rootJson, is(jsonLiteral));
        assertSameDouble(bindingMethod.deserialize(jsonBinding, jsonLiteral, Double.class), value);

        DoubleNumberBean bean = new DoubleNumberBean(value);
        String beanJson = bindingMethod.serialize(jsonBinding, bean);
        assertThat(beanJson, is("{\"number\":" + jsonLiteral + "}"));
        assertSameDouble(bindingMethod.deserialize(jsonBinding, beanJson, DoubleNumberBean.class).number(), value);
    }

    private void assertQuotedPrimitiveFloatRoundTrip(BindingMethod bindingMethod, float value, String jsonLiteral) {
        assertSameFloat(bindingMethod.deserialize(jsonBinding, jsonLiteral, float.class), value);

        PrimitiveFloatNumberBean bean = new PrimitiveFloatNumberBean(value);
        String beanJson = bindingMethod.serialize(jsonBinding, bean);
        assertThat(beanJson, is("{\"number\":" + jsonLiteral + "}"));
        assertSameFloat(bindingMethod.deserialize(jsonBinding, beanJson, PrimitiveFloatNumberBean.class).number(), value);
    }

    private void assertQuotedPrimitiveDoubleRoundTrip(BindingMethod bindingMethod, double value, String jsonLiteral) {
        assertSameDouble(bindingMethod.deserialize(jsonBinding, jsonLiteral, double.class), value);

        PrimitiveDoubleNumberBean bean = new PrimitiveDoubleNumberBean(value);
        String beanJson = bindingMethod.serialize(jsonBinding, bean);
        assertThat(beanJson, is("{\"number\":" + jsonLiteral + "}"));
        assertSameDouble(bindingMethod.deserialize(jsonBinding, beanJson, PrimitiveDoubleNumberBean.class).number(), value);
    }

    private static void assertSameFloat(float actual, float expected) {
        if (Float.isNaN(expected)) {
            assertThat(Float.isNaN(actual), is(true));
            return;
        }
        assertThat(actual, is(expected));
    }

    private static void assertSameDouble(double actual, double expected) {
        if (Double.isNaN(expected)) {
            assertThat(Double.isNaN(actual), is(true));
            return;
        }
        assertThat(actual, is(expected));
    }

    private void assertRootNumberRejected(BindingMethod bindingMethod, String json) {
        assertAll(
                () -> assertThrows(JsonException.class,
                                   () -> bindingMethod.deserialize(jsonBinding, json, BigDecimal.class)),
                () -> assertThrows(JsonException.class,
                                   () -> bindingMethod.deserialize(jsonBinding, json, Double.class))
        );
    }

    @Json.Entity
    record BigNumberBean(BigDecimal decimal, BigInteger integer) {

    }

    @Json.Entity
    record FloatNumberBean(Float number) {

    }

    @Json.Entity
    record PrimitiveFloatNumberBean(float number) {

    }

    @Json.Entity
    record DoubleNumberBean(Double number) {

    }

    @Json.Entity
    record PrimitiveDoubleNumberBean(double number) {

    }
}
