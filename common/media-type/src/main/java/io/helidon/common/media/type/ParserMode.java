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

package io.helidon.common.media.type;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Media type parsing mode.
 */
public enum ParserMode {

    /**
     * Strict mode (default).
     * Media type must match known name.
     */
    STRICT,
    /**
     * Relaxed mode.
     * Apply additional rules to identify unknown media types.
     */
    RELAXED;

    // Relaxed media types mapping
    private static final Map<String, String> RELAXED_TYPES = Map.of(
            "text", "text/plain"
    );

    // Lower-case value names mapping
    private static final Map<String, ParserMode> VALUE_OF_MAPPING = Map.of(
            STRICT.name().toLowerCase(), STRICT,
            RELAXED.name().toLowerCase(), RELAXED
    );

    /**
     * Find relaxed media type mapping for provided value.
     *
     * @param value source media type value
     * @return mapped media type value or {@code Optional.empty()}
     *         when no mapping for given value exists
     */
    public static Optional<String> findRelaxedMediaType(String value) {
        Objects.requireNonNull(value);
        String relaxedValue = RELAXED_TYPES.get(value);
        return (relaxedValue != null) ? Optional.of(relaxedValue) : Optional.empty();
    }

    // Config value resolving helper
    /**
     * Resolve {@link String} values to {@link ParserMode} instances.
     * Matching is case-insensitive, source names are converted to lower-case.
     *
     * @param name ParserMode instance name
     * @param defaultMode ParserMode instance to use when provided name
     *                    does not match any known name.
     * @return matching {@link ParserMode} instance or {@code defaultMode}
     *         when provided name does not match any known mode name.
     */
    public static ParserMode valueOfIgnoreCase(String name, ParserMode defaultMode) {
        Objects.requireNonNull(name);
        return VALUE_OF_MAPPING.getOrDefault(name, defaultMode);
    }

}
