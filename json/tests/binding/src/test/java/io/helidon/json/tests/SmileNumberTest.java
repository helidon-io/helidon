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

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for Smile format number values (integers, floats, BigInteger, BigDecimal).
 * Tests Smile binary format serialization/deserialization using JsonBinding.
 *
 * <p>Spec-trace comments quote exact Smile spec section titles and then paraphrase the exercised rule.</p>
 */
@Testing.Test
public class SmileNumberTest {

    private final JsonBinding jsonBinding;

    SmileNumberTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    /*
     * Spec: "Token class: Small integers".
     * Rule: values from -16 to +15 are single-byte tokens whose 5 LSB carry the zigzag-decoded payload.
     */
    @Test
    public void testSmallIntegerZero() {
        NumberModel model = new NumberModel(0);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        NumberModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, NumberModel.class);
        assertThat(result.value(), is(0));
    }

    @Test
    public void testSmallIntegerPositive() {
        NumberModel model = new NumberModel(15);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        NumberModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, NumberModel.class);
        assertThat(result.value(), is(15));
    }

    @Test
    public void testSmallIntegerNegative() {
        NumberModel model = new NumberModel(-16);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        NumberModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, NumberModel.class);
        assertThat(result.value(), is(-16));
    }

    /*
     * Spec: "Token class: Simple literals, numbers".
     * Rule: 32-bit integers use token `0x24` and a signed VInt payload ("zigzag encoded, 1 - 5 data bytes").
     */
    @Test
    public void testInt32Min() {
        IntModel model = new IntModel(Integer.MIN_VALUE);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        IntModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, IntModel.class);
        assertThat(result.value(), is(Integer.MIN_VALUE));
    }

    @Test
    public void testInt32Max() {
        IntModel model = new IntModel(Integer.MAX_VALUE);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        IntModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, IntModel.class);
        assertThat(result.value(), is(Integer.MAX_VALUE));
    }

    @Test
    public void testInt32LargePositive() {
        IntModel model = new IntModel(123456789);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        IntModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, IntModel.class);
        assertThat(result.value(), is(123456789));
    }

    @Test
    public void testInt32LargeNegative() {
        IntModel model = new IntModel(-123456789);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        IntModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, IntModel.class);
        assertThat(result.value(), is(-123456789));
    }

    /*
     * Spec: "Token class: Simple literals, numbers".
     * Rule: 64-bit integers use token `0x25` and a signed VInt payload ("zigzag encoded, 5 - 10 data bytes").
     */
    @Test
    public void testInt64Min() {
        LongModel model = new LongModel(Long.MIN_VALUE);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        LongModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, LongModel.class);
        assertThat(result.value(), is(Long.MIN_VALUE));
    }

    @Test
    public void testInt64Max() {
        LongModel model = new LongModel(Long.MAX_VALUE);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        LongModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, LongModel.class);
        assertThat(result.value(), is(Long.MAX_VALUE));
    }

    @Test
    public void testInt64LargeValue() {
        LongModel model = new LongModel(1234567890123456789L);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        LongModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, LongModel.class);
        assertThat(result.value(), is(1234567890123456789L));
    }

    @Test
    public void testInt64LargeNegative() {
        LongModel model = new LongModel(-1234567890123456789L);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        LongModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, LongModel.class);
        assertThat(result.value(), is(-1234567890123456789L));
    }

    /*
     * Spec: "Token class: Simple literals, numbers".
     * Rule: `BigInteger` uses token `0x26` followed by 7-bit escaped binary with an unsigned VInt length.
     */
    @Test
    public void testBigIntegerZero() {
        BigIntegerModel model = new BigIntegerModel(BigInteger.ZERO);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigIntegerModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigIntegerModel.class);
        assertThat(result.value(), is(BigInteger.ZERO));
    }

    @Test
    public void testBigIntegerPositive() {
        BigInteger value = new BigInteger("1234567890123456789012345678901234567890");
        BigIntegerModel model = new BigIntegerModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigIntegerModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigIntegerModel.class);
        assertThat(result.value(), is(value));
    }

    @Test
    public void testBigIntegerNegative() {
        BigInteger value = new BigInteger("-1234567890123456789012345678901234567890");
        BigIntegerModel model = new BigIntegerModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigIntegerModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigIntegerModel.class);
        assertThat(result.value(), is(value));
    }

    @Test
    public void testBigIntegerVeryLarge() {
        BigInteger value = new BigInteger("123456789012345678901234567890123456789012345678901234567890");
        BigIntegerModel model = new BigIntegerModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigIntegerModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigIntegerModel.class);
        assertThat(result.value(), is(value));
    }

    /*
     * Spec: "Low-level Format" and "Token class: Simple literals, numbers".
     * Rule: `float` values are IEEE-754 payloads encoded in fixed-length big-endian 7-bit chunks behind token `0x28`.
     */
    @Test
    public void testFloatZero() {
        FloatModel model = new FloatModel(0.0f);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        FloatModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, FloatModel.class);
        assertThat(result.value(), is(0.0f));
    }

    @Test
    public void testFloatPositive() {
        FloatModel model = new FloatModel(123.456f);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        FloatModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, FloatModel.class);
        assertThat(result.value(), is(123.456f));
    }

    @Test
    public void testFloatNegative() {
        FloatModel model = new FloatModel(-123.456f);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        FloatModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, FloatModel.class);
        assertThat(result.value(), is(-123.456f));
    }

    @Test
    public void testFloatMin() {
        FloatModel model = new FloatModel(Float.MIN_VALUE);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        FloatModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, FloatModel.class);
        assertThat(result.value(), is(Float.MIN_VALUE));
    }

    @Test
    public void testFloatMax() {
        FloatModel model = new FloatModel(Float.MAX_VALUE);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        FloatModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, FloatModel.class);
        assertThat(result.value(), is(Float.MAX_VALUE));
    }

    @Test
    public void testFloatNaN() {
        FloatModel model = new FloatModel(Float.NaN);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        FloatModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, FloatModel.class);
        assertThat(Float.isNaN(result.value()), is(true));
    }

    @Test
    public void testFloatPositiveInfinity() {
        FloatModel model = new FloatModel(Float.POSITIVE_INFINITY);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        FloatModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, FloatModel.class);
        assertThat(result.value(), is(Float.POSITIVE_INFINITY));
    }

    @Test
    public void testFloatNegativeInfinity() {
        FloatModel model = new FloatModel(Float.NEGATIVE_INFINITY);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        FloatModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, FloatModel.class);
        assertThat(result.value(), is(Float.NEGATIVE_INFINITY));
    }

    /*
     * Spec: "Low-level Format" and "Token class: Simple literals, numbers".
     * Rule: `double` values are IEEE-754 payloads encoded in fixed-length big-endian 7-bit chunks behind token `0x29`.
     */
    @Test
    public void testDoubleZero() {
        DoubleModel model = new DoubleModel(0.0);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        DoubleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, DoubleModel.class);
        assertThat(result.value(), is(0.0));
    }

    @Test
    public void testDoublePositive() {
        DoubleModel model = new DoubleModel(123.456789);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        DoubleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, DoubleModel.class);
        assertThat(result.value(), is(123.456789));
    }

    @Test
    public void testDoubleNegative() {
        DoubleModel model = new DoubleModel(-123.456789);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        DoubleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, DoubleModel.class);
        assertThat(result.value(), is(-123.456789));
    }

    @Test
    public void testDoubleMin() {
        DoubleModel model = new DoubleModel(Double.MIN_VALUE);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        DoubleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, DoubleModel.class);
        assertThat(result.value(), is(Double.MIN_VALUE));
    }

    @Test
    public void testDoubleMax() {
        DoubleModel model = new DoubleModel(Double.MAX_VALUE);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        DoubleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, DoubleModel.class);
        assertThat(result.value(), is(Double.MAX_VALUE));
    }

    @Test
    public void testDoubleNaN() {
        DoubleModel model = new DoubleModel(Double.NaN);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        DoubleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, DoubleModel.class);
        assertThat(Double.isNaN(result.value()), is(true));
    }

    @Test
    public void testDoublePositiveInfinity() {
        DoubleModel model = new DoubleModel(Double.POSITIVE_INFINITY);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        DoubleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, DoubleModel.class);
        assertThat(result.value(), is(Double.POSITIVE_INFINITY));
    }

    @Test
    public void testDoubleNegativeInfinity() {
        DoubleModel model = new DoubleModel(Double.NEGATIVE_INFINITY);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        DoubleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, DoubleModel.class);
        assertThat(result.value(), is(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void testDoubleScientificNotation() {
        DoubleModel model = new DoubleModel(1.23e10);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        DoubleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, DoubleModel.class);
        assertThat(result.value(), is(1.23e10));
    }

    /*
     * Spec: "Token class: Simple literals, numbers".
     * Rule: `BigDecimal` uses token `0x2A`, then zigzag-encoded scale, then 7-bit escaped magnitude with unsigned
     * VInt length.
     */
    @Test
    public void testBigDecimalZero() {
        BigDecimalModel model = new BigDecimalModel(BigDecimal.ZERO);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigDecimalModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigDecimalModel.class);
        assertThat(result.value(), is(BigDecimal.ZERO));
    }

    @Test
    public void testBigDecimalPositive() {
        BigDecimal value = new BigDecimal("123.45678901234567890");
        BigDecimalModel model = new BigDecimalModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigDecimalModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigDecimalModel.class);
        assertThat(result.value(), is(value));
    }

    @Test
    public void testBigDecimalNegative() {
        BigDecimal value = new BigDecimal("-123.45678901234567890");
        BigDecimalModel model = new BigDecimalModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigDecimalModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigDecimalModel.class);
        assertThat(result.value(), is(value));
    }

    @Test
    public void testBigDecimalHighPrecision() {
        BigDecimal value = new BigDecimal("123456789012345678901234567890.123456789012345678901234567890");
        BigDecimalModel model = new BigDecimalModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigDecimalModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigDecimalModel.class);
        assertThat(result.value(), is(value));
    }

    @Test
    public void testBigDecimalLargeScale() {
        BigDecimal value = BigDecimal.valueOf(1, 100); // 0.00...001 (100 zeros)
        BigDecimalModel model = new BigDecimalModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigDecimalModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigDecimalModel.class);
        assertThat(result.value(), is(value));
    }

    @Test
    public void testBigDecimalNegativeScale() {
        BigDecimal value = new BigDecimal("1E+3");
        BigDecimalModel model = new BigDecimalModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        BigDecimalModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, BigDecimalModel.class);
        assertThat(result.value(), is(value));
        assertThat(result.value().scale(), is(value.scale()));
    }

    @Json.Entity
    record NumberModel(int value) {
    }

    @Json.Entity
    record IntModel(int value) {
    }

    @Json.Entity
    record LongModel(long value) {
    }

    @Json.Entity
    record BigIntegerModel(BigInteger value) {
    }

    @Json.Entity
    record FloatModel(float value) {
    }

    @Json.Entity
    record DoubleModel(double value) {
    }

    @Json.Entity
    record BigDecimalModel(BigDecimal value) {
    }
}
