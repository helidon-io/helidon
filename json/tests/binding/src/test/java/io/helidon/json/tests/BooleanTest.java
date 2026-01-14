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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class BooleanTest {

    private final JsonBinding jsonBinding;

    BooleanTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testBooleanSerialization() throws Exception {
        BooleanModel booleanModel = new BooleanModel(true, false);

        String expected = "{\"field1\":true,\"field2\":false}";
        assertThat(jsonBinding.serialize(booleanModel), is(expected));
    }

    @Test
    public void testBooleanDeserializationFromBooleanAsStringValue() throws Exception {
        BooleanModel booleanModel = jsonBinding.deserialize("{\"field1\":\"true\",\"field2\":\"true\"}", BooleanModel.class);
        assertThat(booleanModel.field1, is(true));
        assertThat(booleanModel.field2, is(true));
    }

    @Test
    public void testBooleanDeserializationFromBooleanRawValue() throws Exception {
        BooleanModel booleanModel = jsonBinding.deserialize("{\"field1\":false,\"field2\":false}", BooleanModel.class);
        assertThat(booleanModel.field1, is(false));
        assertThat(booleanModel.field2, is(false));
    }

    @Test
    public void testRawBooleans() {
        Boolean bool = jsonBinding.deserialize("true", Boolean.class);
        assertThat(bool, is(true));
        bool = jsonBinding.deserialize("true", boolean.class);
        assertThat(bool, is(true));
        bool = jsonBinding.deserialize("false", Boolean.class);
        assertThat(bool, is(false));
        bool = jsonBinding.deserialize("false", boolean.class);
        assertThat(bool, is(false));
        bool = jsonBinding.deserialize("null", Boolean.class);
        assertThat(bool, nullValue());
        bool = jsonBinding.deserialize("null", boolean.class);
        assertThat(bool, is(false));

        String result = jsonBinding.serialize(true);
        assertThat(result, is("true"));
        result = jsonBinding.serialize(false);
        assertThat(result, is("false"));
    }

    @Test
    public void testBooleanArrays() {
        boolean[] primitives = {true, false};
        Boolean[] referenceTypes = {true, false};
        String arrayJson = "[true,false]";

        assertThat(JsonBinding.create().serialize(primitives), is(arrayJson));
        assertThat(JsonBinding.create().serialize(referenceTypes), is(arrayJson));

        assertThat(jsonBinding.deserialize(arrayJson, boolean[].class), is(primitives));
        assertThat(jsonBinding.deserialize(arrayJson, Boolean[].class), is(referenceTypes));
    }

    @Test
    public void testBooleanWithLeadingWhitespace() {
        String jsonWithWhitespace = "  true";
        Boolean result = jsonBinding.deserialize(jsonWithWhitespace, Boolean.class);
        assertThat(result, is(true));

        jsonWithWhitespace = "  false";
        result = jsonBinding.deserialize(jsonWithWhitespace, Boolean.class);
        assertThat(result, is(false));
    }

    @Test
    public void testBooleanWithTrailingWhitespace() {
        String jsonWithWhitespace = "true  ";
        Boolean result = jsonBinding.deserialize(jsonWithWhitespace, Boolean.class);
        assertThat(result, is(true));

        jsonWithWhitespace = "false  ";
        result = jsonBinding.deserialize(jsonWithWhitespace, Boolean.class);
        assertThat(result, is(false));
    }

    @Test
    public void testBooleanWithTabs() {
        String jsonWithTabs = "\ttrue\t";
        Boolean result = jsonBinding.deserialize(jsonWithTabs, Boolean.class);
        assertThat(result, is(true));

        jsonWithTabs = "\tfalse\t";
        result = jsonBinding.deserialize(jsonWithTabs, Boolean.class);
        assertThat(result, is(false));
    }

    @Test
    public void testBooleanWithNewlines() {
        String jsonWithNewlines = "\ntrue\n";
        Boolean result = jsonBinding.deserialize(jsonWithNewlines, Boolean.class);
        assertThat(result, is(true));

        jsonWithNewlines = "\nfalse\n";
        result = jsonBinding.deserialize(jsonWithNewlines, Boolean.class);
        assertThat(result, is(false));
    }

    @Test
    public void testBooleanWithMixedWhitespace() {
        String jsonWithMixedWhitespace = " \t\n true \t\n ";
        Boolean result = jsonBinding.deserialize(jsonWithMixedWhitespace, Boolean.class);
        assertThat(result, is(true));

        jsonWithMixedWhitespace = " \t\n false \t\n ";
        result = jsonBinding.deserialize(jsonWithMixedWhitespace, Boolean.class);
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
