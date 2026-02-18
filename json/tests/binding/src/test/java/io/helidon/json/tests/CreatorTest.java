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
import java.util.Set;

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class CreatorTest {

    private final JsonBinding jsonBinding;

    CreatorTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testRootConstructor() {
        String json = "{\"str1\":\"abc\",\"str2\":\"def\",\"bigDec\":25}";
        CreatorConstructorPojo pojo = jsonBinding.deserialize(json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, is("def"));
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @Test
    public void testRootFactoryMethod() {
        String json = "{\"str1\":\"abc\",\"str2\":\"def\",\"bigDec\":25}";
        CreatorFactoryMethodPojo pojo = jsonBinding.deserialize(json, CreatorFactoryMethodPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, is("def"));
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @Test
    public void testCreatorWithNullParameters() {
        String json = "{\"str1\":\"abc\",\"str2\":null,\"bigDec\":25}";
        CreatorConstructorPojo pojo = jsonBinding.deserialize(json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, nullValue());
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @Test
    public void testCreatorWithMissingParameters() {
        String json = "{\"str1\":\"abc\",\"bigDec\":25}";
        CreatorConstructorPojo pojo = jsonBinding.deserialize(json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, nullValue());
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @Test
    public void testCreatorWithExtraProperties() {
        String json = "{\"str1\":\"abc\",\"str2\":\"def\",\"bigDec\":25,\"extra\":\"ignored\"}";
        CreatorConstructorPojo pojo = jsonBinding.deserialize(json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, is("def"));
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @Test
    public void testEmptyObjectDeserialization() {
        String json = "{}";
        CreatorConstructorPojo pojo = jsonBinding.deserialize(json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, nullValue());
        assertThat(pojo.str2, nullValue());
        assertThat(pojo.bigDec, nullValue());
    }

    @Test
    public void testCreatorWithPrimitiveTypes() {
        String json = "{\"value\":42}";
        CreatorWithPrimitives pojo = jsonBinding.deserialize(json, CreatorWithPrimitives.class);
        assertThat(pojo.value, is(42));
        assertThat(pojo.flag, is(false)); // default value
    }

    @Test
    public void testGenericCreatorParameter() {
        String json = "{\"testPersons\": [{\"name\": \"name1\"}]}";
        Persons persons = jsonBinding.deserialize(json, Persons.class);
        assertThat(persons.hiddenPersons.size(), is(1));
        assertThat(persons.hiddenPersons.iterator().next().getName(), is("name1"));
    }

    @Test
    public void testCreatorParameterNaming() {
        String json = "{\"string\":\"someText\", \"someParam\":null }";
        ParameterNameTester result = jsonBinding.deserialize(json, ParameterNameTester.class);
        assertThat(result.name, is("someText"));
        assertThat(result.secondParam, nullValue());
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

}
