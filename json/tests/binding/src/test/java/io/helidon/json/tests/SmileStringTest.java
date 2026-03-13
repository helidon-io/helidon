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

/**
 * Tests for Smile format string values (ASCII, Unicode, shared strings).
 * Tests Smile binary format serialization/deserialization using JsonBinding.
 */
@Testing.Test
public class SmileStringTest {

    private final JsonBinding jsonBinding;

    SmileStringTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    // Empty string (already tested in literals, but included here for completeness)
    @Test
    public void testEmptyString() {
        StringModel model = new StringModel("");
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(""));
    }

    // Tiny ASCII strings (1-32 bytes/chars)
    @Test
    public void testTinyAsciiSingleChar() {
        StringModel model = new StringModel("A");
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is("A"));
    }

    @Test
    public void testTinyAsciiShort() {
        StringModel model = new StringModel("Hello");
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is("Hello"));
    }

    @Test
    public void testTinyAsciiMaxLength() {
        String value = "12345678901234567890123456789012"; // 32 chars
        StringModel model = new StringModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(value));
    }

    // Short ASCII strings (33-64 bytes/chars)
    @Test
    public void testShortAsciiMinLength() {
        String value = "123456789012345678901234567890123"; // 33 chars
        StringModel model = new StringModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(value));
    }

    @Test
    public void testShortAsciiMaxLength() {
        String value = "1234567890123456789012345678901234567890123456789012345678901234"; // 64 chars
        StringModel model = new StringModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(value));
    }

    // Tiny Unicode strings (2-33 bytes, may contain multi-byte chars)
    @Test
    public void testTinyUnicodeWithAccents() {
        StringModel model = new StringModel("café");
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is("café"));
    }

    @Test
    public void testTinyUnicodeMaxLength() {
        String value = "αβγδεζηθικλμνξοπρστυφχψω"; // Greek alphabet, ~33 bytes
        StringModel model = new StringModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(value));
    }

    // Short Unicode strings (34-64 bytes)
    @Test
    public void testShortUnicodeEmoji() {
        StringModel model = new StringModel("Hello 😀 World 🌍 Test 🚀");
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is("Hello 😀 World 🌍 Test 🚀"));
    }

    // Long ASCII strings (65+ bytes, variable length with end marker)
    @Test
    public void testLongAsciiBasic() {
        String value = "This is a long ASCII string that should exceed the 64 byte limit for short strings.";
        StringModel model = new StringModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(value));
    }

    // Long Unicode strings (65+ bytes, variable length with end marker)
    @Test
    public void testLongUnicodeBasic() {
        String value = "This is a long Unicode string with emojis 😀🌍🚀 and accented chars café naïve résumé that exceeds 64 bytes.";
        StringModel model = new StringModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(value));
    }

    // Strings with escape sequences (should be handled as Unicode)
    @Test
    public void testStringWithEscapes() {
        String value = "Line 1\nLine 2\tTabbed\r\nWindows line";
        StringModel model = new StringModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(value));
    }

    @Json.Entity
    record StringModel(String value) {
    }
}