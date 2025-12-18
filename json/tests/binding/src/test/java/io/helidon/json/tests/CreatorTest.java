/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CreatorTest {

    private static final JsonBinding HELIDON = Services.get(JsonBinding.class);

    @Test
    public void testRootConstructor() {
        String json = "{\"str1\":\"abc\",\"str2\":\"def\",\"bigDec\":25}";
        CreatorConstructorPojo pojo = HELIDON.deserialize(json, CreatorConstructorPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, is("def"));
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
    }

    @Test
    public void testRootFactoryMethod() {
        String json = "{\"str1\":\"abc\",\"str2\":\"def\",\"bigDec\":25}";
        CreatorFactoryMethodPojo pojo = HELIDON.deserialize(json, CreatorFactoryMethodPojo.class);
        assertThat(pojo.str1, is("abc"));
        assertThat(pojo.str2, is("def"));
        assertThat(pojo.bigDec, is(new BigDecimal("25")));
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

    //
    //    @Test
    //    public void testRootCreatorWithInnerCreator() {
    //        String json = "{\"str1\":\"abc\",\"str2\":\"def\",\"bigDec\":25, \"innerFactoryCreator\":{\"par1\":\"inn1\",
    //        \"par2\":\"inn2\",\"bigDec\":11}}";
    //        CreatorConstructorPojo pojo = defaultJsonb.fromJson(json, CreatorConstructorPojo.class);
    //        assertEquals("abc", pojo.str1);
    //        assertEquals("def", pojo.str2);
    //        assertEquals(new BigDecimal("25"), pojo.bigDec);
    //
    //        assertEquals("inn1", pojo.innerFactoryCreator.str1);
    //        assertEquals("inn2", pojo.innerFactoryCreator.str2);
    //        assertEquals(new BigDecimal("11"), pojo.innerFactoryCreator.bigDec);
    //    }
    //
    //    @Test
    //    public void testIncompatibleFactoryMethodReturnType() {
    //        try {
    //            defaultJsonb.fromJson("{\"s1\":\"abc\"}", CreatorIncompatibleTypePojo.class);
    //            fail();
    //        } catch (JsonbException e) {
    //            assertTrue(e.getMessage().startsWith("Return type of creator"));
    //        }
    //    }
    //
    //    @Test
    //    public void testMultipleCreatorsError() {
    //        try {
    //            defaultJsonb.fromJson("{\"s1\":\"abc\"}", CreatorMultipleDeclarationErrorPojo.class);
    //            fail();
    //        } catch (JsonbException e) {
    //            assertTrue(e.getMessage().startsWith("More than one @JsonbCreator"));
    //        }
    //    }
    //
    //    @Test
    //    public void testCreatorWithoutJsonbParameters1() {
    //        //arg2 is missing in json document
    //        CreatorWithoutJsonbProperty1 object = defaultJsonb.fromJson("{\"arg0\":\"abc\", \"s2\":\"def\"}",
    //                                                                    CreatorWithoutJsonbProperty1.class);
    //        assertThat(object.getPar1(), is("abc"));
    //        assertThat(object.getPar2(), is("def"));
    //        assertThat(object.getPar3(), is((byte) 0));
    //    }
    //
    //    @Test
    //    public void testCreatorWithoutJavabeanProperty() {
    //        final CreatorWithoutJavabeanProperty result = defaultJsonb.fromJson("{\"s1\":\"abc\", \"s2\":\"def\"}",
    //        CreatorWithoutJavabeanProperty.class);
    //        assertEquals("abcdef", result.getStrField());
    //
    //    }
    //
    //    @Test
    //    public void testPackagePrivateCreator() {
    //        assertThrows(JsonbException.class, () -> defaultJsonb.fromJson("{\"strVal\":\"abc\", \"intVal\":5}",
    //        CreatorPackagePrivateConstructor.class));
    //    }
    //
    //    @Test
    //    public void testLocalizedConstructor() {
    //        String json = "{\"localDate\":\"05-09-2017\"}";
    //        DateConstructor result = defaultJsonb.fromJson(json, DateConstructor.class);
    //        assertEquals(LocalDate.of(2017, 9, 5), result.localDate);
    //    }
    //
    //    @Test
    //    public void testLocalizedConstructorMergedWithProperty() {
    //        String json = "{\"localDate\":\"05-09-2017\"}";
    //        DateConstructorMergedWithProperty result = defaultJsonb.fromJson(json, DateConstructorMergedWithProperty.class);
    //        assertEquals(LocalDate.of(2017, 9, 5), result.localDate);
    //    }
    //
    //    @Test
    //    public void testLocalizedFactoryParameter() {
    //        String json = "{\"number\":\"10.000\"}";
    //        FactoryNumberParam result = defaultJsonb.fromJson(json, FactoryNumberParam.class);
    //        assertEquals(BigDecimal.TEN, result.number);
    //    }
    //
    //    @Test
    //    public void testLocalizedFactoryParameterMergedWithProperty() {
    //        String json = "{\"number\":\"10.000\"}";
    //        FactoryNumberParamMergedWithProperty result = defaultJsonb.fromJson(json, FactoryNumberParamMergedWithProperty
    //        .class);
    //        assertEquals(BigDecimal.TEN, result.number);
    //    }
    //
    //    @Test
    //    public void testCorrectCreatorParameterNames() {
    //        String json = "{\"string\":\"someText\", \"someParam\":null }";
    //        ParameterNameTester result = defaultJsonb.fromJson(json, ParameterNameTester.class);
    //        assertEquals("someText", result.name);
    //        assertNull(result.secondParam);
    //    }
    //
    //    @Test
    //    public void testGenericCreatorParameter() throws Exception {
    //        final String json = "{\"persons\": [{\"name\": \"name1\"}]}";
    //        Persons persons = defaultJsonb.fromJson(json, Persons.class);
    //        assertEquals(1, persons.hiddenPersons.size());
    //        assertEquals("name1", persons.hiddenPersons.iterator().next().getName());
    //    }
    //
    //    public static final class Persons {
    //
    //        Set<Person> hiddenPersons;
    //
    //        private Persons(Set<Person> persons) {
    //            this.hiddenPersons = persons;
    //        }
    //
    //        @JsonbCreator
    //        public static Persons wrap(@JsonbProperty("persons") Set<Person> persons) {
    //            return new Persons(persons);
    //        }
    //
    //        public Set<Person> getPersons() {
    //            return null;
    //        }
    //    }
    //
    //    public static final class Person {
    //        private String name;
    //
    //        public String getName() {
    //            return name;
    //        }
    //
    //        public void setName(String name) {
    //            this.name = name;
    //        }
    //    }
    //
    //
    //    public static final class DateConstructor {
    //        public LocalDate localDate;
    //
    //        @JsonbCreator
    //        public DateConstructor(@JsonbProperty("localDate") @JsonbDateFormat(value = "dd-MM-yyyy", locale = "nl-NL")
    //        LocalDate localDate) {
    //            this.localDate = localDate;
    //        }
    //
    //    }
    //
    //    public static final class DateConstructorMergedWithProperty {
    //        @JsonbDateFormat(value = "dd-MM-yyyy", locale = "cs-CZ")
    //        public LocalDate localDate;
    //
    //        @JsonbCreator
    //        public DateConstructorMergedWithProperty(@JsonbProperty("localDate") LocalDate localDate) {
    //            this.localDate = localDate;
    //        }
    //
    //    }
    //
    //    public static final class FactoryNumberParam {
    //        public BigDecimal number;
    //
    //        private FactoryNumberParam(BigDecimal number) {
    //            this.number = number;
    //        }
    //
    //        @JsonbCreator
    //        public static FactoryNumberParam createInstance(
    //                @JsonbProperty("number") @JsonbNumberFormat(value = "000.000", locale = "en-us")
    //                BigDecimal number) {
    //            return new FactoryNumberParam(number);
    //        }
    //
    //    }
    //
    //    public static final class FactoryNumberParamMergedWithProperty {
    //
    //        @JsonbNumberFormat(value = "000.000", locale = "en-us")
    //        public BigDecimal number;
    //
    //        private FactoryNumberParamMergedWithProperty(BigDecimal number) {
    //            this.number = number;
    //        }
    //
    //        @JsonbCreator
    //        public static FactoryNumberParamMergedWithProperty createInstance(@JsonbProperty("number") BigDecimal number) {
    //            return new FactoryNumberParamMergedWithProperty(number);
    //        }
    //
    //    }

}
