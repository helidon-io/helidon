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

/**
 * API used to configure headers when propagating tracing information across service boundaries.
 */
public interface HeaderConsumer extends HeaderProvider {
    /**
     * Create a header consumer over a map of headers (must be mutable).
     *
     * @param headers headers to update
     * @return a new consumer
     */
    static HeaderConsumer create(Map<String, List<String>> headers) {
        return new MapHeaderConsumer(headers);
    }

    /**
     * Set the value(s) if not already set.
     *
     * @param key header name
     * @param values header value(s)
     */
    void setIfAbsent(String key, String... values);

    /**
     * Set the value(s).
     *
     * @param key header name
     * @param values header value(s)
     */
    void set(String key, String... values);
}
