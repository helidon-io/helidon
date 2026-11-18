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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.helidon.common.GenericType;
import io.helidon.json.JsonException;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileParser;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class BigIntegerBindingTest {
    private final JsonBinding binding;

    BigIntegerBindingTest(JsonBinding binding) {
        this.binding = binding;
    }

    @ParameterizedTest
    @CsvSource({"1.0, 1", "1e3, 1000", "1.9, 1", "-1.9, -1", "1e-3, 0",
                "9223372036854775808, 9223372036854775808"})
    void convertsNumericValuesAcrossFormats(String literal, String integer) {
        BigInteger expected = new BigInteger(integer);
        for (BindingMethod method : BindingMethod.values()) {
            assertThat(method.name(), method.deserialize(binding, literal, BigInteger.class), is(expected));
        }
        assertThat(binding.deserialize(JsonParser.create(literal).readJsonValue(), BigInteger.class), is(expected));

        byte[] smile = SmileBindingSupport.serializeSmile(binding, new BigDecimal(literal));
        assertThat(SmileBindingSupport.deserializeSmile(binding, smile, BigInteger.class), is(expected));
        assertThat(binding.deserialize(SmileParser.create(new ByteArrayInputStream(smile), 5), BigInteger.class), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void convertsGeneratedBeanField(BindingMethod method) {
        String json = "{\"value\":1e3,\"description\":\"after\"}";
        MiscConvertersTest.BigIntegerBean bean = method.deserialize(binding, json, MiscConvertersTest.BigIntegerBean.class);

        assertThat(bean.getValue(), is(BigInteger.valueOf(1000)));
        assertThat(bean.getDescription(), is("after"));
    }

    @Test
    void convertsGeneratedBeanFieldFromSmileFloatingPoint() {
        byte[] smile = SmileBindingSupport.serializeSmile(binding, Map.of("value", 1e20, "description", "after"));
        MiscConvertersTest.BigIntegerBean bean = SmileBindingSupport.deserializeSmile(binding,
                                                                                     smile,
                                                                                     MiscConvertersTest.BigIntegerBean.class);

        assertThat(bean.getValue(), is(BigInteger.TEN.pow(20)));
        assertThat(bean.getDescription(), is("after"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void preservesQuotedMapKeys(BindingMethod method) {
        GenericType<Map<BigInteger, String>> type = new GenericType<>() { };
        Map<BigInteger, String> result = method.deserialize(binding, "{\"9223372036854775808\":\"value\"}", type);

        assertThat(result, is(Map.of(new BigInteger("9223372036854775808"), "value")));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsExcessiveExpansion(BindingMethod method) {
        assertThrows(JsonException.class, () -> method.deserialize(binding, "1e4097", BigInteger.class));
        GenericType<List<BigInteger>> type = new GenericType<>() { };
        String json = "[" + "1e4096,".repeat(16) + "1e4096]";

        assertThrows(JsonException.class, () -> method.deserialize(binding, json, type));
    }
}
