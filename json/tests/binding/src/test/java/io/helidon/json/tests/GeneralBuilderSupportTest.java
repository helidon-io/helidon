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
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class GeneralBuilderSupportTest {

    private final JsonBinding jsonBinding;

    GeneralBuilderSupportTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testBuilderSupport() {
        String json = """
                {
                    "value" : "test"
                }""";

        TestPojoWithBuilder deserialized = jsonBinding.deserialize(json, TestPojoWithBuilder.class);
        assertThat(deserialized.value(), is("test"));
    }

    @Test
    public void testBuilderWithNamePrefix() {
        String json = """
                {
                    "value" : "test"
                }""";

        TestPojoWithBuilderNamePrefix deserialized = jsonBinding.deserialize(json, TestPojoWithBuilderNamePrefix.class);
        assertThat(deserialized.value(), is("test"));
    }

    @Test
    public void testBuilderWithDifferentBuildMethod() {
        String json = """
                {
                    "value" : "test",
                    "value2" : "test2"
                }""";

        TestPojoWithSetterAndBuilder deserialized = jsonBinding.deserialize(json, TestPojoWithSetterAndBuilder.class);
        assertThat(deserialized.value(), is("test"));
        assertThat(deserialized.value2(), is("test2"));
    }

    @Json.Entity
    @Json.BuilderInfo(TestPojoWithBuilder.Builder.class)
    public static class TestPojoWithBuilder {

        @Json.Required
        private final String value;

        private TestPojoWithBuilder(Builder builder) {
            value = builder.value;
        }

        public String value() {
            return value;
        }

        public static class Builder {

            private String value;

            public Builder value(String value) {
                this.value = value;
                return this;
            }

            public TestPojoWithBuilder build() {
                return new TestPojoWithBuilder(this);
            }
        }
    }

    @Json.Entity
    @Json.BuilderInfo(value = TestPojoWithBuilderNamePrefix.Builder.class, methodPrefix = "with")
    static class TestPojoWithBuilderNamePrefix {

        private final String value;

        private TestPojoWithBuilderNamePrefix(Builder builder) {
            value = builder.value;
        }

        public String value() {
            return value;
        }

        static class Builder {

            private String value;

            public Builder withValue(String value) {
                this.value = value;
                return this;
            }

            public TestPojoWithBuilderNamePrefix build() {
                return new TestPojoWithBuilderNamePrefix(this);
            }
        }

    }

    @Json.Entity
    @Json.BuilderInfo(value = TestPojoWithBuilderDifferentBuildMethod.Builder.class, buildMethod = "create")
    static class TestPojoWithBuilderDifferentBuildMethod {

        private final String value;

        private TestPojoWithBuilderDifferentBuildMethod(Builder builder) {
            value = builder.value;
        }

        public String value() {
            return value;
        }

        static class Builder {

            private String value;

            public Builder value(String value) {
                this.value = value;
                return this;
            }

            public TestPojoWithBuilderDifferentBuildMethod create() {
                return new TestPojoWithBuilderDifferentBuildMethod(this);
            }
        }

    }

    @Json.Entity
    @Json.BuilderInfo(value = TestPojoWithSetterAndBuilder.Builder.class)
    static class TestPojoWithSetterAndBuilder {

        private final String value;
        private String value2;

        private TestPojoWithSetterAndBuilder(Builder builder) {
            value = builder.value;
        }

        public String value() {
            return value;
        }

        public String value2() {
            return value2;
        }

        public void value2(String value2) {
            this.value2 = value2;
        }

        static class Builder {

            private String value;

            public Builder value(String value) {
                this.value = value;
                return this;
            }

            public TestPojoWithSetterAndBuilder build() {
                return new TestPojoWithSetterAndBuilder(this);
            }
        }
    }

}
