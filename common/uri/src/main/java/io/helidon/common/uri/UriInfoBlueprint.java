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

package io.helidon.common.uri;

import java.net.URI;
import java.net.URISyntaxException;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Information about URI, that can be used to invoke a specific request over the network.
 */
@Prototype.Blueprint(decorator = UriBuilderSupport.UriInfoInterceptor.class)
@Prototype.CustomMethods(UriBuilderSupport.UriInfoCustomMethods.class)
interface UriInfoBlueprint {

    /**
     * Scheme of the request ({@code http}, {@code https}).
     *
     * @return the scheme, defaults to {@code http}
     */
    @Option.Default("http")
    String scheme();

    /**
     * Host part of authority of the request.
     *
     * @return host, defaults to {@code localhost}
     */
    @Option.Default("localhost")
    String host();

    /**
     * Port part of authority of the request.
     * If port is not defined (e.g. authority without a port is used, or none is configured), the default
     * port is used based on the defined {@link #scheme()} - for {@code http} the port would be {@code 80},
     * and for {@code https} the port would be {@code 443}. If the scheme is different, if it ends with {@code s},
     * port would be {@code 443}, otherwise {@code 80}.
     *
     * @return port
     */
    int port();

    /**
     * Authority (host:port) of this URI.
     *
     * @return authority
     */
    default String authority() {
        return host() + ":" + port();
    }

    /**
     * Path of the request.
     *
     * @return path
     */
    @Option.Default("root()")
    UriPath path();

    /**
     * URI Query of the request.
     *
     * @return query, may be {@link io.helidon.common.uri.UriQuery#isEmpty() empty}
     */
    @Option.Default("empty()")
    UriQuery query();

    /**
     * Uri Fragment of the request.
     *
     * @return fragment, may be {@link io.helidon.common.uri.UriFragment#empty() empty}
     */
    @Option.Default("empty()")
    UriFragment fragment();

    /**
     * Create a URI from information in this URI info.
     * Creating a URI is a relatively expensive operation (as it always validates and does some additional operations).
     *
     * @return a new URI
     */
    default URI toUri() {
        try {
            return new URI(scheme(),
                           authority(),
                           path().path(),
                           query().isEmpty() ? null : query().value(),
                           fragment().hasValue() ? fragment().value() : null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("UriInfo cannot be used to create a URI: " + this, e);
        }
    }
}
