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
package io.helidon.tracing;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API used to obtain headers when reading propagated tracing information incoming across service boundaries.
 */
public interface HeaderProvider {
    /**
     * Empty headers.
     *
     * @return empty headers provider
     */
    static HeaderProvider empty() {
        return create(Map.of());
    }

    /**
     * Header provider from an existing map of headers (can be read only).
     *
     * @param inboundHeaders headers to use
     * @return a new header provider
     */

    static HeaderProvider create(Map<String, List<String>> inboundHeaders) {
        return new MapHeaderConsumer(Map.copyOf(inboundHeaders));
    }

    /**
     * All keys available in the headers (header names).
     *
     * @return iterable of keys
     */
    Iterable<String> keys();

    /**
     * Get a header based on its name.
     *
     * @param key name of the header
     * @return first header value if present in the headers
     */
    Optional<String> get(String key);

    /**
     * Get a header based on its name, returning all values.
     *
     * @param key name of the header
     * @return all header values, or empty iterable if the header does not exist
     */
    Iterable<String> getAll(String key);

    /**
     * Whether a header is present.
     *
     * @param key name of the header
     * @return {@code true} if the header exists
     */
    boolean contains(String key);
}
