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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class AnnotationsTest {

    private final JsonBinding jsonBinding;

    AnnotationsTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWithoutEntityParameterized(BindingMethod bindingMethod) {
        assertThrows(IllegalStateException.class, () -> bindingMethod.serialize(jsonBinding, new WithoutEntity()));
        assertThrows(IllegalStateException.class, () -> bindingMethod.deserialize(jsonBinding, "{}", WithoutEntity.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWithEntityParameterized(BindingMethod bindingMethod) {
        String json = bindingMethod.serialize(jsonBinding, new WithEntity());
        assertThat(json, is("{}"));

        WithEntity deserialized = bindingMethod.deserialize(jsonBinding, json, WithEntity.class);
        assertThat(deserialized, notNullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testPropertyNameOnTheFieldParameterized(BindingMethod bindingMethod) {
        PropertyNameChangeField entity = new PropertyNameChangeField();
        entity.property1 = "property1";
        entity.property2 = "property2";

        String json = bindingMethod.serialize(jsonBinding, entity);
        assertThat(json, is("{\"property1\":\"property1\",\"prop2\":\"property2\"}"));

        PropertyNameChangeField deserialize = bindingMethod.deserialize(jsonBinding, json, PropertyNameChangeField.class);
        assertThat(deserialize, notNullValue());
        assertThat(deserialize.property1, is("property1"));
        assertThat(deserialize.property2, is("property2"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testPropertyNameOnTheAccessorParameterized(BindingMethod bindingMethod) {
        PropertyNameChangeAccessor entity = new PropertyNameChangeAccessor();
        entity.property1 = "property1";
        entity.property2 = "property2";

        String json = bindingMethod.serialize(jsonBinding, entity);
        assertThat(json, is("{\"property1\":\"property1\",\"prop2\":\"property2\"}"));

        PropertyNameChangeAccessor deserialize = bindingMethod.deserialize(jsonBinding, json, PropertyNameChangeAccessor.class);
        assertThat(deserialize, notNullValue());
        assertThat(deserialize.property1, nullValue());
        assertThat(deserialize.property2, nullValue());

        String toDeserialize = "{\"prop1\":\"property1\",\"property2\":\"property2\"}";
        deserialize = bindingMethod.deserialize(jsonBinding, toDeserialize, PropertyNameChangeAccessor.class);
        assertThat(deserialize.property1, is("property1"));
        assertThat(deserialize.property2, is("property2"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testPropertyNameOverrideParameterized(BindingMethod bindingMethod) {
        PropertyNameOverride entity = new PropertyNameOverride();
        entity.property1 = "property1";
        entity.property2 = "property2";

        String json = bindingMethod.serialize(jsonBinding, entity);
        assertThat(json, is("{\"myProperty1\":\"property1\",\"prop2\":\"property2\"}"));

        PropertyNameOverride deserialize = bindingMethod.deserialize(jsonBinding, json, PropertyNameOverride.class);
        assertThat(deserialize, notNullValue());
        assertThat(deserialize.property1, nullValue());
        assertThat(deserialize.property2, nullValue());

        String toDeserialize = "{\"prop1\":\"property1\",\"myProperty2\":\"property2\"}";
        deserialize = bindingMethod.deserialize(jsonBinding, toDeserialize, PropertyNameOverride.class);
        assertThat(deserialize.property1, is("property1"));
        assertThat(deserialize.property2, is("property2"));
    }

    static class WithoutEntity {
    }

    @Json.Entity
    static class WithEntity {
    }

    @Json.Entity
    static class PropertyNameChangeField {
        public String property1;
        @Json.Property("prop2")
        public String property2;
    }

    @Json.Entity
    static class PropertyNameChangeAccessor {
        private String property1;
        private String property2;

        public String getProperty1() {
            return property1;
        }

        @Json.Property("prop1")
        public void setProperty1(String property1) {
            this.property1 = property1;
        }

        @Json.Property("prop2")
        public String getProperty2() {
            return property2;
        }

        public void setProperty2(String property2) {
            this.property2 = property2;
        }
    }

    @Json.Entity
    static class PropertyNameOverride {
        @Json.Property("myProperty1")
        private String property1;
        @Json.Property("myProperty2")
        private String property2;

        public String getProperty1() {
            return property1;
        }

        @Json.Property("prop1")
        public void setProperty1(String property1) {
            this.property1 = property1;
        }

        @Json.Property("prop2")
        public String getProperty2() {
            return property2;
        }

        public void setProperty2(String property2) {
            this.property2 = property2;
        }
    }
}
