/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Information about URI.
 */
public class UriInfo {

    private final String scheme;
    private final String host;
    private final int port;
    private final String path;
    private final Optional<String> query;

    /**
     * Creates a new instance of {@code UriInfo}.
     *
     * @param scheme Scheme of the request ({@code http}, {@code https})
     * @param host Host part of authority of the request
     * @param port Port part of authority of the request
     * @param path Path of the request
     * @param query Query of the request
     */
    public UriInfo(String scheme,
                   String host,
                   int port,
                   String path,
                   Optional<String> query) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.path = path;
        this.query = query;
    }
    /**
     * Create URI from the information. This operation is expensive.
     *
     * @return URI to use
     */
    public URI toUri() {
        try {
            return new URI(scheme, authority(), path, query.orElse(null), null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("UriInfo cannot be used to create a URI: " + this, e);
        }
    }

    /**
     *
     * @return scheme from the URI
     */
    public String scheme() {
        return scheme;
    }

    /**
     *
     * @return host from the URI
     */
    public String host() {
        return host;
    }

    /**
     *
     * @return port from the URI
     */
    public int port() {
        return port;
    }

    /**
     *
     * @return path from the URI
     */
    public String path() {
        return path;
    }

    /**
     *
     * @return query string from the URI
     */
    public Optional<String> query() {
        return query;
    }

    /**
     * Authority (host:port) of this URI.
     *
     * @return authority
     */
    public String authority() {
        return host + ":" + port;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", UriInfo.class.getSimpleName() + "[", "]")
                .add("scheme='" + scheme + "'")
                .add("host='" + host + "'")
                .add("port=" + port)
                .add("path='" + path + "'")
                .add("query=" + query)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UriInfo uriInfo = (UriInfo) o;
        return port == uriInfo.port
                && scheme.equals(uriInfo.scheme)
                && host.equals(uriInfo.host)
                && path.equals(uriInfo.path)
                && query.equals(uriInfo.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, host, port, path, query);
    }
}
