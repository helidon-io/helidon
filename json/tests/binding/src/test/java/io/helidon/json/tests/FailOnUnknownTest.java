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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class FailOnUnknownTest {

    private final JsonBinding jsonBinding;

    FailOnUnknownTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testFailOnUnknownDisabled() {
        // Without @FailOnUnknown, unknown properties should be ignored
        String json = "{\"knownField\":\"value\",\"unknownField\":\"ignored\"}";
        NoFailOnUnknown entity = jsonBinding.deserialize(json, NoFailOnUnknown.class);
        assertThat(entity.knownField, is("value"));
    }

    @Test
    public void testFailOnUnknownEnabled() {
        // With @FailOnUnknown, unknown properties should cause failure
        String json = "{\"knownField\":\"value\",\"unknownField\":\"should_fail\"}";
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(json, FailOnUnknownEnabled.class));
    }

    @Test
    public void testFailOnUnknownEnabledKnownOnly() {
        // With @FailOnUnknown, but only known properties, should succeed
        String json = "{\"knownField\":\"value\"}";
        FailOnUnknownEnabled entity = jsonBinding.deserialize(json, FailOnUnknownEnabled.class);
        assertThat(entity.knownField, is("value"));
    }

    @Json.Entity
    static class NoFailOnUnknown {
        public String knownField;
    }

    @Json.Entity
    @Json.FailOnUnknown
    static class FailOnUnknownEnabled {
        public String knownField;
    }
}
