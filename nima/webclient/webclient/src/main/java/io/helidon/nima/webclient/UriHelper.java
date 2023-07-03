/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;

/**
 * Helper for client URI handling.
 */
public class UriHelper {

    private static final Map<String, Integer> DEFAULT_PORTS = Map.of(
            "http", 80,
            "https", 443
    );
    private static final String EMPTY_STRING = "";
    private final String baseScheme;
    private final String baseAuthority;
    private final String basePath;
    private final String baseHost;
    private final int basePort;

    private String scheme;
    private String authority;
    private String path = EMPTY_STRING;
    private String host;
    private int port;
    private boolean skipUriEncoding = false;

    private UriHelper() {
        this.baseScheme = null;
        this.baseAuthority = null;
        this.basePath = null;
        this.baseHost = null;
        this.basePort = -1;
    }

    private UriHelper(URI baseUri, String basePath) {
        this.baseScheme = baseUri.getScheme();
        this.baseAuthority = baseUri.getAuthority();
        this.basePath = basePath;
        this.baseHost = baseUri.getHost();
        this.basePort = baseUri.getPort();
        this.scheme = baseScheme;
        this.authority = baseAuthority;
        this.path = basePath;
        this.host = baseHost;
        this.port = basePort;
    }

    /**
     * Create an empty URI helper.
     *
     * @return uri helper
     */
    public static UriHelper create() {
        return new UriHelper();
    }

    /**
     * Create a new helper.
     *
     * @param baseUri base URI
     * @param query   query to extract query parmaters into
     * @return uri helper
     */
    public static UriHelper create(URI baseUri, UriQueryWriteable query) {
        // todo handle fragment
        String basePath = extractQuery(baseUri.getPath(), query);
        return new UriHelper(baseUri, basePath);
    }

    @Override
    public String toString() {
        return scheme + "://" + authority + (path.startsWith("/") ? "" : "/") + path;
    }

    /**
     * Convert instance to {@link java.net.URI}. Does not include query or fragment.
     *
     * @return the converted URI
     */
    public URI toUri() {
        try {
            return new URI(scheme, authority, (path.startsWith("/") ? "" : "/") + path, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Scheme of this URI.
     *
     * @param scheme to use (such as {@code http})
     */
    public void scheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Host of this URI.
     *
     * @param host to connect to
     */
    public void host(String host) {
        this.host = host;
        authority(host, port);
    }

    /**
     * Port of this URI.
     *
     * @param port to connect to
     */
    public void port(int port) {
        this.port = port;
        authority(host, port);
    }

    /**
     * Path of this URI.
     *
     * @param path path to use
     * @param query writable query to extract query parameters to
     */
    public void path(String path, UriQueryWriteable query) {
        this.path = extractQuery(path, query);
    }

    /**
     * Whether to skip uri encoding.
     *
     * @param skipUriEncoding skip uri encoding
     */
    public void skipUriEncoding(boolean skipUriEncoding) {
        this.skipUriEncoding = skipUriEncoding;
    }

    /**
     * Resolve the provided URI against this URI and extract query from it.
     *
     * @param uri   URI to use
     * @param query query to configure from the provided URI
     */
    public void resolve(URI uri, UriQueryWriteable query) {
        if (uri.isAbsolute()) {
            this.scheme = uri.getScheme();
            this.path = extractQuery(uri.getPath(), query);
            this.host = uri.getHost();
            this.port = uri.getPort();
            authority(host, port);
            return;
        }
        String uriPath = extractQuery(uri.getPath(), query);

        if (this.scheme == null) {
            this.scheme = resolve(baseScheme, uri.getScheme());
            this.path = resolvePath(basePath, uriPath);
            this.host = resolve(baseHost, uri.getHost());
            this.port = resolvePort(basePort, uri.getPort());
            if (host == null) {
                return;
            }
            if (uri.getHost() == null && uri.getPort() == -1) {
                this.authority = baseAuthority;
            } else {
                authority(host, port);
            }
        } else {
            // we already have a custom URI
            this.scheme = resolve(this.scheme, uri.getScheme());
            this.path = resolvePath(this.path, uriPath);
            this.host = resolve(this.host, uri.getHost());
            this.port = resolvePort(this.port, uri.getPort());

            if (uri.getHost() != null || uri.getPort() != -1) {
                authority(host, port);
            }
        }
    }

    /**
     * Scheme of this URI.
     *
     * @return scheme
     */
    public String scheme() {
        return scheme;
    }

    /**
     * Authority of this URI.
     *
     * @return authority
     */
    public String authority() {
        return authority;
    }

    /**
     * Host of this URI.
     *
     * @return host
     */
    public String host() {
        return host;
    }

    /**
     * Port of this URI.
     *
     * @return port
     */
    public int port() {
        if (this.port == -1) {
            return DEFAULT_PORTS.getOrDefault(this.scheme, -1);
        }
        return port;
    }

    /**
     * Path of this URI.
     *
     * @return path
     */
    public String path() {
        return path;
    }

    /**
     * Encoded path with query and fragment.
     *
     * @param query query to use (may be empty)
     * @param fragment fragment to use (may be empty)
     * @return string containing encoded path with query
     */
    public String pathWithQueryAndFragment(UriQuery query, UriFragment fragment) {
        String queryString = skipUriEncoding ? query.value() : query.rawValue();

        boolean hasQuery = !queryString.isEmpty();

        String path;
        if (this.path.equals("")) {
            path = "/";
        } else {
            path = skipUriEncoding ? this.path : UriEncoding.encodeUri(this.path);
        }

        if (hasQuery) {
            path = path + '?' + queryString;
        }
        if (fragment.hasValue()) {
            String fragmentValue = skipUriEncoding ? fragment.value() : fragment.rawValue();
            path = path + '#' + fragmentValue;
        }

        return path;
    }

    private static String extractQuery(String path, UriQueryWriteable query) {
        if (path != null) {
            int i = path.indexOf('?');
            if (i > -1) {
                String queryString = path.substring(i + 1);
                query.fromQueryString(queryString);
                path = path.substring(0, i);
            }
        }
        return path;
    }

    private void authority(String host, int port) {
        if (port == -1) {
            this.authority = host;
        } else {
            this.authority = host + ":" + port;
        }
    }

    private int resolvePort(int port, int resolvePort) {
        if (resolvePort == -1) {
            return port;
        }
        return resolvePort;
    }

    private String resolvePath(String path, String resolvePath) {
        if (resolvePath == null) {
            return path;
        }
        if (resolvePath.isEmpty()) {
            return path;
        }
        if (path == null) {
            return resolvePath;
        }
        boolean pathEndsWith = path.length() != 0 && path.charAt(path.length() - 1) == '/';
        boolean pathStartsWith = resolvePath.charAt(0) == '/';

        if (pathEndsWith && pathStartsWith) {
            return new StringBuilder().append(path)
                    .append(resolvePath.subSequence(1, resolvePath.length()))
                    .toString();
        }
        if (pathEndsWith || pathStartsWith) {
            return new StringBuilder().append(path).append(resolvePath).toString();
        }
        if (path.isEmpty()) {
            return resolvePath;
        }
        return new StringBuilder().append(path)
                .append("/")
                .append(resolvePath)
                .toString();
    }

    private String resolve(String originalValue, String resolveValue) {
        if (resolveValue == null) {
            return originalValue;
        }
        return resolveValue;
    }
}
