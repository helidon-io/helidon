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

    public static int translateHex(byte b, JsonParser parser) {
        int val = HEX_DIGITS[b & 0xFF];
        if (val == -1) {
            throw parser.createException("Invalid hex digit found", b);
        }
        return val;
    }

    public static String toPrintableForm(byte c) {
        return toPrintableForm((char) c);
    }

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
