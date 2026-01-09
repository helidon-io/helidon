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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testing.Test
public class BasicTest {

    private static final String EXPECTED_VALUE = "{\"value\":\"abc\"}";
    private final JsonBinding jsonBinding;

    BasicTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testSimpleSerialize() {
        StringWrapper wrapper = new StringWrapper();
        wrapper.setValue("abc");
        String val = jsonBinding.serialize(wrapper);
        assertThat(val, is(EXPECTED_VALUE));
    }

    @Test
    public void testSimpleDeserializer() {
        StringWrapper stringWrapper = jsonBinding.deserialize(EXPECTED_VALUE, StringWrapper.class);
        assertEquals("abc", stringWrapper.value);
    }

    @Json.Entity
    static class StringWrapper {

        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

}
