/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.lra.coordinator.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Headers propagated between Participant and Coordinator.
 */
public interface PropagatedHeaders {

    /**
     * Get all headers as a map.
     *
     * @return map of headers
     */
    Map<String, List<String>> toMap();

    /**
     * Scan map of headers for any headers with allowed prefix.
     * Any existing headers with same key is replaced.
     *
     * @param headers map to be scanned
     */
    void scan(Map<String, List<String>> headers);

    /**
     * Clear all headers.
     */
    void clear();

    /**
     * Create new instance, with prefixes for allowed headers.
     *
     * @param prefixes list of the allowed header prefixes
     * @return new instance, ready to scan for allowed headers
     */
    static PropagatedHeaders create(Set<String> prefixes) {
        if (prefixes.isEmpty()) {
            return new NoopPropagatedHeaders();
        }
        return new PrefixedPropagatedHeaders(prefixes);
    }

    /**
     * Create new noop instance, always returns same instance of empty map.
     *
     * @return noop instance
     */
    static PropagatedHeaders noop() {
        return new NoopPropagatedHeaders();
    }

    /**
     * Propagated headers which can scan for allowed headers with any of the preconfigured prefixes.
     */
    final class PrefixedPropagatedHeaders implements PropagatedHeaders {

        private final Map<String, List<String>> filteredMap = new HashMap<>();
        private final Set<String> prefixes;

        private PrefixedPropagatedHeaders(Set<String> prefixes) {
            this.prefixes = prefixes;
        }

        @Override
        public Map<String, List<String>> toMap() {
            return Collections.unmodifiableMap(filteredMap);
        }

        @Override
        public void scan(Map<String, List<String>> headers) {
            if (prefixes.isEmpty()) {
                return;
            }
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String key = entry.getKey();
                for (String prefix : prefixes) {
                    if (startsWithIgnoreCase(prefix, key)) {
                        filteredMap.put(key, Collections.unmodifiableList(entry.getValue()));
                    }
                }
            }
        }

        @Override
        public void clear() {
            filteredMap.clear();
        }
    }

    /**
     * Noop headers, always returns same instance of empty map.
     */
    final class NoopPropagatedHeaders implements PropagatedHeaders {

        private static final Map<String, List<String>> EMPTY_MAP = Map.of();

        private NoopPropagatedHeaders() {
        }

        @Override
        public Map<String, List<String>> toMap() {
            return EMPTY_MAP;
        }

        @Override
        public void scan(Map<String, List<String>> headers) {

        }

        @Override
        public void clear() {

        }
    }

    /**
     * Case-insensitive starts with check.
     *
     * @param prefix prefix to start with
     * @param value  value to check
     * @return true if the value starts with the prefix
     */
    private static boolean startsWithIgnoreCase(String prefix, String value) {
        if (prefix == null || value == null) {
            return Objects.equals(prefix, value);
        }
        int prefixLength = prefix.length();
        if (prefixLength > value.length()) {
            return false;
        }
        return value.regionMatches(true, 0, prefix, 0, prefixLength);
    }
}
