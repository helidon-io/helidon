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

package io.helidon.json;

import java.util.Arrays;

/**
 * Utility methods shared by JSON parser implementations.
 */
public final class Parsers {

    //Lookup table for converting hexadecimal characters to their numeric values
    private static final int[] HEX_DIGITS = new int[256];

    static {
        Arrays.fill(HEX_DIGITS, -1);
        for (int i = '0'; i <= '9'; ++i) {
            HEX_DIGITS[i] = (i - '0');
        }
        for (int i = 'a'; i <= 'f'; ++i) {
            HEX_DIGITS[i] = ((i - 'a') + 10);
        }
        for (int i = 'A'; i <= 'F'; ++i) {
            HEX_DIGITS[i] = ((i - 'A') + 10);
        }
    }

    private Parsers() {
    }

    /**
     * Translates a hexadecimal byte into its numeric value.
     *
     * @param b the hexadecimal byte to translate
     * @param parser the parser used to create an exception when the byte is invalid
     * @return the numeric value of the hexadecimal digit
     */
    public static int translateHex(byte b, JsonParser parser) {
        int val = HEX_DIGITS[b & 0xFF];
        if (val == -1) {
            throw parser.createException("Invalid hex digit found", b);
        }
        return val;
    }

    /**
     * Converts a byte value into a printable representation for diagnostics.
     *
     * @param c the byte value to convert
     * @return a printable representation of the byte
     */
    public static String toPrintableForm(byte c) {
        return toPrintableForm((char) c);
    }

    /**
     * Converts a character value into a printable representation for diagnostics.
     *
     * @param c the character value to convert
     * @return a printable representation of the character
     */
    public static String toPrintableForm(char c) {
        if (Character.isDigit(c) || Character.isAlphabetic(c)) {
            return "'" + c + "'";
        }
        return "0x" + hex(c);
    }

    private static String hex(char c) {
        String hexString = Integer.toHexString(c);
        if (hexString.length() == 1) {
            return "0" + hexString;
        }
        return hexString;
    }

}
