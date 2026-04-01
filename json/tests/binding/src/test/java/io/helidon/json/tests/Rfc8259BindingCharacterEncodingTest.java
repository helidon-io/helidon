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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.json.JsonException;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class Rfc8259BindingCharacterEncodingTest {

    private static final int STREAM_BUFFER_SIZE = 15;

    private final JsonBinding jsonBinding;

    Rfc8259BindingCharacterEncodingTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @Test
    public void testAcceptsUtf8EncodedInputStreamForUnicodeStringProperties() {
        String value = "€😀žluťoučký";
        byte[] json = ("{\"value\":\"" + value + "\"}").getBytes(StandardCharsets.UTF_8);

        UnicodeTextBean bean = jsonBinding.deserialize(new ByteArrayInputStream(json), UnicodeTextBean.class);
        assertThat(bean.value(), is(value));
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @Test
    public void testAcceptsUtf8SequenceSplitAcrossConfiguredInputBufferBoundary() {
        byte[] json = "{\"value\":\"1234😀\"}".getBytes(StandardCharsets.UTF_8);

        UnicodeTextBean bean = jsonBinding.deserialize(new ByteArrayInputStream(json),
                                                       STREAM_BUFFER_SIZE,
                                                       UnicodeTextBean.class);
        assertThat(bean.value(), is("1234😀"));
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @Test
    public void testRejectsMalformedUtf8ByteSequencesInInputStream() {
        assertAll(
                () -> assertThrows(JsonException.class,
                                   () -> jsonBinding.deserialize(new ByteArrayInputStream(objectJsonBytes((byte) 0x80)),
                                                                 UnicodeTextBean.class)),
                () -> assertThrows(JsonException.class,
                                   () -> jsonBinding.deserialize(new ByteArrayInputStream(objectJsonBytes((byte) 0xC2,
                                                                                                         (byte) 0x20)),
                                                                 UnicodeTextBean.class)),
                () -> assertThrows(JsonException.class,
                                   () -> jsonBinding.deserialize(new ByteArrayInputStream(objectJsonBytes((byte) 0xC0,
                                                                                                         (byte) 0xAF)),
                                                                 UnicodeTextBean.class)),
                () -> assertThrows(JsonException.class,
                                   () -> jsonBinding.deserialize(new ByteArrayInputStream(objectJsonBytes((byte) 0xF0,
                                                                                                         (byte) 0x9F,
                                                                                                         (byte) 0x98)),
                                                                 UnicodeTextBean.class))
        );
    }

    /**
     * RFC 8259 §8.1
     * Quote: "implementations that parse JSON texts MAY ignore the presence of a byte order mark rather than treating it as an error."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @Test
    public void testRejectsLeadingUtf8BomInByteArrayAndInputStream() {
        byte[] json = prependUtf8Bom(objectJsonBytes((byte) 'o', (byte) 'k'));

        assertRejectsByteArrayAndInputStream(json);
    }

    /**
     * RFC 8259 §8.1
     * Quote: "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.1
     */
    @Test
    public void testRejectsUtf16AndUtf32EncodedByteArrayAndInputStream() {
        String jsonText = "{\"value\":\"ok\"}";

        assertAll(
                () -> assertRejectsByteArrayAndInputStream(jsonText.getBytes(StandardCharsets.UTF_16BE)),
                () -> assertRejectsByteArrayAndInputStream(jsonText.getBytes(StandardCharsets.UTF_16LE)),
                () -> assertRejectsByteArrayAndInputStream(utf32BeAsciiBytes(jsonText)),
                () -> assertRejectsByteArrayAndInputStream(utf32LeAsciiBytes(jsonText))
        );
    }

    /**
     * RFC 8259 §7 and §8.1
     * Object member names are strings, and JSON text exchanged between systems MUST be encoded using UTF-8.
     */
    @Test
    public void testRejectsMalformedUtf8InObjectMemberNameBytes() {
        byte[] json = objectJsonWithMalformedMemberNameBytes((byte) 0xC0, (byte) 0xAF);

        assertAll(
                () -> assertThrows(JsonException.class,
                                   () -> jsonBinding.deserialize(json, UnicodeTextBean.class)),
                () -> assertThrows(JsonException.class,
                                   () -> jsonBinding.deserialize(new ByteArrayInputStream(json),
                                                                 STREAM_BUFFER_SIZE,
                                                                 UnicodeTextBean.class))
        );
    }

    private void assertRejectsByteArrayAndInputStream(byte[] json) {
        assertAll(
                () -> assertThrows(JsonException.class,
                                   () -> jsonBinding.deserialize(json, UnicodeTextBean.class)),
                () -> assertThrows(JsonException.class,
                                   () -> jsonBinding.deserialize(new ByteArrayInputStream(json),
                                                                 STREAM_BUFFER_SIZE,
                                                                 UnicodeTextBean.class))
        );
    }

    private static byte[] objectJsonBytes(byte... valueBytes) {
        byte[] prefix = "{\"value\":\"".getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = "\"}".getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[prefix.length + valueBytes.length + suffix.length];

        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(valueBytes, 0, result, prefix.length, valueBytes.length);
        System.arraycopy(suffix, 0, result, prefix.length + valueBytes.length, suffix.length);

        return result;
    }

    private static byte[] prependUtf8Bom(byte[] json) {
        byte[] result = new byte[json.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(json, 0, result, 3, json.length);
        return result;
    }

    private static byte[] utf32BeAsciiBytes(String json) {
        byte[] ascii = json.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[ascii.length * 4];

        for (int i = 0; i < ascii.length; i++) {
            result[i * 4 + 3] = ascii[i];
        }

        return result;
    }

    private static byte[] utf32LeAsciiBytes(String json) {
        byte[] ascii = json.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[ascii.length * 4];

        for (int i = 0; i < ascii.length; i++) {
            result[i * 4] = ascii[i];
        }

        return result;
    }

    private static byte[] objectJsonWithMalformedMemberNameBytes(byte... memberNameBytes) {
        byte[] prefix = "{\"".getBytes(StandardCharsets.US_ASCII);
        byte[] middle = "\":\"x\",\"value\":\"ok\"}".getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[prefix.length + memberNameBytes.length + middle.length];

        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(memberNameBytes, 0, result, prefix.length, memberNameBytes.length);
        System.arraycopy(middle, 0, result, prefix.length + memberNameBytes.length, middle.length);

        return result;
    }

    @Json.Entity
    record UnicodeTextBean(String value) {

    }
}
