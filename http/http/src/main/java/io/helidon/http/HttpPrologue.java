/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.service.registry.Service;

/**
 * A prologue of an HTTP protocol.
 */
@Service.Describe(Service.PerRequest.class)
public class HttpPrologue {
    private final String rawProtocol;
    private final String protocol;
    private final String protocolVersion;
    private final Method method;
    private final UriPath uriPath;
    private final String rawQuery;
    private final String rawFragment;

    private UriQuery query;
    private UriFragment fragment;

    /**
     * @param protocol        protocol name, should be {@code HTTP} in most cases
     * @param protocolVersion HTTP protocol version of this request
     * @param method          HTTP method of this request
     * @param path            URI path
     * @param rawQuery        query as received over the network, may be {@code null} when query is not present
     * @param rawFragment     fragment as received over the network; may be {@code null} when the prologue does not contain a
     *                        fragment
     */
    private HttpPrologue(String rawProtocol,
                         String protocol,
                         String protocolVersion,
                         Method method,
                         UriPath path,
                         String rawQuery,
                         String rawFragment) {
        this.rawProtocol = rawProtocol;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.method = method;
        this.uriPath = path;
        this.rawQuery = rawQuery;
        this.rawFragment = rawFragment;
    }

    private HttpPrologue(String rawProtocol,
                         String protocol,
                         String protocolVersion,
                         Method httpMethod,
                         UriPath uriPath,
                         UriQuery uriQuery,
                         UriFragment uriFragment) {
        this.rawProtocol = rawProtocol;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.method = httpMethod;
        this.uriPath = uriPath;
        this.rawQuery = uriQuery.rawValue();
        this.rawFragment = uriFragment.hasValue() ? uriFragment.rawValue() : null;

        this.fragment = uriFragment;
        this.query = uriQuery;
    }

    /**
     * Create a new prologue.
     *
     * @param rawProtocol     raw protocol string (full HTTP/1.1 or similar)
     * @param protocol        protocol
     * @param protocolVersion protocol version
     * @param httpMethod      HTTP Method
     * @param unresolvedPath  unresolved path
     * @param validatePath    whether to validate path (that it contains only allowed characters)
     * @return a new prologue
     */
    public static HttpPrologue create(String rawProtocol,
                                      String protocol,
                                      String protocolVersion,
                                      Method httpMethod,
                                      String unresolvedPath,
                                      boolean validatePath) {

        String rawPath = unresolvedPath;
        String rawFragment;
        String rawQuery;

        int fragment = rawPath.lastIndexOf('#');
        if (fragment > -1) {
            rawFragment = rawPath.substring(fragment + 1);
            rawPath = rawPath.substring(0, fragment);
        } else {
            rawFragment = null;
        }
        int query = rawPath.indexOf('?');
        if (query > -1) {
            rawQuery = rawPath.substring(query + 1);
            rawPath = rawPath.substring(0, query);
        } else {
            rawQuery = null;
        }

        UriPath uriPath = UriPath.create(rawPath);

        if (validatePath) {
            uriPath.validate();
        }

        return new HttpPrologue(rawProtocol,
                                protocol,
                                protocolVersion,
                                httpMethod,
                                uriPath,
                                rawQuery,
                                rawFragment);
    }

    /**
     * Create a new prologue with decoded values.
     *
     * @param rawProtocol     raw protocol string (full HTTP/1.1 or similar)
     * @param protocol        protocol
     * @param protocolVersion protocol version
     * @param httpMethod      HTTP Method
     * @param uriPath         resolved path
     * @param uriQuery        resolved query
     * @param uriFragment     resolved fragment
     * @return a new prologue
     */
    public static HttpPrologue create(String rawProtocol,
                                      String protocol,
                                      String protocolVersion,
                                      Method httpMethod,
                                      UriPath uriPath,
                                      UriQuery uriQuery,
                                      UriFragment uriFragment) {
        return new HttpPrologue(rawProtocol, protocol, protocolVersion, httpMethod, uriPath, uriQuery, uriFragment);
    }

    /**
     * Raw protocol, should be {@code HTTP/1.1} or {@code HTTP/2} in most cases.
     *
     * @return protocol
     */
    public String rawProtocol() {
        return rawProtocol;
    }

    /**
     * Protocol name, should be {@code HTTP} in most cases.
     *
     * @return protocol
     */
    public String protocol() {
        return protocol;
    }

    /**
     * HTTP protocol version of this request.
     *
     * @return protocol
     */
    public String protocolVersion() {
        return protocolVersion;
    }

    /**
     * HTTP method of this request.
     *
     * @return method
     */
    public Method method() {
        return method;
    }

    /**
     * Path or the request.
     *
     * @return path
     */
    public UriPath uriPath() {
        return uriPath;
    }

    /**
     * Query of the request.
     *
     * @return query
     */
    public UriQuery query() {
        if (query == null) {
            query = rawQuery == null ? UriQuery.empty() : UriQuery.create(rawQuery);
        }
        return query;
    }

    /**
     * Fragment of the request.
     *
     * @return fragment
     */
    public UriFragment fragment() {
        if (fragment == null) {
            fragment = rawFragment == null ? UriFragment.empty() : UriFragment.create(rawFragment);
        }
        return fragment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, protocolVersion, method, uriPath, query(), fragment());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (HttpPrologue) obj;
        return Objects.equals(this.protocol, that.protocol())
                && Objects.equals(this.protocolVersion, that.protocolVersion())
                && Objects.equals(this.method, that.method())
                && Objects.equals(this.uriPath, that.uriPath())
                && Objects.equals(this.query, that.query())
                && Objects.equals(this.fragment, that.fragment());
    }

    @Override
    public String toString() {
        return "HttpPrologue["
                + "protocol=" + protocol + ", "
                + "protocolVersion=" + protocolVersion + ", "
                + "method=" + method + ", "
                + "uriPath=" + uriPath + ", "
                + "query=" + query() + ", "
                + "fragment=" + fragment() + ']';
    }
}
