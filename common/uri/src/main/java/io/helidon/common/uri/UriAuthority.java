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

import java.util.Objects;

import io.helidon.common.Api;

/**
 * Normalized URI authority.
 * <p>
 * URI authority is a host plus an optional port. Userinfo is not supported.
 */
@Api.Stable
public final class UriAuthority {
    /**
     * Undefined port value.
     */
    public static final int UNDEFINED_PORT = -1;

    private final UriHost host;
    private final int port;

    private UriAuthority(UriHost host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Create a normalized authority from a host and optional port.
     * The host must be a DNS name or IP literal, not a generic URI registered name.
     *
     * @param host normalized host
     * @param port port, or {@value #UNDEFINED_PORT} when absent
     * @return normalized authority
     * @throws NullPointerException     in case the host is {@code null}
     * @throws IllegalArgumentException in case the host is a generic registered name or the port is invalid
     */
    public static UriAuthority create(UriHost host, int port) {
        Objects.requireNonNull(host, "host");
        if (host.kind() == UriHost.Kind.REGISTERED_NAME) {
            throw new IllegalArgumentException("Authority host must be a DNS name or IP literal");
        }
        validatePort(port);
        return new UriAuthority(host, port);
    }

    /**
     * Parse and normalize authority text.
     * <p>
     * Only DNS hosts and IP literals are accepted. DNS hosts are normalized to lower-case ASCII.
     * IPv6 authorities must use brackets. An explicit port must not be empty.
     * Use {@link #createForUri(String)} for generic URI registered names or an empty port.
     *
     * @param authority authority text containing a host and optional port
     * @return normalized authority
     * @throws NullPointerException     in case the authority is {@code null}
     * @throws IllegalArgumentException in case the authority is invalid
     */
    public static UriAuthority create(String authority) {
        return parse(authority, false);
    }

    /**
     * Parse and normalize URI authority text.
     * <p>
     * Unlike {@link #create(String)}, this method accepts generic registered names as defined by RFC 3986.
     * Unreserved percent-encoded characters are decoded. DNS and IP normalization is used when applicable;
     * other registered names are normalized to lower case, retaining other percent-encoded characters using upper-case
     * hex digits. IPv6 authorities must use brackets. An empty port is treated as absent.
     * Userinfo and empty hosts are not supported.
     *
     * @param authority authority text containing a host and optional port
     * @return normalized URI authority
     * @throws NullPointerException     in case the authority is {@code null}
     * @throws IllegalArgumentException in case the authority is invalid
     */
    public static UriAuthority createForUri(String authority) {
        return parse(authority, true);
    }

    /**
     * Create a normalized URI authority from a host and optional port.
     * <p>
     * Unlike {@link #create(UriHost, int)}, this method accepts generic registered names as defined by RFC 3986.
     * Host normalization is the same as for {@link #createForUri(String)}.
     *
     * @param host host text, without a port or IPv6 brackets
     * @param port port, or {@value #UNDEFINED_PORT} when absent
     * @return normalized URI authority
     * @throws NullPointerException     in case the host is {@code null}
     * @throws IllegalArgumentException in case the host or port is invalid
     */
    public static UriAuthority createForUri(String host, int port) {
        Objects.requireNonNull(host, "host");
        validatePort(port);
        return new UriAuthority(UriHost.createForUri(host), port);
    }

    /**
     * Host.
     *
     * @return normalized host
     */
    public UriHost host() {
        return host;
    }

    /**
     * Port.
     *
     * @return port, or {@value #UNDEFINED_PORT} when absent
     */
    public int port() {
        return port;
    }

    /**
     * Whether the authority has a port.
     *
     * @return whether a port is defined
     */
    public boolean hasPort() {
        return port != UNDEFINED_PORT;
    }

    /**
     * Port or a default value.
     *
     * @param defaultPort default port to use when the authority has no port
     * @return authority port or the provided default port
     */
    public int portOrDefault(int defaultPort) {
        return hasPort() ? port : defaultPort;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UriAuthority that)) {
            return false;
        }
        return port == that.port && host.equals(that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public String toString() {
        if (!hasPort()) {
            return hostText();
        }
        return hostText() + ":" + port;
    }

    private static UriAuthority parse(String authority, boolean allowRegisteredName) {
        Objects.requireNonNull(authority, "authority");
        if (authority.isBlank()) {
            throw new IllegalArgumentException("Authority cannot be blank");
        }
        if (authority.indexOf('@') >= 0) {
            throw new IllegalArgumentException("Authority must not contain userinfo");
        }

        if (authority.charAt(0) == '[') {
            return createBracketed(authority, allowRegisteredName);
        }

        int firstColon = authority.indexOf(':');
        if (firstColon == -1) {
            return new UriAuthority(createHost(authority, allowRegisteredName), UNDEFINED_PORT);
        }
        if (authority.indexOf(':', firstColon + 1) != -1) {
            throw new IllegalArgumentException("IPv6 authority must use square brackets");
        }
        return new UriAuthority(createHost(authority.substring(0, firstColon), allowRegisteredName),
                                parsePort(authority, firstColon + 1, allowRegisteredName));
    }

    private static UriHost createHost(String host, boolean allowRegisteredName) {
        return allowRegisteredName ? UriHost.createForUri(host) : UriHost.create(host);
    }

    private static UriAuthority createBracketed(String authority, boolean allowEmptyPort) {
        int closingBracket = authority.indexOf(']');
        if (closingBracket == -1) {
            throw new IllegalArgumentException("IPv6 authority is missing a closing bracket");
        }
        String host = authority.substring(1, closingBracket);
        if (host.isEmpty()) {
            throw new IllegalArgumentException("IPv6 authority host cannot be blank");
        }
        UriHost uriHost = UriHost.create(host);
        if (uriHost.kind() != UriHost.Kind.IPV6) {
            throw new IllegalArgumentException("Bracketed authority host must be an IPv6 literal");
        }
        return new UriAuthority(uriHost, bracketedPort(authority, closingBracket, allowEmptyPort));
    }

    private static int bracketedPort(String authority, int closingBracket, boolean allowEmptyPort) {
        if (closingBracket == authority.length() - 1) {
            return UNDEFINED_PORT;
        }
        if (authority.charAt(closingBracket + 1) != ':') {
            throw new IllegalArgumentException("Authority contains unexpected content after IPv6 host");
        }
        return parsePort(authority, closingBracket + 2, allowEmptyPort);
    }

    private static int parsePort(String authority, int offset, boolean allowEmptyPort) {
        if (offset == authority.length()) {
            if (allowEmptyPort) {
                return UNDEFINED_PORT;
            }
            throw new IllegalArgumentException("Authority port cannot be blank");
        }
        int result = 0;
        for (int i = offset; i < authority.length(); i++) {
            char c = authority.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Authority port must contain only digits");
            }
            result = result * 10 + c - '0';
            if (result > 65535) {
                throw new IllegalArgumentException("Authority port must be between 0 and 65535");
            }
        }
        return result;
    }

    private static void validatePort(int port) {
        if (port == UNDEFINED_PORT) {
            return;
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Authority port must be between 0 and 65535, or -1 when absent");
        }
    }

    private String hostText() {
        if (host.kind() == UriHost.Kind.IPV6) {
            return "[" + host.value() + "]";
        }
        return host.value();
    }
}
