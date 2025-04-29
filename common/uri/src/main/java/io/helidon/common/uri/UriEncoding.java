/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Support for encoding and decoding of URI in HTTP.
 */
public final class UriEncoding {
    private static final char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };
    private static final int[] HEX_TABLE = initHexTable();
    private static final String[] SCHEME = {"0-9", "A-Z", "a-z", "+", "-", "."};
    private static final String[] UNRESERVED = {"0-9", "A-Z", "a-z", "-", ".", "_", "~"};
    private static final String[] SUB_DELIMS = {"!", "$", "&", "'", "(", ")", "*", "+", ",", ";", "="};
    private static final boolean[][] ENCODING_TABLES = initEncodingTables();

    private UriEncoding() {
    }

    /**
     * Decode a URI segment.
     * <p>
     * Percent characters {@code "%s"} found between brackets {@code "[]"} are not decoded to support IPv6 literal.
     * E.g. {@code http://[fe80::1%lo0]:8080}.
     * <p>
     * See <a href="https://tools.ietf.org/html/rfc6874#section-2">RFC 6874, section 2.</a>
     *
     * @param uriSegment URI segment with percent encoding
     * @return decoded string
     */
    public static String decodeUri(String uriSegment) {
        return decodeUri(uriSegment, true);
    }

    /**
     * Decode a URI query.
     *
     * @param uriQuery URI query with percent encoding
     * @return decoded string
     */
    public static String decodeQuery(String uriQuery) {
        return decodeUri(uriQuery, false);
    }

    /**
     * Encode a URI segment.
     *
     * @param uriSegment URI segment that may contain characters not allowed by the URI
     * @return URI segment with percent encoding
     */
    public static String encodeUri(String uriSegment) {
        if (uriSegment.isEmpty()) {
            return "";
        }
        return encode(uriSegment);
    }

    /**
     * Encodes the characters of string that are either non-ASCII characters
     * or are ASCII characters that are not allowed.
     *
     * @param s the string to be encoded
     * @param t the URI component type identifying the ASCII characters that
     *          must be percent-encoded
     * @return the encoded string
     */
    public static String encode(String s, Type t) {
        Objects.requireNonNull(s, "String to encode must not be null");
        Objects.requireNonNull(t, "Type of encoded component must not be null");
        boolean[] table = ENCODING_TABLES[t.ordinal()];

        StringBuilder sb = new StringBuilder();
        for (int offset = 0, codePoint; offset < s.length(); offset += Character.charCount(codePoint)) {
            codePoint = s.codePointAt(offset);

            if (codePoint < 0x80 && table[codePoint]) {
               sb.append((char) codePoint);
            } else {
                // we need to encode percent to %25, as otherwise we are ignoring the decoded string
                if (codePoint == '%') {
                    sb.append("%25");
                    continue;
                }

                if (codePoint < 0x80) {
                    if (codePoint == ' ' && t == Type.QUERY_PARAM) {
                        sb.append('+');
                    } else {
                        appendEscape(sb, (char) codePoint);
                    }
                } else {
                    appendUTF8EncodedCharacter(sb, codePoint);
                }
            }
        }

        return sb.toString();
    }

    private static String encode(String uriSegment) {
        return encode(uriSegment, Type.PATH);
    }

    private static void appendEscape(StringBuilder appender, int b) {
        appender.append('%');
        appender.append(HEX_DIGITS[b >> 4]);
        appender.append(HEX_DIGITS[b & 0x0F]);
    }

    private static String decodeUri(String uriSegment, boolean ignorePercentInBrackets) {
        if (uriSegment.isEmpty()) {
            return "";
        }
        if (uriSegment.indexOf('%') == -1 && uriSegment.indexOf('+') == -1) {
            return uriSegment;
        }
        return decode(uriSegment, ignorePercentInBrackets);
    }

    // see java.net.URI.decode(String, boolean)
    @SuppressWarnings("checkstyle:IllegalToken") // assert is well placed here
    private static String decode(String string, boolean ignorePercentInBrackets) {
        int len = string.length();

        StringBuilder sb = new StringBuilder(len);
        ByteBuffer bb = ByteBuffer.allocate(len);

        // This is not horribly efficient, but it will do for now
        char c = string.charAt(0);
        boolean betweenBrackets = false;

        int i = 0;
        while (i < len) {
            assert c == string.charAt(i);    // Loop invariant
            if (c == '[') {
                betweenBrackets = true;
            } else if (betweenBrackets && c == ']') {
                betweenBrackets = false;
            }
            if (c != '%' || (betweenBrackets && ignorePercentInBrackets)) {
                sb.append(c == '+' && !betweenBrackets ? ' ' : c);      // handles '+' decoding
                if (++i >= len) {
                    break;
                }
                c = string.charAt(i);
                continue;
            }
            bb.clear();
            while (true) {
                bb.put(decode(string.charAt(++i), string.charAt(++i)));
                if (++i >= len) {
                    break;
                }
                c = string.charAt(i);
                if (c != '%') {
                    break;
                }
            }
            bb.flip();

            CharBuffer cb = StandardCharsets.UTF_8.decode(bb);
            sb.append(cb);
        }

        return sb.toString();
    }

    private static byte decode(char c1, char c2) {
        return (byte) (((decode(c1) & 0xf) << 4) | ((decode(c2) & 0xf)));
    }

    private static int decode(char c) {
        if ((c >= '0') && (c <= '9')) {
            return c - '0';
        }
        if ((c >= 'a') && (c <= 'f')) {
            return c - 'a' + 10;
        }
        if ((c >= 'A') && (c <= 'F')) {
            return c - 'A' + 10;
        }

        return -1;
    }

    private static void appendUTF8EncodedCharacter(StringBuilder sb, int codePoint) {
        CharBuffer chars = CharBuffer.wrap(Character.toChars(codePoint));
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(chars);

        while (bytes.hasRemaining()) {
            appendEscape(sb, bytes.get() & 0xFF);
        }
    }

    private static int[] initHexTable() {
        int[] table = new int[0x80];
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
        boolean[][] tables = new boolean[Type.values().length][];

        List<String> l = new ArrayList<String>();
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

    private static boolean[] initEncodingTable(List<String> allowed) {
        boolean[] table = new boolean[0x80];
        for (String range : allowed) {
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
    private static boolean isHexCharacter(char c) {
        return c < 128 && HEX_TABLE[c] != -1;
    }

    /**
     * The URI component type.
     */
    public enum Type {

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
