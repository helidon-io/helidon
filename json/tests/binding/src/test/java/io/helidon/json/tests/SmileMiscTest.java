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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.helidon.json.JsonException;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileConfig;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for miscellaneous Smile format features and edge cases.
 * Tests Smile binary format serialization/deserialization using JsonBinding.
 */
@Testing.Test
public class SmileMiscTest {

    private final JsonBinding jsonBinding;

    SmileMiscTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testMixedTypesInComplexStructure() {
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add("two");
        list.add(true);
        list.add(null);
        ComplexModel model = new ComplexModel(
                "text", 42, 123456789012345L, 123.456f, 123.456789, true, false, null,
                new BigInteger("123456789012345678901234567890"),
                new BigDecimal("123.456789012345678901234567890"),
                "", list,
                Map.of("nested", "value", "count", 99)
        );
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        ComplexModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, ComplexModel.class);

        // Verify all types are preserved
        assertThat(result.string(), is("text"));
        assertThat(result.integer(), is(42));
        assertThat(result.longVal(), is(123456789012345L));
        assertThat(result.floatVal(), is(123.456f));
        assertThat(result.doubleVal(), is(123.456789));
        assertThat(result.boolTrue(), is(true));
        assertThat(result.boolFalse(), is(false));
        assertThat(result.nullVal(), is(nullValue()));
        assertThat(result.bigInt(), is(new BigInteger("123456789012345678901234567890")));
        assertThat(result.bigDec(), is(new BigDecimal("123.456789012345678901234567890")));
        assertThat(result.emptyStr(), is(""));
        assertThat(result.array().size(), is(4));
        assertThat(result.array().get(0), is(1.0));
        assertThat(result.array().get(1), is("two"));
        assertThat(result.array().get(2), is(true));
        assertThat(result.array().get(3), is(nullValue()));
        assertThat(result.nestedObj().get("nested"), is("value"));
        assertThat(result.nestedObj().get("count"), is(99.0));
    }

    @Test
    public void testUnicodeEdgeCases() {
        UnicodeModel model = new UnicodeModel(
                "", "α", "Hello 世界 € 😀", "\uD83D\uDE00\uD83C\uDF0D\uD83D\uDD25",
                "e\u0301\u0302", "a\u200Bb\u200Cc", "line1\nline2\t\r\n\u0000null"
        );
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        UnicodeModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, UnicodeModel.class);

        assertThat(result.empty(), is(""));
        assertThat(result.single(), is("α"));
        assertThat(result.mixed(), is("Hello 世界 € 😀"));
        assertThat(result.high(), is("\uD83D\uDE00\uD83C\uDF0D\uD83D\uDD25"));
        assertThat(result.combining(), is("e\u0301\u0302"));
        assertThat(result.zeroWidth(), is("a\u200Bb\u200Cc"));
        assertThat(result.control(), is("line1\nline2\t\r\n\u0000null"));
    }

    @Test
    public void testNumberEdgeCases() {
        NumbersModel model = new NumbersModel(
                0, 0L, 0.0f, 0.0, -0.0f, -0.0,
                Integer.MAX_VALUE, Integer.MIN_VALUE,
                Long.MAX_VALUE, Long.MIN_VALUE,
                Float.MAX_VALUE, Float.MIN_VALUE,
                Double.MAX_VALUE, Double.MIN_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Float.NaN, Double.NaN,
                Double.MIN_VALUE, Float.MIN_VALUE
        );
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        NumbersModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, NumbersModel.class);

        assertThat(result.zeroInt(), is(0));
        assertThat(result.zeroLong(), is(0L));
        assertThat(result.zeroFloat(), is(0.0f));
        assertThat(result.zeroDouble(), is(0.0));
        assertThat(result.negZeroFloat(), is(-0.0f));
        assertThat(result.negZeroDouble(), is(-0.0));
        assertThat(result.maxInt(), is(Integer.MAX_VALUE));
        assertThat(result.minInt(), is(Integer.MIN_VALUE));
        assertThat(result.maxLong(), is(Long.MAX_VALUE));
        assertThat(result.minLong(), is(Long.MIN_VALUE));
        assertThat(result.maxFloat(), is(Float.MAX_VALUE));
        assertThat(result.minFloat(), is(Float.MIN_VALUE));
        assertThat(result.maxDouble(), is(Double.MAX_VALUE));
        assertThat(result.minDouble(), is(Double.MIN_VALUE));
        assertThat(result.infFloat(), is(Float.POSITIVE_INFINITY));
        assertThat(result.negInfFloat(), is(Float.NEGATIVE_INFINITY));
        assertThat(result.infDouble(), is(Double.POSITIVE_INFINITY));
        assertThat(result.negInfDouble(), is(Double.NEGATIVE_INFINITY));
        assertThat(Float.isNaN(result.nanFloat()), is(true));
        assertThat(Double.isNaN(result.nanDouble()), is(true));
        assertThat(result.minValDouble(), is(Double.MIN_VALUE));
        assertThat(result.minValFloat(), is(Float.MIN_VALUE));
    }

    @Test
    public void testRepeatedSerialization() {
        SimpleModel model = new SimpleModel("test", 123, List.of(1, 2, 3));
        byte[] smileData1 = SmileBindingSupport.serializeSmile(jsonBinding, model);
        SimpleModel result1 = SmileBindingSupport.deserializeSmile(jsonBinding, smileData1, SimpleModel.class);

        byte[] smileData2 = SmileBindingSupport.serializeSmile(jsonBinding, model);
        SimpleModel result2 = SmileBindingSupport.deserializeSmile(jsonBinding, smileData2, SimpleModel.class);

        // Results should be identical
        assertThat(result1, is(result2));

        // Third serialization of the result (round-trip)
        byte[] smileData3 = SmileBindingSupport.serializeSmile(jsonBinding, result1);
        SimpleModel result3 = SmileBindingSupport.deserializeSmile(jsonBinding, smileData3, SimpleModel.class);

        assertThat(result1, is(result3));
    }

    @Test
    public void testEmptyKeyNames() {
        EmptyKeyModel model = new EmptyKeyModel("empty_value", "normal_value");
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        EmptyKeyModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, EmptyKeyModel.class);

        assertThat(result.emptyKey(), is("empty_value"));
        assertThat(result.normalKey(), is("normal_value"));
    }

    @Test
    public void testOptionalEndOfContentMarkerIsAccepted() {
        SimpleModel model = new SimpleModel("test", 123, List.of(1, 2, 3));
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        byte[] framed = Arrays.copyOf(smileData, smileData.length + 1);
        framed[framed.length - 1] = (byte) 0xFF;

        SimpleModel result = SmileBindingSupport.deserializeSmile(jsonBinding, framed, SimpleModel.class);
        assertThat(result, is(model));
    }

    @Test
    public void testIncompleteHeaderFails() {
        byte[] truncatedHeader = new byte[] {0x3A, 0x29, 0x0A};
        assertThrows(JsonException.class, () -> SmileBindingSupport.deserializeSmile(jsonBinding, truncatedHeader, Object.class));
    }

    @Test
    public void testIncompleteVIntNumberFails() {
        // Header + TOKEN_INT32 (0x24) without any VInt payload bytes.
        byte[] truncatedNumber = new byte[] {0x3A, 0x29, 0x0A, 0x01, 0x24};
        assertThrows(JsonException.class, () -> SmileBindingSupport.deserializeSmile(jsonBinding, truncatedNumber, Integer.class));
    }

    @Test
    public void testIncompleteFloatPayloadFails() {
        // Header + TOKEN_FLOAT32 (0x28) with only 2 out of 5 required data bytes.
        byte[] truncatedFloat = new byte[] {0x3A, 0x29, 0x0A, 0x01, 0x28, 0x00, 0x00};
        assertThrows(JsonException.class, () -> SmileBindingSupport.deserializeSmile(jsonBinding, truncatedFloat, Float.class));
    }

    @Test
    public void testMissingLongStringTerminatorFails() {
        // Header + long-unicode string token (0xE4) + bytes without terminating 0xFC marker.
        byte[] unterminatedLongString = new byte[] {0x3A, 0x29, 0x0A, 0x01, (byte) 0xE4, 'h', 'i'};
        assertThrows(JsonException.class, () -> SmileBindingSupport.deserializeSmile(jsonBinding, unterminatedLongString, String.class));
    }

    @Test
    public void testLongSharedValueReferenceLowByteFeFails() {
        // Header enables shared values (bit 0x02), then long shared-value ref token 0xEC + forbidden low byte 0xFE.
        byte[] invalid = new byte[] {0x3A, 0x29, 0x0A, 0x03, (byte) 0xEC, (byte) 0xFE};
        assertThrows(JsonException.class, () -> SmileBindingSupport.deserializeSmile(jsonBinding, invalid, String.class));
    }

    @Test
    public void testLongSharedKeyReferenceLowByteFfFails() {
        // Object start + long shared-key ref token 0x30 + forbidden low byte 0xFF.
        byte[] invalid = new byte[] {0x3A, 0x29, 0x0A, 0x03, (byte) 0xFA, 0x30, (byte) 0xFF};
        assertThrows(JsonException.class, () -> SmileBindingSupport.deserializeSmile(jsonBinding, invalid, Map.class));
    }

    @Test
    public void testSharedValueReferenceDisabledByHeaderFails() {
        // Header has only shared-keys enabled (0x01), but payload uses short shared-value reference token 0x01.
        byte[] invalid = new byte[] {0x3A, 0x29, 0x0A, 0x01, 0x01};
        assertThrows(JsonException.class, () -> SmileBindingSupport.deserializeSmile(jsonBinding, invalid, String.class));
    }

    @Test
    public void testSharedKeyReferenceDisabledByHeaderFails() {
        // Header has only shared-values enabled (0x02), but payload uses short shared-key reference token 0x40.
        byte[] invalid = new byte[] {0x3A, 0x29, 0x0A, 0x02, (byte) 0xFA, 0x40};
        assertThrows(JsonException.class, () -> SmileBindingSupport.deserializeSmile(jsonBinding, invalid, Map.class));
    }

    @Test
    public void testSharedValueWindowResetsAfter1024Entries() {
        SmileConfig config = SmileConfig.builder()
                .sharedValueStrings(true)
                .build();

        List<String> values = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            values.add("v" + i);
        }
        values.add("v0");

        StringListModel result = SmileBindingSupport.deserializeSmile(jsonBinding,
                                                                      SmileBindingSupport.serializeSmile(jsonBinding,
                                                                                                        new StringListModel(values),
                                                                                                        config),
                                                                      StringListModel.class);

        assertThat(result.values().size(), is(1025));
        assertThat(result.values().getFirst(), is("v0"));
        assertThat(result.values().getLast(), is("v0"));
    }

    @Test
    public void testSharedKeyWindowResetsAfter1024Entries() {
        Map<String, Integer> values = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 1024; i++) {
            values.put("k" + i, i);
        }
        values.put("k0", 999);

        KeyMapModel result = SmileBindingSupport.deserializeSmile(jsonBinding,
                                                                  SmileBindingSupport.serializeSmile(jsonBinding,
                                                                                                    new KeyMapModel(values)),
                                                                  KeyMapModel.class);

        assertThat(result.values().get("k0"), is(999));
        assertThat(result.values().get("k1023"), is(1023));
    }

    @Json.Entity
    record ComplexModel(String string, int integer, long longVal, float floatVal, double doubleVal,
                        boolean boolTrue, boolean boolFalse, Object nullVal,
                        BigInteger bigInt, BigDecimal bigDec, String emptyStr,
                        @Json.SerializeNulls List<Object> array, Map<String, Object> nestedObj) {
    }

    @Json.Entity
    record UnicodeModel(String empty, String single, String mixed, String high,
                        String combining, String zeroWidth, String control) {
    }

    @Json.Entity
    record NumbersModel(int zeroInt, long zeroLong, float zeroFloat, double zeroDouble,
                        float negZeroFloat, double negZeroDouble,
                        int maxInt, int minInt, long maxLong, long minLong,
                        float maxFloat, float minFloat, double maxDouble, double minDouble,
                        float infFloat, float negInfFloat, double infDouble, double negInfDouble,
                        float nanFloat, double nanDouble, double minValDouble, float minValFloat) {
    }

    @Json.Entity
    record SimpleModel(String test, int number, List<Integer> array) {
    }

    @Json.Entity
    record EmptyKeyModel(String emptyKey, String normalKey) {
    }

    @Json.Entity
    record StringListModel(List<String> values) {
    }

    @Json.Entity
    record KeyMapModel(Map<String, Integer> values) {
    }
}