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

package io.helidon.common.media.type;

/**
 * Media type support and known media types.
 * @see io.helidon.common.media.type.MediaTypes
 */
public interface MediaType {

    /**
     * Type, such as {@code application}.
     *
     * @return type part of the media type
     */
    String type();

    /**
     * Subtype, such as {@code yaml}.
     *
     * @return subtype part of the media type
     */
    String subtype();

    /**
     * Full type, such as {@code application/yaml}.
     *
     * @return full media type string
     * @deprecated use {@link #text()}
     */
    @Deprecated
    default String fullType() {
        return text();
    }

    /**
     * Full type, such as {@code application/yaml}.
     *
     * @return full media type string
     */
    String text();

    /**
     * Is the type a wildcard?
     * @return whether this is a wildcard type
     */
    default boolean isWildcardType() {
        return "*".equals(type());
    }

    /**
     * Is the subtype a wildcard?
     * @return whether this is a wildcard subtype
     */
    default boolean isWildcardSubtype() {
        return "*".equals(subtype());
    }

    /**
     * Tests if this media type has provided Structured Syntax {@code suffix} (RFC 6839).
     *
     * @param suffix Suffix with or without '+' prefix. If null or empty then returns {@code true} if this media type
     *               has ANY suffix.
     * @return {@code true} if media type has specified {@code suffix} or has any suffix if parameter is {@code null} or empty.
     */
    default boolean hasSuffix(String suffix) {
        if (suffix != null && !suffix.isEmpty()) {
            if (suffix.charAt(0) != '+') {
                suffix = "+" + suffix;
            }
            return subtype().endsWith(suffix);
        } else {
            return subtype().indexOf('+') >= 0;
        }
    }
}
