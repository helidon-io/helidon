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

package io.helidon.common.uri;

import java.net.IDN;
import java.util.Locale;
import java.util.Objects;

import io.helidon.common.Api;

/**
 * Normalized host value.
 * <p>
 * The value never contains a port or IPv6 brackets. DNS names are normalized to lower-case ASCII, IPv4 literals to
 * decimal dotted-quad form, and IPv6 literals to canonical compressed lower-case text form.
 * Generic registered names obtained from {@link UriAuthority#createForUri(String)} are normalized to lower case,
 * with unreserved percent-encoded characters decoded and other percent-encoded characters retained using upper-case hex digits.
 */
@Api.Stable
public final class UriHost {
    private final String value;
    private final Kind kind;

    private UriHost(String value, Kind kind) {
        this.value = value;
        this.kind = kind;
    }

    /**
     * Create a normalized DNS host or IP literal.
     * This method does not accept generic URI registered names; use {@link UriAuthority#createForUri(String)} for those.
     *
     * @param host host text, without a port; IPv6 literals must not use authority brackets, use {@link UriAuthority}
     *             for authority text
     * @return normalized host
     * @throws NullPointerException     in case the host is {@code null}
     * @throws IllegalArgumentException in case the host is invalid
     */
    public static UriHost create(String host) {
        Objects.requireNonNull(host, "host");

        if (host.isBlank()) {
            throw new IllegalArgumentException("Host cannot be blank");
        }

        if (host.indexOf(':') >= 0) {
            return ipv6(host);
        }

        if (UriValidator.isIpv4Address(host)) {
            return ipv4(host);
        }

        return dns(host);
    }

    static UriHost createForUri(String host) {
        Objects.requireNonNull(host, "host");
        boolean encoded = host.indexOf('%') >= 0;
        if (encoded) {
            UriValidator.validateNonIpLiteral(host);
        }
        String normalized = UriEncoding.decodeHost(host);
        if (normalized.indexOf(':') >= 0 || !isAscii(normalized)) {
            return create(normalized);
        }
        if (UriValidator.isIpv4Address(normalized)) {
            return ipv4(normalized);
        }
        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        String dnsHost = stripTerminalDot(lowerCase);
        if (dnsNameError(dnsHost) == null) {
            return new UriHost(dnsHost, Kind.DNS);
        }
        if (!encoded) {
            UriValidator.validateNonIpLiteral(host);
        }
        return new UriHost(UriEncoding.encodeHost(lowerCase), Kind.REGISTERED_NAME);
    }

    /**
     * Normalized host value.
     *
     * @return normalized host value
     */
    public String value() {
        return value;
    }

    /**
     * Host kind.
     *
     * @return host kind
     */
    public Kind kind() {
        return kind;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UriHost that)) {
            return false;
        }
        return kind == that.kind && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, kind);
    }

    @Override
    public String toString() {
        return value;
    }

    private static UriHost ipv4(String host) {
        if (!UriValidator.isIpv4Address(host)) {
            throw new IllegalArgumentException("Host is not a valid IPv4 address");
        }
        UriValidator.validateNonIpLiteral(host);

        StringBuilder normalized = new StringBuilder(host.length());
        int start = 0;
        int part = 0;
        while (start <= host.length()) {
            int dot = host.indexOf('.', start);
            int end = dot == -1 ? host.length() : dot;
            if (end == start) {
                throw new IllegalArgumentException("IPv4 address contains an empty segment");
            }
            int value = parseIpv4Segment(host, start, end);
            if (value > 255) {
                throw new IllegalArgumentException("IPv4 segment is greater than 255");
            }
            if (part > 0) {
                normalized.append('.');
            }
            normalized.append(value);
            part++;
            if (dot == -1) {
                break;
            }
            start = dot + 1;
        }
        if (part != 4) {
            throw new IllegalArgumentException("IPv4 address must contain exactly four segments");
        }
        return new UriHost(normalized.toString(), Kind.IPV4);
    }

    private static int parseIpv4Segment(String host, int start, int end) {
        int value = 0;
        for (int i = start; i < end; i++) {
            char c = host.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("IPv4 address contains a non-digit character");
            }
            value = value * 10 + c - '0';
            if (value > 255) {
                return value;
            }
        }
        return value;
    }

    private static UriHost ipv6(String host) {
        return new UriHost(UriValidator.normalizeIpv6Literal("[" + host + "]"), Kind.IPV6);
    }

    private static UriHost dns(String host) {
        String normalized = stripTerminalDot(host);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Host cannot be blank");
        }
        String ascii;
        try {
            ascii = IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Host is not a valid DNS name", e);
        }
        if (ascii.isBlank()) {
            throw new IllegalArgumentException("Host cannot be blank");
        }
        ascii = ascii.toLowerCase(Locale.ROOT);
        UriValidator.validateNonIpLiteral(ascii);
        validateDnsName(ascii);
        return new UriHost(ascii, Kind.DNS);
    }

    private static void validateDnsName(String host) {
        String error = dnsNameError(host);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    private static String dnsNameError(String host) {
        // RFC 1035, 2.3.4 limits a domain name to 255 octets on the wire; without the root label and label-length
        // octets, the textual host name is at most 253 characters.
        if (host.length() > 253) {
            return "Host DNS name is too long";
        }

        int labelStart = 0;
        for (int i = 0; i <= host.length(); i++) {
            if (i == host.length() || host.charAt(i) == '.') {
                String error = dnsLabelError(host, labelStart, i);
                if (error != null) {
                    return error;
                }
                labelStart = i + 1;
            }
        }
        return null;
    }

    private static String dnsLabelError(String host, int start, int end) {
        // RFC 1035, 2.3.1 defines labels as non-empty components.
        if (start == end) {
            return "Host DNS name contains an empty label";
        }
        // RFC 1035, 2.3.4 limits a DNS label to 63 octets.
        if (end - start > 63) {
            return "Host DNS label is too long";
        }
        // RFC 1035, 2.3.1 defines the preferred name syntax; RFC 1123, 2.1 relaxes the first character to also
        // allow digits, but labels still do not start or end with a hyphen.
        if (host.charAt(start) == '-' || host.charAt(end - 1) == '-') {
            return "Host DNS label must not start or end with a hyphen";
        }
        // RFC 1035, 2.3.1 plus RFC 1123, 2.1 allow letters, digits, and hyphen in DNS host labels.
        for (int i = start; i < end; i++) {
            char c = host.charAt(i);
            if (c >= 'a' && c <= 'z') {
                continue;
            }
            if (c >= '0' && c <= '9') {
                continue;
            }
            if (c == '-') {
                continue;
            }
            return "Host is not a valid DNS name";
        }
        return null;
    }

    private static boolean isAscii(String host) {
        for (int i = 0; i < host.length(); i++) {
            if (host.charAt(i) > 0x7f) {
                return false;
            }
        }
        return true;
    }

    private static String stripTerminalDot(String host) {
        if (host.endsWith(".")) {
            return host.substring(0, host.length() - 1);
        }
        return host;
    }

    /**
     * Host kind.
     */
    public enum Kind {
        /**
         * DNS name.
         */
        DNS,
        /**
         * IPv4 literal.
         */
        IPV4,
        /**
         * IPv6 literal.
         */
        IPV6,
        /**
         * URI registered name that is not a DNS name or IP literal.
         */
        REGISTERED_NAME
    }
}
