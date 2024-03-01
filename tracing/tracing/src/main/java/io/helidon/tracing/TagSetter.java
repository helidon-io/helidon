/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

/**
 * Behavior for allowing callers to assign tag names and values.
 *
 * @param <T> the type returned by the tag-setting methods
 */
interface TagSetter<T> {

    /**
     * Add a tag to this span.
     *
     * @param tag tag to add
     * @return current span
     */
    T tag(Tag<?> tag);

    /**
     * Add a string tag.
     *
     * @param key   tag key
     * @param value tag value
     * @return current span
     */
    T tag(String key, String value);

    /**
     * Add a boolean tag.
     *
     * @param key   tag key
     * @param value tag value
     * @return current span
     */
    T tag(String key, Boolean value);

    /**
     * Add a numeric tag.
     *
     * @param key   tag key
     * @param value tag value
     * @return current span
     */
    T tag(String key, Number value);

}
