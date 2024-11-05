package io.helidon.common.uri;

import java.util.Objects;

/**
 * Validate parts of the URI.
 * <p>
 * Validation is based on
 * <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.2.2">RFC-3986</a>.
 */
public class UriValidator {
    private static final boolean[] HEXDIGIT = new boolean[256];
    private static final boolean[] UNRESERVED = new boolean[256];
    private static final boolean[] SUB_DELIMS = new boolean[256];

    static {
        // digits
        for (int i = '0'; i <= '9'; i++) {
            UNRESERVED[i] = true;
        }
        // alpha
        for (int i = 'a'; i <= 'z'; i++) {
            UNRESERVED[i] = true;
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            UNRESERVED[i] = true;
        }
        UNRESERVED['-'] = true;
        UNRESERVED['.'] = true;
        UNRESERVED['_'] = true;
        UNRESERVED['~'] = true;

        // hexdigits
        // digits
        for (int i = '0'; i <= '9'; i++) {
            HEXDIGIT[i] = true;
        }
        // alpha
        for (int i = 'a'; i <= 'f'; i++) {
            HEXDIGIT[i] = true;
        }
        for (int i = 'A'; i <= 'F'; i++) {
            HEXDIGIT[i] = true;
        }

        // sub-delim set
        SUB_DELIMS['!'] = true;
        SUB_DELIMS['$'] = true;
        SUB_DELIMS['&'] = true;
        SUB_DELIMS['\''] = true;
        SUB_DELIMS['('] = true;
        SUB_DELIMS[')'] = true;
        SUB_DELIMS['*'] = true;
        SUB_DELIMS['+'] = true;
        SUB_DELIMS[','] = true;
        SUB_DELIMS[';'] = true;
        SUB_DELIMS['='] = true;
    }

    private UriValidator() {
    }

    /**
     * Validate a URI Query raw string.
     *
     * @param rawQuery query to validate
     * @throws java.lang.IllegalArgumentException in case the query is not valid, the message will not contain the query, and
     *                                            will never contain dangerous characters
     */
    public static void validateQuery(String rawQuery) {
        Objects.requireNonNull(rawQuery);

        // empty query is valid
        if (rawQuery.isEmpty()) {
            return;
        }

        // query = *( pchar / "/" / "?" )
        // pchar = unreserved / pct-encoded / sub-delims / "@"
        // unreserved = ALPHA / DIGIT / "-" / "." / "_" / "~"
        // pct-encoded = "%" HEXDIG HEXDIG
        // sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="

        char[] chars = rawQuery.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c > 255) {
                // in general only ASCII characters are allowed
                throw new IllegalArgumentException("Query contains invalid character (non-ASCII). Index: " + i
                                                           + ", character: " + hex(c));
            }
            if (UNRESERVED[c]) {
                continue;
            }
            if (SUB_DELIMS[c]) {
                continue;
            }
            if (c == '@') {
                continue;
            }
            if (c == '/') {
                continue;
            }
            if (c == '?') {
                continue;
            }
            // done with pchar validation except for percent encoded
            if (c == '%') {
                // percent encoding
                validatePercentEncoding("Query", chars, i);
                i += 2;
                continue;
            }
            throw new IllegalArgumentException("Query contains invalid character. Index: " + i
                                                       + ", character: " + hex(c));
        }
    }

    /**
     * Validate percent encoding sequence.
     *
     * @param type  type of validate part of URI
     * @param chars characters of the part
     * @param i     index of the percent
     */
    private static void validatePercentEncoding(String type, char[] chars, int i) {
        if (i + 2 >= chars.length) {
            throw new IllegalArgumentException(type + " contains invalid % encoding, not enough characters left. Index: " + i);
        }
        char p1 = chars[i + 1];
        char p2 = chars[i + 2];
        // %p1p2
        if (p1 > 255 || !HEXDIGIT[p1]) {
            throw new IllegalArgumentException(type + " contains invalid character in % encoding. Index: " + (i + 1)
                                                       + ", character: " + hex(p1));
        }
        if (p2 > 255 || !HEXDIGIT[p2]) {
            throw new IllegalArgumentException(type + " contains invalid character in % encoding. Index: " + (i + 2)
                                                       + ", character: " + hex(p2));
        }
    }

    private static String hex(char c) {
        String hexString = Integer.toHexString(c);
        if (hexString.length() == 1) {
            return "0x0" + hexString;
        }
        return "0x" + hexString;
    }
}
