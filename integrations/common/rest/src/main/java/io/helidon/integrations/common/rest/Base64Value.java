/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.common.rest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base64 wrapper.
 * APIs use base64 encoding to transfer binary data as strings. This class provides helpful methods
 * to handle such transitions.
 *
 * @see #create(byte[])
 * @see #createFromEncoded(String)
 * @see #toBase64()
 * @see #toBytes()
 */
public class Base64Value {
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final String base64;

    private Base64Value(String base64) {
        this.base64 = base64;
    }

    /**
     * Create a base64 value from plain text. This method uses UTF-8 bytes of the string
     * to create a base64 encoded string.
     *
     * @param plainText plain text to use
     * @return a new value
     */
    public static Base64Value create(String plainText) {
        return create(plainText.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a base64 value from bytes. The bytes are encoded using base64.
     *
     * @param bytes bytes to use
     * @return a new value
     */
    public static Base64Value create(byte[] bytes) {
        return createFromEncoded(ENCODER.encodeToString(bytes));
    }

    /**
     * Create from an already encoded base64 value.
     *
     * @param base64Text base64 to use (no encoding will be done)
     * @return a new value
     */
    public static Base64Value createFromEncoded(String base64Text) {
        return new Base64Value(base64Text);
    }

    /**
     * Base64 encoded string.
     *
     * @return base64 value
     */
    public String toBase64() {
        return base64;
    }

    /**
     * Decoded value as bytes.
     *
     * @return bytes
     */
    public byte[] toBytes() {
        return DECODER.decode(base64);
    }

    /**
     * Returns decoded bytes as a string.
     * <p>
     * WARNING - this method is potentially dangerous, as it uses the underlying bytes to create a new string
     * without any validation whether it is in fact bytes fit for a readable string.
     * Use only when the data is known to be a string.
     *
     * @return string value from the decoded bytes
     */
    public String toDecodedString() {
        return new String(toBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return base64;
    }
}
