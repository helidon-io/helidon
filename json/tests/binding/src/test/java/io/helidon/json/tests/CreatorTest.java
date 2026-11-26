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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

@Testing.Test
public class CreatorTest {

    private final JsonBinding jsonBinding;

    CreatorTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void wideCreatorCallsTheConfiguredIntegerDeserializerOnlyForKnownFields(BindingMethod method) {
        List<Integer> reads = new ArrayList<>();
        JsonBinding binding = JsonBinding.builder()
                .addDeserializer(new JsonDeserializer<Integer>() {
                    @Override
                    public GenericType<Integer> type() {
                        return GenericType.create(int.class);
                    }

                    @Override
                    public Integer deserialize(JsonParser parser) {
                        int value = parser.readInt();
                        reads.add(value);
                        return 100 + value;
                    }

                    @Override
                    public Integer deserializeNull() {
                        reads.add(-1);
                        return -1;
                    }
                })
                .build();

        WideCounters counters = method.deserialize(binding, """
                {"last":7,"unknown":{"first":99},"first":null,"last":11,"p32":23}
                """, WideCounters.class);

        assertAll(
                () -> assertThat(reads, is(List.of(7, -1, 11, 23))),
                () -> assertThat(counters.first(), is(-1)),
                () -> assertThat(counters.p31(), is(0)),
                () -> assertThat(counters.p32(), is(123)),
                () -> assertThat(counters.last(), is(111)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void wideCreatorRetainsDefaultsWhenEveryMemberIsUnknown(BindingMethod method) {
        WideCounters counters = method.deserialize(jsonBinding, "{\"unknown\":[1,{}]}", WideCounters.class);

        assertAll(
                () -> assertThat(counters.first(), is(0)),
                () -> assertThat(counters.last(), is(0)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRootConstructorParameterized(BindingMethod bindingMethod) {
        String json = "{\"str1\":\"abc\",\"str2\":\"def\",\"bigDec\":25}";
        CreatorConstructorPojo pojo = bindingMethod.deserialize(jsonBinding, json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, is("def"));
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRootFactoryMethodParameterized(BindingMethod bindingMethod) {
        String json = "{\"str1\":\"abc\",\"str2\":\"def\",\"bigDec\":25}";
        CreatorFactoryMethodPojo pojo = bindingMethod.deserialize(jsonBinding, json, CreatorFactoryMethodPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, is("def"));
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testCreatorWithNullParametersParameterized(BindingMethod bindingMethod) {
        String json = "{\"str1\":\"abc\",\"str2\":null,\"bigDec\":25}";
        CreatorConstructorPojo pojo = bindingMethod.deserialize(jsonBinding, json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, nullValue());
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testCreatorWithMissingParametersParameterized(BindingMethod bindingMethod) {
        String json = "{\"str1\":\"abc\",\"bigDec\":25}";
        CreatorConstructorPojo pojo = bindingMethod.deserialize(jsonBinding, json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, nullValue());
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testCreatorWithExtraPropertiesParameterized(BindingMethod bindingMethod) {
        String json = "{\"str1\":\"abc\",\"str2\":\"def\",\"bigDec\":25,\"extra\":\"ignored\"}";
        CreatorConstructorPojo pojo = bindingMethod.deserialize(jsonBinding, json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, is("def"));
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEmptyObjectDeserializationParameterized(BindingMethod bindingMethod) {
        String json = "{}";
        CreatorConstructorPojo pojo = bindingMethod.deserialize(jsonBinding, json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, nullValue());
        assertThat(pojo.str2, nullValue());
        assertThat(pojo.bigDec, nullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testCreatorWithPrimitiveTypesParameterized(BindingMethod bindingMethod) {
        String json = "{\"value\":42}";
        CreatorWithPrimitives pojo = bindingMethod.deserialize(jsonBinding, json, CreatorWithPrimitives.class);
        assertThat(pojo.value, is(42));
        assertThat(pojo.flag, is(false)); // default value
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testGenericCreatorParameterParameterized(BindingMethod bindingMethod) {
        String json = "{\"testPersons\": [{\"name\": \"name1\"}]}";
        Persons persons = bindingMethod.deserialize(jsonBinding, json, Persons.class);
        assertThat(persons.hiddenPersons.size(), is(1));
        assertThat(persons.hiddenPersons.iterator().next().getName(), is("name1"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testCreatorParameterNamingParameterized(BindingMethod bindingMethod) {
        String json = "{\"string\":\"someText\", \"someParam\":null }";
        ParameterNameTester result = bindingMethod.deserialize(jsonBinding, json, ParameterNameTester.class);
        assertThat(result.name, is("someText"));
        assertThat(result.secondParam, nullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testIgnoredCreatorParametersParameterized(BindingMethod bindingMethod) {
        String json = "{\"included\":\"value\",\"ignored\":\"should_be_ignored\",\"ignoredFlag\":true}";
        IgnoredCreatorParameters result = bindingMethod.deserialize(jsonBinding, json, IgnoredCreatorParameters.class);
        assertThat(result.included, is("value"));
        assertThat(result.ignored, nullValue());
        assertThat(result.ignoredFlag, is(false));

        String serialized = bindingMethod.serialize(jsonBinding, new IgnoredCreatorParameters("value", "ignored", true));
        assertThat(serialized, is("{\"included\":\"value\"}"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testIgnoredFactoryCreatorParameterParameterized(BindingMethod bindingMethod) {
        String json = "{\"included\":\"value\",\"ignoredCount\":42}";
        IgnoredFactoryCreatorParameter result = bindingMethod.deserialize(jsonBinding,
                                                                          json,
                                                                          IgnoredFactoryCreatorParameter.class);
        assertThat(result.included, is("value"));
        assertThat(result.ignoredCount, is(0));

        String serialized = bindingMethod.serialize(jsonBinding, IgnoredFactoryCreatorParameter.create("value", 42));
        assertThat(serialized, is("{\"included\":\"value\"}"));
    }

    @Json.Entity
    static final class CreatorConstructorPojo {

        public String str1;
        public String str2;
        public BigDecimal bigDec;

        @Json.Creator
        public CreatorConstructorPojo(String str1, String str2) {
            this.str1 = str1;
            this.str2 = str2;
        }

        public CreatorConstructorPojo() {
            throw new IllegalStateException("This should have not been called");
        }

    }

    @Json.Entity
    static final class CreatorFactoryMethodPojo {

        public final String str1;
        public final String str2;
        BigDecimal bigDec;

        public CreatorFactoryMethodPojo() {
            throw new IllegalStateException("This should have not been called");
        }

        public CreatorFactoryMethodPojo(String str1, String str2, BigDecimal bigDec) {
            throw new IllegalStateException("This should have not been called");
        }

        private CreatorFactoryMethodPojo(String str1, String str2) {
            this.str1 = str1;
            this.str2 = str2;
        }

        @Json.Creator
        public static CreatorFactoryMethodPojo createInstance(String str1, String str2) {
            return new CreatorFactoryMethodPojo(str1, str2);
        }
    }

    @Json.Entity
    static final class CreatorWithPrimitives {

        public final int value;
        public final boolean flag;

        @Json.Creator
        public CreatorWithPrimitives(int value) {
            this.value = value;
            this.flag = false;
        }

        public CreatorWithPrimitives() {
            throw new IllegalStateException("This should have not been called");
        }

    }

    @Json.Entity
    static final class Persons {

        final Set<Person> hiddenPersons;

        private Persons(Set<Person> persons) {
            this.hiddenPersons = persons;
        }

        @Json.Creator
        static Persons wrap(@Json.Property("testPersons") Set<Person> persons) {
            return new Persons(persons);
        }

    }

    @Json.Entity
    static final class Person {
        private String name;

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }
    }

    @Json.Entity
    static final class ParameterNameTester {

        final String name;
        final String secondParam;

        @Json.Creator
        public ParameterNameTester(String string, String someParam) {
            this.name = string;
            this.secondParam = someParam;
        }
    }

    @Json.Entity
    static final class IgnoredCreatorParameters {

        public final String included;
        public final String ignored;
        public final boolean ignoredFlag;

        @Json.Creator
        public IgnoredCreatorParameters(String included, @Json.Ignore String ignored, @Json.Ignore boolean ignoredFlag) {
            this.included = included;
            this.ignored = ignored;
            this.ignoredFlag = ignoredFlag;
        }
    }

    @Json.Entity
    static final class IgnoredFactoryCreatorParameter {

        public final String included;
        public final int ignoredCount;

        private IgnoredFactoryCreatorParameter(String included, int ignoredCount) {
            this.included = included;
            this.ignoredCount = ignoredCount;
        }

        @Json.Creator
        static IgnoredFactoryCreatorParameter create(String included, @Json.Ignore int ignoredCount) {
            return new IgnoredFactoryCreatorParameter(included, ignoredCount);
        }
    }

    @Json.Entity
    record WideCounters(int first, int p01, int p02, int p03, int p04, int p05, int p06, int p07,
                        int p08, int p09, int p10, int p11, int p12, int p13, int p14, int p15,
                        int p16, int p17, int p18, int p19, int p20, int p21, int p22, int p23,
                        int p24, int p25, int p26, int p27, int p28, int p29, int p30, int p31,
                        int p32, int p33, int p34, int p35, int p36, int p37, int p38, int p39,
                        int p40, int p41, int p42, int p43, int p44, int p45, int p46, int p47,
                        int p48, int p49, int p50, int p51, int p52, int p53, int p54, int p55,
                        int p56, int p57, int p58, int p59, int p60, int p61, int p62, int last) {
    }
}
