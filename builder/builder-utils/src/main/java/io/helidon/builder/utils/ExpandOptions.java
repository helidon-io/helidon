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

package io.helidon.builder.utils;

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Used by {@link BuilderUtils#expand(Object, ExpandOptions)}.
 */
@Builder
public interface ExpandOptions {

    /**
     * Should an empty collection map to {@code null}. The default value is {@code true}. This is useful for cases wheere the
     * expansion will be diff'ed using {@link BuilderUtils#diff(Object, Object, DiffOptions)}.
     *
     * @return true if any empty collection type should map to a null
     */
    @ConfiguredOption("true")
    boolean emptyCollectionMapsToNull();

    /**
     * Should type information be included in the expansion. The default value is {@code true}.
     *
     * @return true if type information should be captured in the the expansion
     */
    @ConfiguredOption("true")
    boolean includeTypeInformation();

    /**
     * Should maps and collections be attempted to be sorted. This is a best effort, applicable only if the contents of the
     * collection support {@link Comparable}. The default value is {@code true}.
     *
     * @return true if maps and collections should be sorted
     */
    @ConfiguredOption("true")
    boolean sortCollections();

}
