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

package io.helidon.cors;

import java.util.List;
import java.util.Optional;

import io.helidon.common.uri.UriInfo;
import io.helidon.http.HeaderName;

/**
 * <em>Not for use by developers.</em>
 *
 * Minimal abstraction of an HTTP request.
 *
 * @param <T> type of the request wrapped by the adapter
 */
public interface CorsRequestAdapter<T> {

    /**
     * @return possibly unnormalized path from the request
     */
    String path();

    /**
     * Returns the {@link io.helidon.common.uri.UriInfo} for the request.
     *
     * @return URI info for the request
     */
    UriInfo requestedUri();

    /**
     * Retrieves the first value for the specified header as a String.
     *
     * @param key header name to retrieve
     * @return the first header value for the key
     */
    Optional<String> firstHeader(HeaderName key);

    /**
     * Reports whether the specified header exists.
     *
     * @param key header name to check for
     * @return whether the header exists among the request's headers
     */
    boolean headerContainsKey(HeaderName key);

    /**
     * Retrieves all header values for a given key as Strings.
     *
     * @param key header name to retrieve
     * @return header values for the header; empty list if none
     */
    List<String> allHeaders(HeaderName key);

    /**
     * Reports the method name for the request.
     *
     * @return the method name
     */
    String method();

    /**
     * Processes the next handler/filter/request processor in the chain.
     */
    void next();

    /**
     * Returns the request this adapter wraps.
     *
     * @return the request
     */
    T request();
}
