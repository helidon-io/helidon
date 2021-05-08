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

package io.helidon.webclient;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for validating, encoding and decoding components
 * of a URI.
 *
 * Taken from Jersey 2.34. Original class is {@code UriComponent}
 */
class UriComponentEncoder {

    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    private static final int[] HEX_TABLE = initHexTable();
    private static final String[] SCHEME = {"0-9", "A-Z", "a-z", "+", "-", "."};
    private static final String[] UNRESERVED = {"0-9", "A-Z", "a-z", "-", ".", "_", "~"};
    private static final String[] SUB_DELIMS = {"!", "$", "&", "'", "(", ")", "*", "+", ",", ";", "="};
    private static final boolean[][] ENCODING_TABLES = initEncodingTables();

    private UriComponentEncoder() {
    }

    /**
     * Encodes the characters of string that are either non-ASCII characters
     * or are ASCII characters that must be percent-encoded using the
     * UTF-8 encoding.
     *
     * @param s the string to be encoded.
     * @param t the URI component type identifying the ASCII characters that
     *          must be percent-encoded.
     * @return the encoded string.
     */
    public static String encode(final String s, final Type t) {
        final boolean[] table = ENCODING_TABLES[t.ordinal()];

        StringBuilder sb = null;
        for (int offset = 0, codePoint; offset < s.length(); offset += Character.charCount(codePoint)) {
            codePoint = s.codePointAt(offset);

            if (codePoint < 0x80 && table[codePoint]) {
                if (sb != null) {
                    sb.append((char) codePoint);
                }
            } else {
                if (codePoint == '%'
                        && offset + 2 < s.length()
                        && isHexCharacter(s.charAt(offset + 1))
                        && isHexCharacter(s.charAt(offset + 2))) {
                    if (sb != null) {
                        sb.append('%').append(s.charAt(offset + 1)).append(s.charAt(offset + 2));
                    }
                    offset += 2;
                    continue;
                }

                if (sb == null) {
                    sb = new StringBuilder();
                    sb.append(s, 0, offset);
                }

                if (codePoint < 0x80) {
                    if (codePoint == ' ' && t == Type.QUERY_PARAM) {
                        sb.append('+');
                    } else {
                        appendPercentEncodedOctet(sb, (char) codePoint);
                    }
                } else {
                    appendUTF8EncodedCharacter(sb, codePoint);
                }
            }
        }

        return (sb == null) ? s : sb.toString();
    }

    private static void appendPercentEncodedOctet(final StringBuilder sb, final int b) {
        sb.append('%');
        sb.append(HEX_DIGITS[b >> 4]);
        sb.append(HEX_DIGITS[b & 0x0F]);
    }

    private static void appendUTF8EncodedCharacter(final StringBuilder sb, final int codePoint) {
        final CharBuffer chars = CharBuffer.wrap(Character.toChars(codePoint));
        final ByteBuffer bytes = StandardCharsets.UTF_8.encode(chars);

        while (bytes.hasRemaining()) {
            appendPercentEncodedOctet(sb, bytes.get() & 0xFF);
        }
    }

    private static int[] initHexTable() {
        final int[] table = new int[0x80];
        Arrays.fill(table, -1);

        for (char c = '0'; c <= '9'; c++) {
            table[c] = c - '0';
        }
        for (char c = 'A'; c <= 'F'; c++) {
            table[c] = c - 'A' + 10;
        }
        for (char c = 'a'; c <= 'f'; c++) {
            table[c] = c - 'a' + 10;
        }
        return table;
    }

