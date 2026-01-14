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
public class HelidonBuilderSupportTest {

    private final JsonBinding jsonBinding;

    HelidonBuilderSupportTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testHelidonBuilderSupport() {
        String json = """
                {
                    "value" : "test"
                }""";

        TestPojoWithBuilder deserialized = jsonBinding.deserialize(json, TestPojoWithBuilder.class);
        assertThat(deserialized.value(), is("test"));
    }

    @Json.Entity
    static class TestPojoWithBuilder {

        private final String value;

        private TestPojoWithBuilder(Builder builder) {
            value = builder.value;
        }

        static Builder builder() {
            return new Builder();
        }

        String value() {
            return value;
        }

        static class Builder implements io.helidon.common.Builder<Builder, TestPojoWithBuilder> {

            private String value;

            Builder value(String value) {
                this.value = value;
                return this;
            }

            @Override
            public TestPojoWithBuilder build() {
                return new TestPojoWithBuilder(this);
            }
        }
    }

}
