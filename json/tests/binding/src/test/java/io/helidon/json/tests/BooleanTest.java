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

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class BooleanTest {

    private final JsonBinding jsonBinding;

    BooleanTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanSerializationParameterized(BindingMethod bindingMethod) {
        BooleanModel booleanModel = new BooleanModel(true, false);

        String expected = "{\"field1\":true,\"field2\":false}";
        assertThat(bindingMethod.serialize(jsonBinding, booleanModel), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanDeserializationFromBooleanAsStringValueParameterized(BindingMethod bindingMethod) {
        BooleanModel booleanModel = bindingMethod.deserialize(jsonBinding,
                                                              "{\"field1\":\"true\",\"field2\":\"true\"}",
                                                              BooleanModel.class);
        assertThat(booleanModel.field1, is(true));
        assertThat(booleanModel.field2, is(true));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanDeserializationFromBooleanRawValueParameterized(BindingMethod bindingMethod) {
        BooleanModel booleanModel = bindingMethod.deserialize(jsonBinding,
                                                              "{\"field1\":false,\"field2\":false}",
                                                              BooleanModel.class);
        assertThat(booleanModel.field1, is(false));
        assertThat(booleanModel.field2, is(false));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRawBooleansParameterized(BindingMethod bindingMethod) {
        Boolean bool = bindingMethod.deserialize(jsonBinding, "true", Boolean.class);
        assertThat(bool, is(true));
        bool = bindingMethod.deserialize(jsonBinding, "true", boolean.class);
        assertThat(bool, is(true));
        bool = bindingMethod.deserialize(jsonBinding, "false", Boolean.class);
        assertThat(bool, is(false));
        bool = bindingMethod.deserialize(jsonBinding, "false", boolean.class);
        assertThat(bool, is(false));
        bool = bindingMethod.deserialize(jsonBinding, "null", Boolean.class);
        assertThat(bool, nullValue());
        bool = bindingMethod.deserialize(jsonBinding, "null", boolean.class);
        assertThat(bool, is(false));

        String result = bindingMethod.serialize(jsonBinding, true);
        assertThat(result, is("true"));
        result = bindingMethod.serialize(jsonBinding, false);
        assertThat(result, is("false"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanArraysParameterized(BindingMethod bindingMethod) {
        boolean[] primitives = {true, false};
        Boolean[] referenceTypes = {true, false};
        String arrayJson = "[true,false]";

        assertThat(bindingMethod.serialize(jsonBinding, primitives), is(arrayJson));
        assertThat(bindingMethod.serialize(jsonBinding, referenceTypes), is(arrayJson));

        assertThat(bindingMethod.deserialize(jsonBinding, arrayJson, boolean[].class), is(primitives));
        assertThat(bindingMethod.deserialize(jsonBinding, arrayJson, Boolean[].class), is(referenceTypes));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanWithLeadingWhitespaceParameterized(BindingMethod bindingMethod) {
        String jsonWithWhitespace = "  true";
        Boolean result = bindingMethod.deserialize(jsonBinding, jsonWithWhitespace, Boolean.class);
        assertThat(result, is(true));

        jsonWithWhitespace = "  false";
        result = bindingMethod.deserialize(jsonBinding, jsonWithWhitespace, Boolean.class);
        assertThat(result, is(false));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanWithTrailingWhitespaceParameterized(BindingMethod bindingMethod) {
        String jsonWithWhitespace = "true  ";
        Boolean result = bindingMethod.deserialize(jsonBinding, jsonWithWhitespace, Boolean.class);
        assertThat(result, is(true));

        jsonWithWhitespace = "false  ";
        result = bindingMethod.deserialize(jsonBinding, jsonWithWhitespace, Boolean.class);
        assertThat(result, is(false));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanWithTabsParameterized(BindingMethod bindingMethod) {
        String jsonWithTabs = "\ttrue\t";
        Boolean result = bindingMethod.deserialize(jsonBinding, jsonWithTabs, Boolean.class);
        assertThat(result, is(true));

        jsonWithTabs = "\tfalse\t";
        result = bindingMethod.deserialize(jsonBinding, jsonWithTabs, Boolean.class);
        assertThat(result, is(false));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanWithNewlinesParameterized(BindingMethod bindingMethod) {
        String jsonWithNewlines = "\ntrue\n";
        Boolean result = bindingMethod.deserialize(jsonBinding, jsonWithNewlines, Boolean.class);
        assertThat(result, is(true));

        jsonWithNewlines = "\nfalse\n";
        result = bindingMethod.deserialize(jsonBinding, jsonWithNewlines, Boolean.class);
        assertThat(result, is(false));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanWithMixedWhitespaceParameterized(BindingMethod bindingMethod) {
        String jsonWithMixedWhitespace = " \t\n true \t\n ";
        Boolean result = bindingMethod.deserialize(jsonBinding, jsonWithMixedWhitespace, Boolean.class);
        assertThat(result, is(true));

        jsonWithMixedWhitespace = " \t\n false \t\n ";
        result = bindingMethod.deserialize(jsonBinding, jsonWithMixedWhitespace, Boolean.class);
        assertThat(result, is(false));
    }

    @Json.Entity
    public static class BooleanModel {
        public Boolean field1;
        public boolean field2;

        public BooleanModel() {
        }

        public BooleanModel(boolean field1, Boolean field2) {
            this.field2 = field2;
            this.field1 = field1;
        }
    }
}
