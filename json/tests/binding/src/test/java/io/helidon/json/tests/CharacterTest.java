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

import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class CharacterTest {

    private final JsonBinding jsonBinding;

    CharacterTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testAsciiChar() {
        String expected = "\"a\"";
        char c = 'a';
        String jsonValue = jsonBinding.serialize(c);
        assertThat(jsonValue, is(expected));

        char deserialized = jsonBinding.deserialize(jsonValue, char.class);
        assertThat(deserialized, is(c));
    }

    @Test
    public void testUTF8Char() {
        String expected = "\"ř\"";
        char c = 'ř';
        String jsonValue = jsonBinding.serialize(c);
        assertThat(jsonValue, is(expected));

        char deserialized = jsonBinding.deserialize(jsonValue, char.class);
        assertThat(deserialized, is(c));
    }

    @Test
    public void testUnicodeChar() {
        String jsonValue = "\"\\u0041\"";

        char deserialized = jsonBinding.deserialize(jsonValue, char.class);
        assertThat(deserialized, is('A'));
    }
}
