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

import org.junit.jupiter.api.Test;

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

    @Test
    public void testIgnoreField() {
        IgnoreField entity = new IgnoreField("included", "ignored");
        String json = jsonBinding.serialize(entity);
        assertThat(json, is("{\"includedField\":\"included\"}"));

        IgnoreField deserialized = jsonBinding.deserialize("{\"includedField\":\"test\",\"ignoredField\":\"should_be_ignored\"}",
                                                           IgnoreField.class);
        assertThat(deserialized.getIncludedField(), is("test"));
        assertThat(deserialized.getIgnoredField(), nullValue());
    }

    @Test
    public void testTransientField() {
        TransientField entity = new TransientField("included", "ignored");
        String json = jsonBinding.serialize(entity);
        assertThat(json, is("{\"includedField\":\"included\"}"));

        TransientField deserialized = jsonBinding.deserialize(
                "{\"includedField\":\"test\",\"ignoredField\":\"should_be_ignored\"}",
                TransientField.class);
        assertThat(deserialized.getIncludedField(), is("test"));
        assertThat(deserialized.getIgnoredField(), nullValue());
    }

    @Test
    public void testIgnoreMethod() {
        IgnoreMethod entity = new IgnoreMethod("included", "ignored");
        String json = jsonBinding.serialize(entity);
        assertThat(json, is("{\"includedField\":\"included\"}"));

        IgnoreMethod deserialized = jsonBinding.deserialize("{\"includedField\":\"test\",\"ignoredField\":\"should_be_ignored\"}",
                                                            IgnoreMethod.class);
        assertThat(deserialized.getIncludedField(), is("test"));
        assertThat(deserialized.getIgnoredField(), nullValue());
    }

    @Test
    public void testRequiredFieldPresent() {
        String json = "{\"requiredField\":\"value\",\"optionalField\":\"optional\"}";
        RequiredField entity = jsonBinding.deserialize(json, RequiredField.class);
        assertThat(entity.requiredField, is("value"));
        assertThat(entity.optionalField, is("optional"));
    }

    @Test
    public void testRequiredFieldMissing() {
        String json = "{\"optionalField\":\"optional\"}";
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(json, RequiredField.class));
    }

    @Test
    public void testRequiredFieldNull() {
        String json = "{\"requiredField\":null,\"optionalField\":\"optional\"}";
        RequiredField entity = jsonBinding.deserialize(json, RequiredField.class);
        assertThat(entity.requiredField, is(nullValue()));
        assertThat(entity.optionalField, is("optional"));
    }

    @Test
    public void testRequiredMethodPresent() {
        String json = "{\"requiredProperty\":\"value\",\"optionalProperty\":\"optional\"}";
        RequiredMethod entity = jsonBinding.deserialize(json, RequiredMethod.class);
        assertThat(entity.getRequiredProperty(), is("value"));
        assertThat(entity.getOptionalProperty(), is("optional"));
    }

    @Test
    public void testRequiredMethodMissing() {
        String json = "{\"optionalProperty\":\"optional\"}";
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(json, RequiredMethod.class));
    }

    @Test
    public void testRequiredMethodNull() {
        String json = "{\"requiredProperty\":null,\"optionalProperty\":\"optional\"}";
        RequiredMethod entity = jsonBinding.deserialize(json, RequiredMethod.class);
        assertThat(entity.getRequiredProperty(), is(nullValue()));
        assertThat(entity.getOptionalProperty(), is("optional"));
    }

    @Test
    public void testMultipleRequiredFields() {
        String json = "{\"field1\":\"value1\",\"field2\":\"value2\",\"field3\":\"value3\"}";
        MultipleRequired entity = jsonBinding.deserialize(json, MultipleRequired.class);
        assertThat(entity.field1, is("value1"));
        assertThat(entity.field2, is("value2"));
        assertThat(entity.field3, is("value3"));
    }

    @Test
    public void testMultipleRequiredFieldsOneMissing() {
        String json = "{\"field1\":\"value1\",\"field3\":\"value3\"}";
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(json, MultipleRequired.class));
    }

    @Test
    public void testIgnoreAndRequiredCombination() {
        IgnoreAndRequired entity = new IgnoreAndRequired("included", "ignored", "required");
        String json = jsonBinding.serialize(entity);
        assertThat(json, is("{\"includedField\":\"included\",\"requiredField\":\"required\"}"));

        String toDeserialize = "{\"includedField\":\"included\",\"ignoredField\":\"ignored\",\"requiredField\":\"required\"}";

        IgnoreAndRequired deserialized = jsonBinding.deserialize(toDeserialize, IgnoreAndRequired.class);
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
