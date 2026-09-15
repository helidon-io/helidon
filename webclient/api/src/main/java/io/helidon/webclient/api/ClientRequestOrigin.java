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

package io.helidon.webclient.api;

import java.util.Locale;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriHost;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;

/**
 * Normalized HTTP request origin used by WebClient security decisions.
 */
@Api.Internal
public final class ClientRequestOrigin {
    private final String scheme;
    private final UriAuthority authority;

    private ClientRequestOrigin(String scheme, UriAuthority authority) {
        this.scheme = scheme;
        this.authority = authority;
    }

    /**
     * Create an origin from the URI authority.
     *
     * @param uri request URI
     * @return normalized origin
     */
    public static ClientRequestOrigin create(ClientUri uri) {
        Objects.requireNonNull(uri, "uri");
        String scheme = normalizedScheme(uri.scheme());
        return new ClientRequestOrigin(scheme, uriAuthority(uri, scheme));
    }

    /**
     * Create an effective HTTP origin. The final {@code Host} header overrides the URI authority.
     *
     * @param uri request URI
     * @param headers final request headers
     * @return normalized effective origin
     */
    public static ClientRequestOrigin create(ClientUri uri, Headers headers) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        String scheme = normalizedScheme(uri.scheme());
        UriAuthority requestAuthority = headers.first(HeaderNames.HOST)
                .map(authority -> requestAuthority(uri, scheme, authority))
                .orElseGet(() -> uriAuthority(uri, scheme));
        UriAuthority authority = UriAuthority.create(requestAuthority.host(),
                                                     effectivePort(scheme, requestAuthority.port()));
        return new ClientRequestOrigin(scheme, authority);
    }

    /**
     * URI scheme.
     *
     * @return normalized lower-case scheme
     */
    public String scheme() {
        return scheme;
    }

    /**
     * Origin authority with an explicit effective port.
     *
     * @return normalized authority
     */
    public UriAuthority authority() {
        return authority;
    }

    /**
     * Apply this origin's authority to a copy of a request URI.
     *
     * @param uri request URI
     * @return URI using this origin's scheme and authority
     */
    public ClientUri apply(ClientUri uri) {
        return ClientUri.create(uri)
                .scheme(scheme)
                .host(clientUriHost(authority.host()))
                .port(authority.port());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ClientRequestOrigin that)) {
            return false;
        }
        return scheme.equals(that.scheme) && authority.equals(that.authority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, authority);
    }

    @Override
    public String toString() {
        return scheme + "://" + authority;
    }

    private static String normalizedScheme(String scheme) {
        return Objects.requireNonNull(scheme, "scheme").toLowerCase(Locale.ROOT);
    }

    private static UriAuthority uriAuthority(ClientUri uri, String scheme) {
        UriAuthority authority = UriAuthority.create(uri.authority());
        return UriAuthority.create(authority.host(), effectivePort(scheme, authority.port()));
    }

    private static UriAuthority requestAuthority(ClientUri uri, String scheme, String authority) {
        try {
            return UriAuthority.create(authority);
        } catch (IllegalArgumentException _) {
            return uriAuthority(uri, scheme);
        }
    }

    private static String clientUriHost(UriHost host) {
        return host.kind() == UriHost.Kind.IPV6 ? "[" + host.value() + "]" : host.value();
    }

    private static int effectivePort(String scheme, int port) {
        if (port != UriAuthority.UNDEFINED_PORT) {
            return port;
        }
        return "https".equals(scheme) ? 443 : 80;
    }
}
