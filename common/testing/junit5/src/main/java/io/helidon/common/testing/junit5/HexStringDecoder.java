/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.common.testing.junit5;

/**
 * Utility class to decode hex strings.
 */
public final class HexStringDecoder {

    private HexStringDecoder() {
    }

    /**
     * Utility method to decode hex strings. For example, "\0x0D\0x0A\0x0D\0x0A" is decoded
     * as a 4-byte array with hex values 0D 0A 0D 0A.
     *
     * @param s string to decode
     * @return decoded string as byte array
     */
    public static byte[] decodeHexString(String s) {
        if (s.isEmpty() || s.length() % 4 != 0) {
            throw new IllegalArgumentException("Invalid hex string");
        }
        byte[] bytes = new byte[s.length() / 4];
        for (int i = 0, j = 0; i < s.length(); i += 4) {
            char c1 = s.charAt(i + 2);
            byte b1 = (byte) (Character.isDigit(c1) ? c1 - '0' : c1 - 'A' + 10);
            char c2 = s.charAt(i + 3);
            byte b2 = (byte) (Character.isDigit(c2) ? c2 - '0' : c2 - 'A' + 10);
            bytes[j++] = (byte) (((b1 << 4) & 0xF0) | (b2 & 0x0F));
        }
        return bytes;
    }
}
