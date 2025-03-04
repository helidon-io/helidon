/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.uri.UriValidationException.Segment;

/**
 * Validate parts of the URI.
 * <p>
 * Validation is based on
 * <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.2.2">RFC-3986</a>.
 * <p>
 * The following list provides an overview of parts of URI and how/if we validate it:
 * <ul>
 *     <li>scheme - {@link #validateScheme(String)}</li>
 *     <li>authority - {@link #validateHost(String)}, port is validated in HTTP processing</li>
 *     <li>path - see {@link io.helidon.common.uri.UriPath#validate()}</li>
 *     <li>query - {@link #validateQuery(String)}</li>
 *     <li>fragment - {@link #validateFragment(String)}</li>
 * </ul>
 */
public final class UriValidator {
    private static final Pattern IP_V4_PATTERN =
            Pattern.compile("^([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})$");
    private static final boolean[] HEXDIGIT = new boolean[256];
    private static final boolean[] UNRESERVED = new boolean[256];
    private static final boolean[] SUB_DELIMS = new boolean[256];
    // characters (in addition to hex, unreserved and sub-delims) that can be safely printed
    private static final boolean[] PRINTABLE = new boolean[256];

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

        PRINTABLE[':'] = true;
        PRINTABLE['/'] = true;
        PRINTABLE['?'] = true;
        PRINTABLE['@'] = true;
        PRINTABLE['%'] = true;
        PRINTABLE['#'] = true;
        PRINTABLE['['] = true;
        PRINTABLE[']'] = true;
    }

    private UriValidator() {
    }

    /**
     * Validate a URI scheme.
     *
     * @param scheme scheme to validate
     * @throws io.helidon.common.uri.UriValidationException in case there are invalid characters in the scheme
     */
    public static void validateScheme(String scheme) {
        if ("http".equals(scheme)) {
            return;
        }
        if ("https".equals(scheme)) {
            return;
        }
        // ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
        char[] chars = scheme.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            validateAscii(Segment.SCHEME, chars, i, c);
            if (Character.isLetterOrDigit(c)) {
                continue;
            }
            if (c == '+') {
                continue;
            }
            if (c == '-') {
                continue;
            }
            if (c == '.') {
                continue;
            }
            failInvalidChar(Segment.SCHEME, chars, i, c);
        }
    }

    /**
     * Validate a URI Query raw string.
     *
     * @param rawQuery query to validate
     * @throws io.helidon.common.uri.UriValidationException in case there are invalid characters in the query
     */
    public static void validateQuery(String rawQuery) {
        Objects.requireNonNull(rawQuery);

        // empty query is valid
        if (rawQuery.isEmpty()) {
            return;
        }

        // query = *( pchar / "/" / "?" )
        // pchar = unreserved / pct-encoded / sub-delims / ":" / "@"
        // unreserved = ALPHA / DIGIT / "-" / "." / "_" / "~"
        // pct-encoded = "%" HEXDIG HEXDIG
        // sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="

        char[] chars = rawQuery.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            validateAscii(Segment.QUERY, chars, i, c);
            if (UNRESERVED[c]) {
                continue;
            }
            if (SUB_DELIMS[c]) {
                continue;
            }
            if (c == '@') {
                continue;
            }
            if (c == ':') {
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
                validatePercentEncoding(Segment.QUERY, rawQuery, chars, i);
                i += 2;
                continue;
            }
            failInvalidChar(Segment.QUERY, chars, i, c);
        }
    }

    /**
     * Validate a host string.
     *
     * @param host host to validate
     * @throws io.helidon.common.uri.UriValidationException in case there are invalid characters in the host
     */
    public static void validateHost(String host) {
        Objects.requireNonNull(host);
        if (host.indexOf('[') == 0 && host.indexOf(']') == host.length() - 1) {
            validateIpLiteral(host);
        } else {
            validateNonIpLiteral(host);
        }
    }

    /**
     * An IP literal starts with {@code [} and ends with {@code ]}.
     *
     * @param ipLiteral host literal string, may be an IPv6 address, or IP version future
     * @throws io.helidon.common.uri.UriValidationException in case there are invalid characters in the host
     */
    public static void validateIpLiteral(String ipLiteral) {
        Objects.requireNonNull(ipLiteral);
        checkNotBlank(Segment.HOST, "IP Literal", ipLiteral, ipLiteral);

        // IP-literal = "[" ( IPv6address / IPvFuture  ) "]"
        if (ipLiteral.charAt(0) != '[') {
            throw new UriValidationException(Segment.HOST,
                                             ipLiteral.toCharArray(),
                                             "Invalid IP literal, missing square bracket(s)",
                                             0,
                                             ipLiteral.charAt(0));
        }
        int lastIndex = ipLiteral.length() - 1;
        if (ipLiteral.charAt(lastIndex) != ']') {
            throw new UriValidationException(Segment.HOST,
                                             ipLiteral.toCharArray(),
                                             "Invalid IP literal, missing square bracket(s)",
                                             lastIndex,
                                             ipLiteral.charAt(lastIndex));
        }

        String host = ipLiteral.substring(1, ipLiteral.length() - 1);
        checkNotBlank(Segment.HOST, "Host", ipLiteral, host);
        if (host.charAt(0) == 'v') {
            // IP future - starts with version `v1` etc.
            validateIpFuture(ipLiteral, host);
            return;
        }
        // IPv6
        /*
        IPv6address   = 6( h16 ":" ) ls32
                 /                       "::" 5( h16 ":" ) ls32
                 / [               h16 ] "::" 4( h16 ":" ) ls32
                 / [ *1( h16 ":" ) h16 ] "::" 3( h16 ":" ) ls32
                 / [ *2( h16 ":" ) h16 ] "::" 2( h16 ":" ) ls32
                 / [ *3( h16 ":" ) h16 ] "::"    h16 ":"   ls32
                 / [ *4( h16 ":" ) h16 ] "::"              ls32
                 / [ *5( h16 ":" ) h16 ] "::"              h16
                 / [ *6( h16 ":" ) h16 ] "::"

              ls32          = ( h16 ":" h16 ) / IPv4address
              h16           = 1*4HEXDIG
         */
        if (host.equals("::")) {
            // all empty
            return;
        }
        if (host.equals("::1")) {
            // localhost
            return;
        }
        boolean skipped = false;
        int segments = 0; // max segments is 8 (full IPv6 address)
        String inProgress = host;
        while (!inProgress.isEmpty()) {
            if (inProgress.length() == 1) {
                segments++;
                validateH16(ipLiteral, inProgress);
                break;
            }
            if (inProgress.charAt(0) == ':' && inProgress.charAt(1) == ':') {
                // :: means skip everything that was before (or everything that is after)
                if (skipped) {
                    throw new UriValidationException(Segment.HOST,
                                                     ipLiteral.toCharArray(),
                                                     "Host IPv6 contains more than one skipped segment");
                }
                skipped = true;
                segments++;
                inProgress = inProgress.substring(2);
                continue;
            }
            if (inProgress.charAt(0) == ':') {
                throw new UriValidationException(Segment.HOST,
                                                 ipLiteral.toCharArray(),
                                                 inProgress.toCharArray(),
                                                 "Host IPv6 contains excessive colon");
            }
            // this must be h16 (or an IPv4 address)
            int nextColon = inProgress.indexOf(':');
            if (nextColon == -1) {
                // the rest of the string
                if (inProgress.indexOf('.') == -1) {
                    segments++;
                    validateH16(ipLiteral, inProgress);
                } else {
                    Matcher matcher = IP_V4_PATTERN.matcher(inProgress);
                    if (matcher.matches()) {
                        validateIpOctet("Host IPv6 dual address contains invalid IPv4 address", ipLiteral, matcher.group(1));
                        validateIpOctet("Host IPv6 dual address contains invalid IPv4 address", ipLiteral, matcher.group(2));
                        validateIpOctet("Host IPv6 dual address contains invalid IPv4 address", ipLiteral, matcher.group(3));
                        validateIpOctet("Host IPv6 dual address contains invalid IPv4 address", ipLiteral, matcher.group(4));
                    } else {
                        throw new UriValidationException(Segment.HOST,
                                                         ipLiteral.toCharArray(),
                                                         "Host IPv6 dual address contains invalid IPv4 address");
                    }
                }
                break;
            }
            validateH16(ipLiteral, inProgress.substring(0, nextColon));
            segments++;
            if (inProgress.length() >= nextColon + 2) {
                if (inProgress.charAt(nextColon + 1) == ':') {
                    // double colon, keep it there
                    inProgress = inProgress.substring(nextColon);
                    continue;
                }
            }
            inProgress = inProgress.substring(nextColon + 1);
            if (inProgress.isBlank()) {
                // this must fail on empty segment
                validateH16(ipLiteral, inProgress);
            }
        }

        if (segments > 8) {
            throw new UriValidationException(Segment.HOST,
                                             ipLiteral.toCharArray(),
                                             "Host IPv6 address contains too many segments");
        }
    }

    /**
     * Validate IPv4 address or a registered name.
     *
     * @param host string with either an IPv4 address, or a registered name
     * @throws io.helidon.common.uri.UriValidationException in case there are invalid characters in the host
     */
    public static void validateNonIpLiteral(String host) {
        Objects.requireNonNull(host);
        checkNotBlank(Segment.HOST, "Host", host, host);

        // Ipv4 address: 127.0.0.1
        Matcher matcher = IP_V4_PATTERN.matcher(host);
        if (matcher.matches()) {
            /*
              IPv4address = dec-octet "." dec-octet "." dec-octet "." dec-octet
              dec-octet   = DIGIT                 ; 0-9
                  / %x31-39 DIGIT         ; 10-99
                  / "1" 2DIGIT            ; 100-199
                  / "2" %x30-34 DIGIT     ; 200-249
                  / "25" %x30-35          ; 250-255
            */

            // we have found an IPv4 address, or a valid registered name (555.555.555.555 is a valid name...)
            return;
        }

        // everything else is a registered name

        // registered name
        /*
        reg-name    = *( unreserved / pct-encoded / sub-delims )
        pct-encoded = "%" HEXDIG HEXDIG
        unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
        sub-delims  = "!" / "$" / "&" / "'" / "(" / ")"
                  / "*" / "+" / "," / ";" / "="
        */
        char[] chars = host.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            validateAscii(Segment.HOST, chars, i, c);

            if (UNRESERVED[c]) {
                continue;
            }
            if (SUB_DELIMS[c]) {
                continue;
            }
            if (c == '%') {
                // percent encoding
                validatePercentEncoding(Segment.HOST, host, chars, i);
                i += 2;
                continue;
            }
            failInvalidChar(Segment.HOST, chars, i, c);
        }
    }

    /**
     * Validate URI fragment.
     *
     * @param rawFragment fragment to validate
     * @throws io.helidon.common.uri.UriValidationException in case there are invalid characters in the fragment
     */
    public static void validateFragment(String rawFragment) {
        Objects.requireNonNull(rawFragment);

        if (rawFragment.isEmpty()) {
            return;
        }
        char[] chars = rawFragment.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            validateAscii(Segment.FRAGMENT, chars, i, c);

            // *( pchar / "/" / "?" )
            // pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"

            if (UNRESERVED[c]) {
                continue;
            }
            if (SUB_DELIMS[c]) {
                continue;
            }
            if (c == '@') {
                continue;
            }
            if (c == ':') {
                continue;
            }

            // done with pchar validation except for percent encoded
            if (c == '%') {
                // percent encoding
                validatePercentEncoding(Segment.FRAGMENT, rawFragment, chars, i);
                i += 2;
                continue;
            }
            failInvalidChar(Segment.FRAGMENT, chars, i, c);
        }
    }

    static String print(char c) {
        if (printable(c)) {
            return "'" + c + "'";
        }
        return "0x" + hex(c);
    }

    static String encode(char[] chars) {
        StringBuilder result = new StringBuilder(chars.length);

        for (char aChar : chars) {
            if (aChar > 254) {
                result.append('?');
                continue;
            }
            if (printable(aChar)) {
                result.append(aChar);
                continue;
            }
            result.append('?');
        }

        return result.toString();
    }

    private static void failInvalidChar(Segment segment, char[] chars, int i, char c) {
        throw new UriValidationException(segment,
                                         chars,
                                         segment.text() + " contains invalid char",
                                         i,
                                         c);
    }

    private static void validateAscii(Segment segment, char[] chars, int i, char c) {
        if (c > 254) {
            // in general only ASCII characters are allowed
            throw new UriValidationException(segment,
                                             chars,
                                             segment.text() + " contains invalid char (non-ASCII)",
                                             i,
                                             c);
        }
    }

    /**
     * Validate percent encoding sequence.
     *
     * @param segment segment of the URI
     * @param chars   characters of the part
     * @param i       index of the percent
     */
    private static void validatePercentEncoding(Segment segment, String value, char[] chars, int i) {
        if (i + 2 >= chars.length) {
            throw new UriValidationException(segment,
                                             chars,
                                             segment.text()
                                                     + " contains invalid % encoding, not enough chars left at index "
                                                     + i);
        }
        char p1 = chars[i + 1];
        char p2 = chars[i + 2];
        // %p1p2
        validateHex(segment, value, chars, p1, segment.text(), i + 1, true);
        validateHex(segment, value, chars, p2, segment.text(), i + 2, true);
    }

    private static void validateHex(Segment segment,
                                    String fullValue,
                                    char[] chars,
                                    char c,
                                    String type,
                                    int index,
                                    boolean isPercentEncoding) {
        if (c > 255 || !HEXDIGIT[c]) {
            if (fullValue.length() == chars.length) {
                if (isPercentEncoding) {
                    throw new UriValidationException(segment,
                                                     chars,
                                                     type + " has non hexadecimal char in % encoding",
                                                     index,
                                                     c);
                }
                throw new UriValidationException(segment,
                                                 chars,
                                                 type + " has non hexadecimal char",
                                                 index,
                                                 c);
            } else {
                if (isPercentEncoding) {
                    throw new UriValidationException(segment,
                                                     fullValue.toCharArray(),
                                                     chars,
                                                     type + " has non hexadecimal char in % encoding",
                                                     index,
                                                     c);
                }
                throw new UriValidationException(segment,
                                                 fullValue.toCharArray(),
                                                 chars,
                                                 type + " has non hexadecimal char",
                                                 index,
                                                 c);
            }
        }
    }

    private static String hex(char c) {
        String hexString = Integer.toHexString(c);
        if (hexString.length() == 1) {
            return "0" + hexString;
        }
        return hexString;
    }

    private static void validateH16(String host, String inProgress) {
        if (inProgress.isBlank()) {
            throw new UriValidationException(Segment.HOST,
                                             host.toCharArray(),
                                             "IPv6 segment is empty");
        }
        if (inProgress.length() > 4) {
            throw new UriValidationException(Segment.HOST,
                                             host.toCharArray(),
                                             inProgress.toCharArray(),
                                             "IPv6 segment has more than 4 chars");
        }
        validateHexDigits(Segment.HOST, "IPv6 segment", host, inProgress);
    }

    private static void validateHexDigits(Segment segment,
                                          String description,
                                          String host,
                                          String section) {
        char[] chars = section.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            validateHex(segment, host, chars, c, description, i, false);
        }
    }

    private static void validateIpOctet(String message, String host, String octet) {
        int octetInt = Integer.parseInt(octet);
        // cannot be negative, as the regexp will not match
        if (octetInt > 255) {
            throw new UriValidationException(Segment.HOST, host.toCharArray(), message);
        }
    }

    private static void validateIpFuture(String ipLiteral, String host) {
        /*
              IPvFuture     = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" )
              unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
              sub-delims    = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
        */
        int dot = host.indexOf('.');
        if (dot == -1) {
            throw new UriValidationException(Segment.HOST,
                                             ipLiteral.toCharArray(),
                                             "IP Future must contain 'v<version>.'");
        }
        // always starts with v
        String version = host.substring(1, dot);
        checkNotBlank(Segment.HOST, "Version", ipLiteral, version);
        validateHexDigits(Segment.HOST, "Future version", ipLiteral, version);

        String address = host.substring(dot + 1);
        checkNotBlank(Segment.HOST, "IP Future", ipLiteral, address);

        char[] chars = address.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            validateAscii(Segment.HOST, chars, i, c);
            if (UNRESERVED[c]) {
                continue;
            }
            if (SUB_DELIMS[c]) {
                continue;
            }
            if (c == ':') {
                continue;
            }
            failInvalidChar(Segment.HOST, ipLiteral.toCharArray(), i + dot + 1, c);
        }
    }

    private static void checkNotBlank(Segment segment,
                                      String message,
                                      String ipLiteral,
                                      String toValidate) {
        if (toValidate.isBlank()) {
            if (ipLiteral.equals(toValidate)) {
                throw new UriValidationException(segment, ipLiteral.toCharArray(), message + " cannot be blank");
            } else {
                throw new UriValidationException(segment,
                                                 ipLiteral.toCharArray(),
                                                 toValidate.toCharArray(),
                                                 message + " cannot be blank");
            }
        }
    }

    private static boolean printable(char c) {
        if (c > 254) {
            return false;
        }
        if (UNRESERVED[c]) {
            return true;
        }
        if (SUB_DELIMS[c]) {
            return true;
        }
        if (PRINTABLE[c]) {
            return true;
        }
        return false;
    }
}
