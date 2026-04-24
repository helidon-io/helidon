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

import io.helidon.json.JsonException;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class IgnoreRequiredTest {

    private final JsonBinding jsonBinding;

    IgnoreRequiredTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testIgnoreFieldParameterized(BindingMethod bindingMethod) {
        IgnoreField entity = new IgnoreField("included", "ignored");
        String json = bindingMethod.serialize(jsonBinding, entity);
        assertThat(json, is("{\"includedField\":\"included\"}"));

        IgnoreField deserialized = bindingMethod.deserialize(jsonBinding,
                                                             "{\"includedField\":\"test\","
                                                                     + "\"ignoredField\":\"should_be_ignored\"}",
                                                             IgnoreField.class);
        assertThat(deserialized.getIncludedField(), is("test"));
        assertThat(deserialized.getIgnoredField(), nullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testTransientFieldParameterized(BindingMethod bindingMethod) {
        TransientField entity = new TransientField("included", "ignored");
        String json = bindingMethod.serialize(jsonBinding, entity);
        assertThat(json, is("{\"includedField\":\"included\"}"));

        TransientField deserialized = bindingMethod.deserialize(
                jsonBinding,
                "{\"includedField\":\"test\",\"ignoredField\":\"should_be_ignored\"}",
                TransientField.class);
        assertThat(deserialized.getIncludedField(), is("test"));
        assertThat(deserialized.getIgnoredField(), nullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testIgnoreMethodParameterized(BindingMethod bindingMethod) {
        IgnoreMethod entity = new IgnoreMethod("included", "ignored");
        String json = bindingMethod.serialize(jsonBinding, entity);
        assertThat(json, is("{\"includedField\":\"included\"}"));

        IgnoreMethod deserialized = bindingMethod.deserialize(jsonBinding,
                                                              "{\"includedField\":\"test\","
                                                                      + "\"ignoredField\":\"should_be_ignored\"}",
                                                              IgnoreMethod.class);
        assertThat(deserialized.getIncludedField(), is("test"));
        assertThat(deserialized.getIgnoredField(), nullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testIgnoreRecordComponentParameterized(BindingMethod bindingMethod) {
        IgnoreRecordComponent entity = new IgnoreRecordComponent("included", "ignored", "mapped");
        String json = bindingMethod.serialize(jsonBinding, entity);
        assertThat(json, is("{\"key\":\"included\",\"mapped\":\"mapped\"}"));

        IgnoreRecordComponent deserialized = bindingMethod.deserialize(jsonBinding,
                                                                      "{\"key\":\"test\","
                                                                              + "\"ignored\":\"should_be_ignored\","
                                                                              + "\"mapped\":\"mapped_test\"}",
                                                                      IgnoreRecordComponent.class);
        assertThat(deserialized.key(), is("test"));
        assertThat(deserialized.ignored(), nullValue());
        assertThat(deserialized.random(), is("mapped_test"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRequiredFieldPresentParameterized(BindingMethod bindingMethod) {
        String json = "{\"requiredField\":\"value\",\"optionalField\":\"optional\"}";
        RequiredField entity = bindingMethod.deserialize(jsonBinding, json, RequiredField.class);
        assertThat(entity.requiredField, is("value"));
        assertThat(entity.optionalField, is("optional"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRequiredFieldMissingParameterized(BindingMethod bindingMethod) {
        String json = "{\"optionalField\":\"optional\"}";
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, json, RequiredField.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRequiredFieldNullParameterized(BindingMethod bindingMethod) {
        String json = "{\"requiredField\":null,\"optionalField\":\"optional\"}";
        RequiredField entity = bindingMethod.deserialize(jsonBinding, json, RequiredField.class);
        assertThat(entity.requiredField, is(nullValue()));
        assertThat(entity.optionalField, is("optional"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRequiredMethodPresentParameterized(BindingMethod bindingMethod) {
        String json = "{\"requiredProperty\":\"value\",\"optionalProperty\":\"optional\"}";
        RequiredMethod entity = bindingMethod.deserialize(jsonBinding, json, RequiredMethod.class);
        assertThat(entity.getRequiredProperty(), is("value"));
        assertThat(entity.getOptionalProperty(), is("optional"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRequiredMethodMissingParameterized(BindingMethod bindingMethod) {
        String json = "{\"optionalProperty\":\"optional\"}";
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, json, RequiredMethod.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRequiredMethodNullParameterized(BindingMethod bindingMethod) {
        String json = "{\"requiredProperty\":null,\"optionalProperty\":\"optional\"}";
        RequiredMethod entity = bindingMethod.deserialize(jsonBinding, json, RequiredMethod.class);
        assertThat(entity.getRequiredProperty(), is(nullValue()));
        assertThat(entity.getOptionalProperty(), is("optional"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testMultipleRequiredFieldsParameterized(BindingMethod bindingMethod) {
        String json = "{\"field1\":\"value1\",\"field2\":\"value2\",\"field3\":\"value3\"}";
        MultipleRequired entity = bindingMethod.deserialize(jsonBinding, json, MultipleRequired.class);
        assertThat(entity.field1, is("value1"));
        assertThat(entity.field2, is("value2"));
        assertThat(entity.field3, is("value3"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testMultipleRequiredFieldsOneMissingParameterized(BindingMethod bindingMethod) {
        String json = "{\"field1\":\"value1\",\"field3\":\"value3\"}";
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, json, MultipleRequired.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testIgnoreAndRequiredCombinationParameterized(BindingMethod bindingMethod) {
        IgnoreAndRequired entity = new IgnoreAndRequired("included", "ignored", "required");
        String json = bindingMethod.serialize(jsonBinding, entity);
        assertThat(json, is("{\"includedField\":\"included\",\"requiredField\":\"required\"}"));

        String toDeserialize = "{\"includedField\":\"included\",\"ignoredField\":\"ignored\",\"requiredField\":\"required\"}";

        IgnoreAndRequired deserialized = bindingMethod.deserialize(jsonBinding, toDeserialize, IgnoreAndRequired.class);
        assertThat(deserialized.includedField, is("included"));
        assertThat(deserialized.ignoredField, nullValue());
        assertThat(deserialized.requiredField, is("required"));
    }

    @Json.Entity
    static class IgnoreField {
        private String includedField;
        @Json.Ignore
        private String ignoredField;

        public IgnoreField() {
        }

        public IgnoreField(String included, String ignored) {
            this.includedField = included;
            this.ignoredField = ignored;
        }

        public String getIncludedField() {
            return includedField;
        }

        public void setIncludedField(String includedField) {
            this.includedField = includedField;
        }

        public String getIgnoredField() {
            return ignoredField;
        }

        public void setIgnoredField(String ignoredField) {
            this.ignoredField = ignoredField;
        }
    }

    @Json.Entity
    static class TransientField {
        private String includedField;
        private transient String ignoredField;

        public TransientField() {
        }

        public TransientField(String included, String ignored) {
            this.includedField = included;
            this.ignoredField = ignored;
        }

        public String getIncludedField() {
            return includedField;
        }

        public void setIncludedField(String includedField) {
            this.includedField = includedField;
        }

        public String getIgnoredField() {
            return ignoredField;
        }

        public void setIgnoredField(String ignoredField) {
            this.ignoredField = ignoredField;
        }
    }

    @Json.Entity
    static class IgnoreMethod {
        private String includedField;
        private String ignoredField;

        public IgnoreMethod() {
        }

        public IgnoreMethod(String included, String ignored) {
            this.includedField = included;
            this.ignoredField = ignored;
        }

        public String getIncludedField() {
            return includedField;
        }

        public void setIncludedField(String includedField) {
            this.includedField = includedField;
        }

        @Json.Ignore
        public String getIgnoredField() {
            return ignoredField;
        }

        @Json.Ignore
        public void setIgnoredField(String ignoredField) {
            this.ignoredField = ignoredField;
        }
    }

    @Json.Entity
    record IgnoreRecordComponent(String key, @Json.Ignore String ignored, @Json.Property("mapped") String random) {
        static final String TYPE = "record";
    }

    @Json.Entity
    static class RequiredField {
        @Json.Required
        public String requiredField;
        public String optionalField;

        public RequiredField() {
        }
    }

    @Json.Entity
    static class RequiredMethod {
        private String requiredProperty;
        private String optionalProperty;

        public RequiredMethod() {
        }

        public String getOptionalProperty() {
            return optionalProperty;
        }

        public void setOptionalProperty(String optionalProperty) {
            this.optionalProperty = optionalProperty;
        }

        @Json.Required
        public String getRequiredProperty() {
            return requiredProperty;
        }

        public void setRequiredProperty(String requiredProperty) {
            this.requiredProperty = requiredProperty;
        }
    }

    @Json.Entity
    static class MultipleRequired {
        @Json.Required
        public String field1;
        @Json.Required
        public String field2;
        @Json.Required
        public String field3;
    }

    @Json.Entity
    static class IgnoreAndRequired {
        public String includedField;
        @Json.Ignore
        public String ignoredField;
        @Json.Required
        public String requiredField;

        public IgnoreAndRequired() {
        }

        public IgnoreAndRequired(String included, String ignored, String required) {
            this.includedField = included;
            this.ignoredField = ignored;
            this.requiredField = required;
        }
    }
}
