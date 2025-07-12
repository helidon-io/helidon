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

package io.helidon.webclient.api;

import java.net.URI;

import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;

/**
 * URI abstraction for WebClient.
 */
public class ClientUri implements UriInfo {
    private final UriInfo base;
    private final UriQueryWriteable query;

    private UriInfo.Builder uriBuilder;
    private boolean skipUriEncoding = false;

    private ClientUri() {
        this.base = null;
        this.query = UriQueryWriteable.create();
        this.uriBuilder = UriInfo.builder();
    }

    private ClientUri(ClientUri baseUri) {
        this.base = baseUri;
        this.uriBuilder = UriInfo.builder(base);
        this.skipUriEncoding = baseUri.skipUriEncoding;
        this.query = UriQueryWriteable.create().from(baseUri.query());
    }

    private ClientUri(UriInfo baseUri) {
        this.base = baseUri;
        this.uriBuilder = UriInfo.builder(baseUri);
        this.skipUriEncoding = false;
        this.query = UriQueryWriteable.create().from(baseUri.query());
    }

    /**
     * Create an empty URI helper.
     *
     * @return uri helper
     */
    public static ClientUri create() {
        return new ClientUri();
    }

    /**
     * Create a new client uri.
     *
     * @param baseUri base URI
     * @return a new client uri
     */
    public static ClientUri create(ClientUri baseUri) {
        return new ClientUri(baseUri);
    }

    /**
     * Create a new client uri.
     *
     * @param baseUri base URI
     * @return a new client uri
     */
    public static ClientUri create(UriInfo baseUri) {
        return new ClientUri(baseUri);
    }

    /**
     * Create a new client URI from an existing URI.
     *
     * @param baseUri base URI
     * @return a new client uri
     */
    public static ClientUri create(URI baseUri) {
        return create().resolve(baseUri);
    }

    @Override
    public String toString() {
        UriInfo info = uriBuilder.query(this.query).build();
        String encodedPath = pathWithQueryAndFragment();
        return info.scheme() + "://"
                + info.authority()
                + (encodedPath.startsWith("/") ? "" : "/") + encodedPath;
    }

    /**
     * Convert instance to {@link java.net.URI}.
     *
     * @return the converted URI
     */
    public URI toUri() {
        UriInfo info = uriBuilder.build();
        return URI.create(info.scheme() + "://"
                + info.authority()
                + pathWithQueryAndFragment());
    }

    /**
     * Scheme of this URI.
     *
     * @param scheme to use (such as {@code http})
     * @return updated instance
     */
    public ClientUri scheme(String scheme) {
        uriBuilder.scheme(scheme);
        return this;
    }

    /**
     * Host of this URI.
     *
     * @param host to connect to
     * @return updated instance
     */
    public ClientUri host(String host) {
        uriBuilder.host(host);
        return this;
    }

    /**
     * Port of this URI.
     *
     * @param port to connect to
     * @return updated instance
     */
    public ClientUri port(int port) {
        uriBuilder.port(port);
        return this;
    }

    /**
     * Path of this URI.
     *
     * @param path path to use
     * @return updated instance
     */
    public ClientUri path(String path) {
        uriBuilder.path(extractQuery(path));
        return this;
    }

    /**
     * Whether to skip uri encoding.
     *
     * @param skipUriEncoding skip uri encoding
     * @return updated instance
     */
    public ClientUri skipUriEncoding(boolean skipUriEncoding) {
        this.skipUriEncoding = skipUriEncoding;
        return this;
    }

