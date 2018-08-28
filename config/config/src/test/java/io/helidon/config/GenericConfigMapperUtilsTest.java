/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import io.helidon.common.CollectionsHelper;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link GenericConfigMapperUtils}.
 */
public class GenericConfigMapperUtilsTest {

    @Test
    public void testDecapitalize() {
        assertThat(GenericConfigMapperUtils.decapitalize("a"), is("a"));
        assertThat(GenericConfigMapperUtils.decapitalize("B"), is("b"));
        assertThat(GenericConfigMapperUtils.decapitalize("cCcCc"), is("cCcCc"));
        assertThat(GenericConfigMapperUtils.decapitalize("DdDdD"), is("ddDdD"));
    }

    @Test
    public void testIsSetter() throws NoSuchMethodException {
        //false
        assertThat(isSetter("set", String.class), is(false));
        assertThat(isSetter("nastav", String.class), is(false));
        assertThat(isSetter("setVal", String.class), is(false));
        assertThat(isSetter("setVal2", String.class, String.class), is(false));
        assertThat(isSetter("init2", String.class, String.class), is(false));
        //true
        assertThat(isSetter("setValue", String.class), is(true));
        assertThat(isSetter("init", String.class), is(true));
    }

    private boolean isSetter(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = ClashBean.class.getMethod(methodName, parameterTypes);
        return GenericConfigMapperUtils.isSetter(method);
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
        ConfigException ex = Assertions.assertThrows(ConfigException.class, () -> {
            isMethodTransient("setValueClash", String.class);
        });
        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf("@Config.Value", "@Config.Transient", "setValueClash", "setter")).matches(ex.getMessage()));
    }

    private boolean isMethodTransient(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = ClashBean.class.getMethod(methodName, parameterTypes);
        return GenericConfigMapperUtils.isTransient(method);
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
        return GenericConfigMapperUtils.isAccessible(field);
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
        ConfigException ex = Assertions.assertThrows(ConfigException.class, () -> {
            isFieldTransient("valueClash");
        });
        
        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf("@Config.Value", "@Config.Transient", "field", "valueClash")).matches(ex.getMessage()));
    }

    private boolean isFieldTransient(String fieldName) throws NoSuchFieldException {
        Field field = ClashBean.class.getField(fieldName);
        return GenericConfigMapperUtils.isTransient(field);
    }

    @Test
    public void testCreate() {
        Map<String, GenericConfigMapper.PropertyAccessor> propertyAccessors =
                GenericConfigMapperUtils.getPropertyAccessors(mock(ConfigMapperManager.class), TestBean.class);

        assertThat(propertyAccessors.keySet(), hasSize(3));
        assertThat(propertyAccessors.keySet(), containsInAnyOrder("init", "value", "valueField"));
    }

    @Test
    public void testCreateAndCallSetter() throws Throwable {
        Map<String, GenericConfigMapper.PropertyAccessor> propertyAccessors =
                GenericConfigMapperUtils.getPropertyAccessors(mock(ConfigMapperManager.class), TestBean.class);

        TestBean bean = new TestBean();
        assertThat(bean.getValue(), is(nullValue()));

        assertThat(propertyAccessors.get("value").getHandle().type().parameterType(1),
                   equalTo(String.class));
        assertThat(propertyAccessors.get("init").getHandle().type().parameterType(1),
                   equalTo(String.class));

        //call setValue
        propertyAccessors.get("value").getHandle()
                .invoke(bean, "val1");
        assertThat(bean.getValue(), is("val1"));

        //call init
        propertyAccessors.get("init").getHandle()
                .invoke(bean, "val2");
        assertThat(bean.getInit(), is("val2"));
    }

    @Test
    public void testCreateAndSetField() throws Throwable {
        Map<String, GenericConfigMapper.PropertyAccessor> propertyAccessors =
                GenericConfigMapperUtils.getPropertyAccessors(mock(ConfigMapperManager.class), TestBean.class);

        TestBean bean = new TestBean();
        assertThat(bean.valueField, is(nullValue()));

        assertThat(propertyAccessors.get("valueField").getHandle().type().parameterType(1),
                   equalTo(String.class));

        propertyAccessors.get("valueField").getHandle()
                .invoke(bean, "val1");
        assertThat(bean.valueField, is("val1"));
    }

    @Test
    public void testCreateAndCallSetterListParam() throws Throwable {
        Map<String, GenericConfigMapper.PropertyAccessor> propertyAccessors =
                GenericConfigMapperUtils.getPropertyAccessors(mock(ConfigMapperManager.class), ListBean.class);

        ListBean bean = new ListBean();
        assertThat(bean.getList(), is(nullValue()));

        assertThat(propertyAccessors.get("list").getHandle().type().parameterType(1),
                   equalTo(List.class));

        propertyAccessors.get("list").getHandle()
                .invoke(bean, CollectionsHelper.listOf(23L, 42L));
        assertThat(bean.getList(), contains(23L, 42L));
    }

    @Test
    public void testCreateAndSetFieldListParam() throws Throwable {
        Map<String, GenericConfigMapper.PropertyAccessor> propertyAccessors =
                GenericConfigMapperUtils.getPropertyAccessors(mock(ConfigMapperManager.class), ListBean.class);

        ListBean bean = new ListBean();
        assertThat(bean.listField, is(nullValue()));

        assertThat(propertyAccessors.get("listField").getHandle().type().parameterType(1),
                   equalTo(List.class));

        propertyAccessors.get("listField").getHandle()
                .invoke(bean, CollectionsHelper.listOf(23L, 42L));
        assertThat(bean.listField, contains(23L, 42L));
    }

    @Test
    public void testCreateErrorMethodTransientFieldClash() {
        ConfigException ex = Assertions.assertThrows(ConfigException.class, () -> {
            GenericConfigMapperUtils.getPropertyAccessors(mock(ConfigMapperManager.class), MethodTransientFieldClashBean.class);
        });
        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf("@Config.Value", "method", "@Config.Transient", "field", "prop1")).matches(ex.getMessage()));
    }

    @Test
    public void testCreateErrorClashFieldTransientMethodClash() {
        ConfigException ex = Assertions.assertThrows(ConfigException.class, () -> {
            GenericConfigMapperUtils.getPropertyAccessors(mock(ConfigMapperManager.class), FieldTransientMethodClashBean.class);
        });
        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf("@Config.Value", "field", "@Config.Transient", "method", "prop1")).matches(ex.getMessage()));
    }

    public static class TestBean {
        // not accessible fields
        public final String valueFinal = null;
        // ok fields
        public String valueField;
        @Config.Transient
        public String valueTransient;
        private String value;
        private String init;

        // not setters
        public void set(String val) { //too short
        }

        public void nastav(String val) { //wrong setter prefix
        }

        public String setVal(String val) { //wrong return type
            return val;
        }

        public void setVal2(String val, String val2) { //wrong number of params
        }

        @Config.Value
        public TestBean init2(String val, String val2) { //wrong number of params, no matter @Config.Value
            return this;
        }

        // ok setters

        public void setValue(String value) { // not transient
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Config.Value
        public TestBean init(String init) { //builder style - needs @Config.Value
            this.init = init;
            return this;
        }

        public String getInit() {
            return init;
        }

        @Config.Transient
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
        @Config.Transient
        @Config.Value
        public String valueClash;

        // clash
        @Config.Transient
        @Config.Value
        public void setValueClash(String value) { // config annotations clash
        }
    }

    public static class MethodTransientFieldClashBean {
        @Config.Transient
        public String prop1;

        @Config.Value
        public void setProp1(String prop1) {
        }
    }

    public static class FieldTransientMethodClashBean {
        @Config.Value
        public String prop1;

        @Config.Transient
        public void setProp1(String prop1) {
        }
    }

}
