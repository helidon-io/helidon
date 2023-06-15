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
    private static final Map<String, MediaType> RELAXED_TYPES = Map.of(
            "text",  MediaTypes.TEXT_PLAIN // text -> text/plain
    );

    /**
     * Find relaxed media type mapping for provided value.
     *
     * @param value source media type value
     * @return mapped media type value or {@code Optional.empty()}
     *         when no mapping for given value exists
     */
    static Optional<MediaType> findRelaxedMediaType(String value) {
        Objects.requireNonNull(value);
        MediaType relaxedValue = RELAXED_TYPES.get(value);
        return relaxedValue != null ? Optional.of(relaxedValue) : Optional.empty();
    }

}
