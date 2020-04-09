/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.cors;

import java.util.List;
import java.util.Optional;

/**
 * Minimal abstraction of an HTTP request.
 *
 * @param <T> type of the request wrapped by the adapter
 */
public interface RequestAdapter<T> {

    /**
     *
     * @return possibly unnormalized path from the request
     */
    String path();

    /**
     * Retrieves the first value for the specified header as a String.
     *
     * @param key header name to retrieve
     * @return the first header value for the key
     */
    Optional<String> firstHeader(String key);

    /**
     * Reports whether the specified header exists.
     *
     * @param key header name to check for
     * @return whether the header exists among the request's headers
     */
    boolean headerContainsKey(String key);

    /**
     * Retrieves all header values for a given key as Strings.
     *
     * @param key header name to retrieve
     * @return header values for the header; empty list if none
     */
    List<String> allHeaders(String key);

    /**
     * Reports the method name for the request.
     *
     * @return the method name
     */
    String method();

    /**
     * Returns the request this adapter wraps.
     *
     * @return the request
     */
    T request();
}
