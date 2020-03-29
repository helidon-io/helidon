/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.objectmapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import io.helidon.config.ConfigException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link ReflectionUtil}.
 */
public class ReflectionUtilTest {

    @Test
    public void testDecapitalize() {
        assertThat(ReflectionUtil.decapitalize("a"), is("a"));
        assertThat(ReflectionUtil.decapitalize("B"), is("b"));
        assertThat(ReflectionUtil.decapitalize("cCcCc"), is("cCcCc"));
        assertThat(ReflectionUtil.decapitalize("DdDdD"), is("ddDdD"));
    }

    @Test
    public void testIsSetter() {
        //false
        assertAll(
                () -> assertThat("Set is fine", isSetter("set", String.class), is(true)),
                () -> assertThat("Any string is fine as long as it has a single parameter and void return",
                                 isSetter("nastav", String.class),
                                 is(true)),
                () -> assertThat("Wrong return type to be a setter", isSetter("setVal", String.class), is(false)),
                () -> assertThat("Wrong number of params to be a setter",
                                 isSetter("setVal2", String.class, String.class),
                                 is(false)),
                () -> assertThat("Wrong number of params to be a setter",
                                 isSetter("init2", String.class, String.class),
                                 is(false)),
                //true
                () -> assertThat("Correct setter definition", isSetter("setValue", String.class), is(true)),
                () -> assertThat("Correct setter definition", isSetter("init", String.class), is(true))
        );
    }

    private boolean isSetter(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = ClashBean.class.getMethod(methodName, parameterTypes);
        return ReflectionUtil.isSetter(ClashBean.class, method);
    }

    @Test
    public void testMethodIsTransientFalse() throws NoSuchMethodException {
        assertThat(isMethodTransient("setValue", String.class), is(false));
    }

    @Test
    public void testMethodIsTransientTrue() throws NoSuchMethodException {
        assertThat(isMethodTransient("setValueTransient", String.class), is(true));
    }

