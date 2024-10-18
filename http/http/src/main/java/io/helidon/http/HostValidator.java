/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validate the host string (maybe from the {@code Host} header).
 * <p>
 * Validation is based on
 * <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.2.2">RFC-3986</a>.
 */
public final class HostValidator {
    private static final Pattern IP_V4_PATTERN =
            Pattern.compile("^([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})$");
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

    private HostValidator() {
    }

    /**
     * Validate a host string.
     *
     * @param host host to validate
     * @throws java.lang.IllegalArgumentException in case the host is not valid, the message is HTML encoded
     */
    public static void validate(String host) {
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
     * @throws java.lang.IllegalArgumentException in case the host is not valid, the message is HTML encoded
     */
    public static void validateIpLiteral(String ipLiteral) {
        Objects.requireNonNull(ipLiteral);
        checkNotBlank("IP Literal", ipLiteral, ipLiteral);

        // IP-literal = "[" ( IPv6address / IPvFuture  ) "]"
        if (ipLiteral.charAt(0) != '[' || ipLiteral.charAt(ipLiteral.length() - 1) != ']') {
            throw new IllegalArgumentException("Invalid IP literal, missing square bracket(s): " + HtmlEncoder.encode(ipLiteral));
        }

        String host = ipLiteral.substring(1, ipLiteral.length() - 1);
        checkNotBlank("Host", ipLiteral, host);
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
                    throw new IllegalArgumentException("Host IPv6 contains more than one skipped segment: "
                                                               + HtmlEncoder.encode(ipLiteral));
                }
                skipped = true;
                segments++;
                inProgress = inProgress.substring(2);
                continue;
            }
            if (inProgress.charAt(0) == ':') {
                throw new IllegalArgumentException("Host IPv6 contains excessive colon: " + HtmlEncoder.encode(ipLiteral));
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
                        validateIpOctet("Host IPv6 dual address contains invalid IPv4 address:", ipLiteral, matcher.group(1));
                        validateIpOctet("Host IPv6 dual address contains invalid IPv4 address:", ipLiteral, matcher.group(2));
                        validateIpOctet("Host IPv6 dual address contains invalid IPv4 address:", ipLiteral, matcher.group(3));
                        validateIpOctet("Host IPv6 dual address contains invalid IPv4 address:", ipLiteral, matcher.group(4));
                    } else {
                        throw new IllegalArgumentException("Host IPv6 dual address contains invalid IPv4 address: "
                                                                   + HtmlEncoder.encode(ipLiteral));
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
            throw new IllegalArgumentException("Host IPv6 address contains too many segments: " + HtmlEncoder.encode(ipLiteral));
        }
    }

    /**
     * Validate IPv4 address or a registered name.
     *
     * @param host string with either an IPv4 address, or a registered name
     * @throws java.lang.IllegalArgumentException in case the host is not valid, the message is HTML encoded
     */
    public static void validateNonIpLiteral(String host) {
        Objects.requireNonNull(host);
        checkNotBlank("Host", host, host);

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
        char[] charArray = host.toCharArray();
        for (int i = 0; i < charArray.length; i++) {
            char c = charArray[i];
            if (c > 255) {
                throw new IllegalArgumentException("Host contains invalid character: " + HtmlEncoder.encode(host));
            }
            if (UNRESERVED[c]) {
                continue;
            }
            if (SUB_DELIMS[c]) {
                continue;
            }
            if (c == '%') {
                // percent encoding
                if (i + 2 >= charArray.length) {
                    throw new IllegalArgumentException("Host contains invalid % encoding: " + HtmlEncoder.encode(host));
                }
                char p1 = charArray[++i];
                char p2 = charArray[++i];
                // %p1p2
                if (p1 > 255 || p2 > 255) {
                    throw new IllegalArgumentException("Host contains invalid character in % encoding: "
                                                               + HtmlEncoder.encode(host));
                }
                if (HEXDIGIT[p1] && HEXDIGIT[p2]) {
                    continue;
                }
                throw new IllegalArgumentException("Host contains non-hexadecimal character in % encoding: "
                                                           + HtmlEncoder.encode(host));
            }
            throw new IllegalArgumentException("Host contains invalid character: " + HtmlEncoder.encode(host));
        }
    }

    private static void validateH16(String host, String inProgress) {
        if (inProgress.isBlank()) {
            throw new IllegalArgumentException("IPv6 segment is empty: " + HtmlEncoder.encode(host));
        }
        if (inProgress.length() > 4) {
            throw new IllegalArgumentException("IPv6 segment has more than 4 characters: " + HtmlEncoder.encode(host));
        }
        validateHexDigits("IPv6 segment", host, inProgress);
    }

    private static void validateHexDigits(String description, String host, String segment) {
        for (char c : segment.toCharArray()) {
            if (c > 255) {
                throw new IllegalArgumentException(description + " non hexadecimal character: " + HtmlEncoder.encode(host));
            }
            if (!HEXDIGIT[c]) {
                throw new IllegalArgumentException(description + " non hexadecimal character: " + HtmlEncoder.encode(host));
            }
        }
    }

    private static void validateIpOctet(String message, String host, String octet) {
        int octetInt = Integer.parseInt(octet);
        // cannot be negative, as the regexp will not match
        if (octetInt > 255) {
            throw new IllegalArgumentException(message + " " + HtmlEncoder.encode(host));
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
            throw new IllegalArgumentException("IP Future must contain 'v<version>.': " + HtmlEncoder.encode(ipLiteral));
        }
        // always starts with v
        String version = host.substring(1, dot);
        checkNotBlank("Version", ipLiteral, version);
        validateHexDigits("Future version", ipLiteral, version);

        String address = host.substring(dot + 1);
        checkNotBlank("IP Future", ipLiteral, address);

        for (char c : address.toCharArray()) {
            if (c > 255) {
                throw new IllegalArgumentException("Host contains invalid character: " + HtmlEncoder.encode(ipLiteral));
            }
            if (UNRESERVED[c]) {
                continue;
            }
            if (SUB_DELIMS[c]) {
                continue;
            }
            if (c == ':') {
                continue;
            }
            throw new IllegalArgumentException("Host contains invalid character: " + HtmlEncoder.encode(ipLiteral));
        }
    }

    private static void checkNotBlank(String message, String ipLiteral, String toValidate) {
        if (toValidate.isBlank()) {
            throw new IllegalArgumentException(message + " cannot be blank: " + HtmlEncoder.encode(ipLiteral));
        }
    }
}
