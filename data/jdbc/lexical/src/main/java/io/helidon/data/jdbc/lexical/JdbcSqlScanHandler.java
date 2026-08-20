/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.lexical;

import io.helidon.common.Api;

/**
 * Receives ordered regions and markers from a JDBC SQL lexical scan.
 * <p>
 * Every end offset is exclusive. A handler can copy source regions, count
 * markers, or reject marker forms without owning the lexical state machine.
 */
@Api.Internal
public interface JdbcSqlScanHandler {

    /**
     * Receives ordinary SQL which has no marker or protected region boundary.
     *
     * @param start first source offset
     * @param end source offset after the region
     */
    default void ordinary(int start, int end) {
    }

    /**
     * Receives one complete quoted or commented region.
     *
     * @param kind kind of protected region
     * @param start first source offset
     * @param end source offset after the region
     */
    default void protectedRegion(RegionKind kind, int start, int end) {
    }

    /**
     * Receives a named marker including its leading colon.
     *
     * @param start colon offset
     * @param end source offset after the marker name
     */
    default void namedMarker(int start, int end) {
    }

    /**
     * Receives a positional question mark marker.
     *
     * @param offset question mark offset
     */
    default void positionalMarker(int offset) {
    }

    /**
     * Kinds of SQL regions whose contents do not contain active bind markers.
     */
    enum RegionKind {

        /**
         * A single quoted string.
         */
        SINGLE_QUOTE,

        /**
         * A double quoted identifier.
         */
        DOUBLE_QUOTE,

        /**
         * A backtick quoted identifier.
         */
        BACKTICK_IDENTIFIER,

        /**
         * A bracket quoted identifier.
         */
        BRACKET_IDENTIFIER,

        /**
         * A line comment.
         */
        LINE_COMMENT,

        /**
         * A block comment.
         */
        BLOCK_COMMENT,

        /**
         * A PostgreSQL dollar quoted string.
         */
        DOLLAR_QUOTE,

        /**
         * An Oracle alternative quoted string.
         */
        ALTERNATIVE_QUOTE
    }
}
