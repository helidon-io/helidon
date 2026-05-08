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
 *
 * <p>Spec-trace comments quote exact Smile spec section titles and then paraphrase the exercised rule.</p>
 */
@Testing.Test
public class SmileStringTest {

    private final JsonBinding jsonBinding;

    SmileStringTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    /*
     * Spec: "Token class: Simple literals, numbers".
     * Rule: the empty String has its own literal token instead of using the ASCII/Unicode length-prefixed token
     * families.
     */
    @Test
    public void testEmptyString() {
        StringModel model = new StringModel("");
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(""));
    }

    /*
     * Spec: "Token classes: Tiny ASCII, Short ASCII".
     * Rule: ASCII strings use `0x40..0x5F` for 1-32 bytes and `0x60..0x7F` for 33-64 bytes.
     */
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

    /*
     * Spec: "Token classes: Tiny Unicode, Short Unicode".
     * Rule: non-ASCII UTF-8 payloads use byte-length-based Unicode token families, not character-count-based ASCII
     * families.
     */
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

    @Test
    public void testShortUnicodeEmoji() {
        StringModel model = new StringModel("Hello 😀 World 🌍 Test 🚀");
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is("Hello 😀 World 🌍 Test 🚀"));
    }

    /*
     * Spec: "Token class: Misc; binary / text / structure markers".
     * Rule: long text moves to the variable-length long-string tokens and is terminated by the `0xFC`
     * end-of-String marker.
     */
    @Test
    public void testLongAsciiBasic() {
        String value = "This is a long ASCII string that should exceed the 64 byte limit for short strings.";
        StringModel model = new StringModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(value));
    }

    @Test
    public void testLongUnicodeBasic() {
        String value = "This is a long Unicode string with emojis 😀🌍🚀 and accented chars "
                + "café naïve résumé that exceeds 64 bytes.";
        StringModel model = new StringModel(value);
        byte[] smileData = SmileBindingSupport.serializeSmile(jsonBinding, model);
        StringModel result = SmileBindingSupport.deserializeSmile(jsonBinding, smileData, StringModel.class);
        assertThat(result.value(), is(value));
    }

    /*
     * Spec: "Low-level Format".
     * Rule: Smile String payloads are raw UTF-8 bytes, so control characters survive round-trip as data rather than
     * JSON text escapes.
     */
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
