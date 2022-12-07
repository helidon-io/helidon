/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.Optional;

/**
 * Information about URI.
 *
 * @param scheme Scheme of the request ({@code http}, {@code https})
 * @param host Host part of authority of the request
 * @param port Port part of authority of the request
 * @param path Path of the request
 * @param query Query of the request
 */
public record UriInfo(String scheme,
                      String host,
                      int port,
                      String path,
                      Optional<String> query) {

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
     * Authority (host:port) of this URI.
     *
     * @return authority
     */
    public String authority() {
        return host + ":" + port;
    }
}