    /**
     * Resolve the provided URI against this URI.
     *
     * @param uri URI to use
     * @return updated instance
     */
    public ClientUri resolve(URI uri) {
        if (uri.isAbsolute()) {
            this.uriBuilder = UriInfo.builder();
            this.query.clear();
        }

        if (uri.getScheme() != null) {
            uriBuilder.scheme(uri.getScheme());
        }
        if (uri.getHost() != null) {
            uriBuilder.host(uri.getHost());
        } else if (uri.getAuthority() != null) {
            throw new IllegalArgumentException("Invalid authority: " + uri.getAuthority());
        }
        if (uri.getPort() != -1) {
            uriBuilder.port(uri.getPort());
        }

        uriBuilder.path(resolvePath(uriBuilder.path().path(), uri.getPath()));

        String queryString = uri.getRawQuery();
        if (queryString != null) {
            // class URI does not decode +'s, so we do it here
            query.fromQueryString(queryString.replaceAll("\\+", "%20"));
        }

        if (uri.getRawFragment() != null) {
            uriBuilder.fragment(UriFragment.create(uri.getRawFragment()));
        }

        return this;
    }

    /**
     * Resolve the provided path against the current path of this URI.
     *
     * @param path to resolve
     * @return updated URI
     */
    public ClientUri resolvePath(String path) {
        uriBuilder.path(resolvePath(uriBuilder.path().path(), path));
        return this;
    }

    /**
     * Replaces the current URI with values from the provided URI.
     *
     * @param uri URI to use
     * @return updated instance
     */
    public ClientUri resolve(ClientUri uri) {
        this.uriBuilder.from(uri);
        return this;
    }

    @Override
    public String scheme() {
        return uriBuilder.scheme();
    }

    @Override
    public String host() {
        return uriBuilder.host();
    }

    @Override
    public UriQuery query() {
        return query;
    }

    @Override
    public UriFragment fragment() {
        return uriBuilder.fragment();
    }

    @Override
    public String authority() {
        return uriBuilder.build().authority();
    }

    @Override
    public int port() {
        return uriBuilder.build().port();
    }

    @Override
    public UriPath path() {
        return uriBuilder.path();
    }

    /**
     * Configure the fragment for this URI.
     *
     * @param fragment fragment to use
     * @return updated URI
     */
    public ClientUri fragment(UriFragment fragment) {
        uriBuilder.fragment(fragment);
        return this;
    }

    /**
     * Configure the fragment for this URI, using its decoded form ("human readable"). If you have an encoded fragment,
     * please use {@link #fragment(io.helidon.common.uri.UriFragment)}.
     *
     * @param fragment decoded fragment
     * @return updated URI
     */
    public ClientUri fragment(String fragment) {
        return fragment(UriFragment.createFromDecoded(fragment));
    }

    /**
     * URI query that can update values.
     *
     * @return writeable query
     */
    public UriQueryWriteable writeableQuery() {
        return query;
    }

    /**
     * Encoded path with query and fragment.
     *
     * @return string containing encoded path with query
     */
    public String pathWithQueryAndFragment() {
        UriInfo info = uriBuilder.query(query).build();

        String queryString = skipUriEncoding ? info.query().value() : info.query().rawValue();
        String path = skipUriEncoding ? info.path().path() : info.path().rawPath();

        boolean hasQuery = !queryString.isEmpty();

        if (path.isEmpty()) {
            path = "/";
        }

        if (hasQuery) {
            path = path + '?' + queryString;
        }

        if (info.fragment().hasValue()) {
            String fragmentValue = skipUriEncoding ? info.fragment().value() : info.fragment().rawValue();
            path = path + '#' + fragmentValue;
        }

        return path;
    }

    private String extractQuery(String path) {
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
        boolean pathEndsWith = !path.isEmpty() && path.charAt(path.length() - 1) == '/';
        boolean pathStartsWith = resolvePath.charAt(0) == '/';

        if (pathEndsWith && pathStartsWith) {
            return path + resolvePath.subSequence(1, resolvePath.length());
        }
        if (pathEndsWith || pathStartsWith) {
            return path + resolvePath;
        }
        if (path.isEmpty()) {
            return resolvePath;
        }
        return path
                + "/"
                + resolvePath;
    }
}