    private static boolean[][] initEncodingTables() {
        final boolean[][] tables = new boolean[Type.values().length][];

        final List<String> l = new ArrayList<String>();
        l.addAll(Arrays.asList(SCHEME));
        tables[Type.SCHEME.ordinal()] = initEncodingTable(l);

        l.clear();

        l.addAll(Arrays.asList(UNRESERVED));
        tables[Type.UNRESERVED.ordinal()] = initEncodingTable(l);

        l.addAll(Arrays.asList(SUB_DELIMS));

        tables[Type.HOST.ordinal()] = initEncodingTable(l);

        tables[Type.PORT.ordinal()] = initEncodingTable(Arrays.asList("0-9"));

        l.add(":");

        tables[Type.USER_INFO.ordinal()] = initEncodingTable(l);

        l.add("@");

        tables[Type.AUTHORITY.ordinal()] = initEncodingTable(l);

        tables[Type.PATH_SEGMENT.ordinal()] = initEncodingTable(l);
        tables[Type.PATH_SEGMENT.ordinal()][';'] = false;

        tables[Type.MATRIX_PARAM.ordinal()] = tables[Type.PATH_SEGMENT.ordinal()].clone();
        tables[Type.MATRIX_PARAM.ordinal()]['='] = false;

        l.add("/");

        tables[Type.PATH.ordinal()] = initEncodingTable(l);

        tables[Type.QUERY.ordinal()] = initEncodingTable(l);
        tables[Type.QUERY.ordinal()]['!'] = false;
        tables[Type.QUERY.ordinal()]['*'] = false;
        tables[Type.QUERY.ordinal()]['\''] = false;
        tables[Type.QUERY.ordinal()]['('] = false;
        tables[Type.QUERY.ordinal()][')'] = false;
        tables[Type.QUERY.ordinal()][';'] = false;
        tables[Type.QUERY.ordinal()][':'] = false;
        tables[Type.QUERY.ordinal()]['@'] = false;
        tables[Type.QUERY.ordinal()]['$'] = false;
        tables[Type.QUERY.ordinal()][','] = false;
        tables[Type.QUERY.ordinal()]['/'] = false;
        tables[Type.QUERY.ordinal()]['?'] = false;

        tables[Type.QUERY_PARAM.ordinal()] = Arrays.copyOf(
                tables[Type.QUERY.ordinal()],
                tables[Type.QUERY.ordinal()].length);
        tables[Type.QUERY_PARAM.ordinal()]['='] = false;
        tables[Type.QUERY_PARAM.ordinal()]['+'] = false;
        tables[Type.QUERY_PARAM.ordinal()]['&'] = false;

        tables[Type.QUERY_PARAM_SPACE_ENCODED.ordinal()] = tables[Type.QUERY_PARAM.ordinal()];

        tables[Type.FRAGMENT.ordinal()] = tables[Type.QUERY.ordinal()];

        return tables;
    }

    private static boolean[] initEncodingTable(final List<String> allowed) {
        final boolean[] table = new boolean[0x80];
        for (final String range : allowed) {
            if (range.length() == 1) {
                table[range.charAt(0)] = true;
            } else if (range.length() == 3 && range.charAt(1) == '-') {
                for (int i = range.charAt(0); i <= range.charAt(2); i++) {
                    table[i] = true;
                }
            }
        }

        return table;
    }

    /**
     * Checks whether the character {@code c} is hexadecimal character.
     *
     * @param c Any character
     * @return The is {@code c} is a hexadecimal character (e.g. 0, 5, a, A, f, ...)
     */
    private static boolean isHexCharacter(final char c) {
        return c < 128 && HEX_TABLE[c] != -1;
    }

    /**
     * The URI component type.
     */
    enum Type {

        /**
         * ALPHA / DIGIT / "-" / "." / "_" / "~" characters.
         */
        UNRESERVED,
        /**
         * The URI scheme component type.
         */
        SCHEME,
        /**
         * The URI authority component type.
         */
        AUTHORITY,
        /**
         * The URI user info component type.
         */
        USER_INFO,
        /**
         * The URI host component type.
         */
        HOST,
        /**
         * The URI port component type.
         */
        PORT,
        /**
         * The URI path component type.
         */
        PATH,
        /**
         * The URI path component type that is a path segment.
         */
        PATH_SEGMENT,
        /**
         * The URI path component type that is a matrix parameter.
         */
        MATRIX_PARAM,
        /**
         * The URI query component type, encoded using application/x-www-form-urlencoded rules.
         */
        QUERY,
        /**
         * The URI query component type that is a query parameter, encoded using
         * application/x-www-form-urlencoded rules (space character is encoded
         * as {@code +}).
         */
        QUERY_PARAM,
        /**
         * The URI query component type that is a query parameter, encoded using
         * application/x-www-form-urlencoded (space character is encoded as
         * {@code %20}).
         */
        QUERY_PARAM_SPACE_ENCODED,
        /**
         * The URI fragment component type.
         */
        FRAGMENT,
    }

}