    @Test
    public void testMethodIsTransientError() throws NoSuchMethodException {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            isMethodTransient("setValueClash", String.class);
        });
        assertThat(ex.getMessage(), stringContainsInOrder(List.of("@Value", "@Transient", "setValueClash")));
    }

    private boolean isMethodTransient(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = ClashBean.class.getMethod(methodName, parameterTypes);
        return ReflectionUtil.isTransient(method, "method " + method.getName());
    }

    @Test
    public void testIsAccessible() throws NoSuchFieldException {
        //false
        assertThat(isAccessible("valueFinal"), is(false));
        //true
        assertThat(isAccessible("valueField"), is(true));
    }

    private boolean isAccessible(String fieldName) throws NoSuchFieldException {
        Field field = ClashBean.class.getField(fieldName);
        return ReflectionUtil.isAccessible(field);
    }

    @Test
    public void testFieldIsTransientFalse() throws NoSuchFieldException {
        assertThat(isFieldTransient("valueField"), is(false));
    }

    @Test
    public void testFieldIsTransientTrue() throws NoSuchFieldException {
        assertThat(isFieldTransient("valueTransient"), is(true));
    }

    @Test
    public void testFieldIsTransientError() throws NoSuchFieldException {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            isFieldTransient("valueClash");
        });

        assertThat(ex.getMessage(),
                   stringContainsInOrder(List.of("@Value", "@Transient", "field", "valueClash")));
    }

    private boolean isFieldTransient(String fieldName) throws NoSuchFieldException {
        Field field = ClashBean.class.getField(fieldName);
        return ReflectionUtil.isTransient(field, "field " + field.getName());
    }

    @Test
    public void testCreate() {
        Map<String, ReflectionUtil.PropertyAccessor<?>> propertyAccessors =
                ReflectionUtil.getPropertyAccessors(TestBean.class);

        String[] expectedKeys = {
                // method init - fluent API with @Value annotation
                "init",
                // method port - fluent API without annotation
                "port",
                // method nastav - a single argument void return type
                "nastav",
                // field valueField - public field
                "valueField",
                // method value - classical setter
                "value",
                // method set - single parameter, void return - this may be a setter...
                "set"
        };

        assertAll(
                () -> assertThat(propertyAccessors.keySet(), hasSize(expectedKeys.length)),
                () -> assertThat(propertyAccessors.keySet(), containsInAnyOrder(expectedKeys))
        );
    }

    @Test
    public void testCreateAndCallSetter() throws Throwable {
        Map<String, ReflectionUtil.PropertyAccessor<?>> propertyAccessors =
                ReflectionUtil.getPropertyAccessors(TestBean.class);

        TestBean bean = new TestBean();
        assertThat(bean.getValue(), is(nullValue()));

        assertThat(propertyAccessors.get("value").handle().type().parameterType(1),
                   equalTo(String.class));
        assertThat(propertyAccessors.get("init").handle().type().parameterType(1),
                   equalTo(String.class));

        //call setValue
        propertyAccessors.get("value").handle()
                .invoke(bean, "val1");
        assertThat(bean.getValue(), is("val1"));

        //call init
        propertyAccessors.get("init").handle()
                .invoke(bean, "val2");
        assertThat(bean.getInit(), is("val2"));
    }

    @Test
    public void testCreateAndSetField() throws Throwable {
        Map<String, ReflectionUtil.PropertyAccessor<?>> propertyAccessors =
                ReflectionUtil.getPropertyAccessors(TestBean.class);

        TestBean bean = new TestBean();
        assertThat(bean.valueField, is(nullValue()));

        assertThat(propertyAccessors.get("valueField").handle().type().parameterType(1),
                   equalTo(String.class));

        propertyAccessors.get("valueField").handle()
                .invoke(bean, "val1");
        assertThat(bean.valueField, is("val1"));
    }

    @Test
    public void testCreateAndCallSetterListParam() throws Throwable {
        Map<String, ReflectionUtil.PropertyAccessor<?>> propertyAccessors =
                ReflectionUtil.getPropertyAccessors(ListBean.class);

        ListBean bean = new ListBean();
        assertThat(bean.getList(), is(nullValue()));

        assertThat(propertyAccessors.get("list").handle().type().parameterType(1),
                   equalTo(List.class));

        propertyAccessors.get("list").handle()
                .invoke(bean, List.of(23L, 42L));
        assertThat(bean.getList(), contains(23L, 42L));
    }

    @Test
    public void testCreateAndSetFieldListParam() throws Throwable {
        Map<String, ReflectionUtil.PropertyAccessor<?>> propertyAccessors =
                ReflectionUtil.getPropertyAccessors(ListBean.class);

        ListBean bean = new ListBean();
        assertThat(bean.listField, is(nullValue()));

        assertThat(propertyAccessors.get("listField").handle().type().parameterType(1),
                   equalTo(List.class));

        propertyAccessors.get("listField").handle()
                .invoke(bean, List.of(23L, 42L));
        assertThat(bean.listField, contains(23L, 42L));
    }

    @Test
    public void testCreateErrorMethodTransientFieldClash() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            ReflectionUtil.getPropertyAccessors(MethodTransientFieldClashBean.class);
        });
        assertThat(ex.getMessage(),
                   stringContainsInOrder(List.of("@Value", "method", "@Transient", "prop1")));
    }

    @Test
    public void testCreateErrorClashFieldTransientMethodClash() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            ReflectionUtil.getPropertyAccessors(FieldTransientMethodClashBean.class);
        });
        assertThat(ex.getMessage(),
                   stringContainsInOrder(List.of("@Value", "@Transient", "method", "prop1", "property")));
    }

    public static class TestBean {
        // not accessible fields
        public final String valueFinal = null;
        // ok fields
        public String valueField;
        @Transient
        public String valueTransient;

        // private fields - accessed through a method
        private String value;
        private String init;

        // not setters
        public String setVal(String val) { //wrong return type
            return val;
        }

        public void setVal2(String val, String val2) { //wrong number of params
        }

        @Value
        public TestBean init2(String val, String val2) { //wrong number of params, no matter @Value
            return this;
        }

        // ok setters
        public void set(String val) { //too short
        }

        public void nastav(String val) { //wrong setter prefix
        }

        public void setValue(String value) { // not transient
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        // this is ok - fluent API
        public TestBean port(int port) {
            return this;
        }

        @Value
        public TestBean init(String init) { //builder style
            this.init = init;
            return this;
        }

        public String getInit() {
            return init;
        }

        @Transient
        public void setValueTransient(String value) { // transient
        }
    }

    public static class ListBean {
        public List<Long> listField;
        private List<Long> list;

        public List<Long> getList() {
            return list;
        }

        public void setList(List<Long> list) {
            this.list = list;
        }
    }

    public static class ClashBean extends TestBean {
        // clash
        @Transient
        @Value
        public String valueClash;

        // clash
        @Transient
        @Value
        public void setValueClash(String value) { // config annotations clash
        }
    }

    public static class MethodTransientFieldClashBean {
        @Transient
        public String prop1;

        @Value
        public void setProp1(String prop1) {
        }
    }

    public static class FieldTransientMethodClashBean {
        @Value
        public String prop1;

        @Transient
        public void setProp1(String prop1) {
        }
    }

}
