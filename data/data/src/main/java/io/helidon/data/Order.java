/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data;

/**
 * Order definition of the query result ordering.
 */
public interface Order {

    /**
     * Create new instance of order definition with default direction and case-sensitivity.
     * Default direction is {@link Direction#ASC} and default value of {@link #ignoreCase()} is {@code false}.
     *
     * @param property entity property
     * @return new order definition instance
     */
    static Order create(String property) {
        return new OrderImpl(property, Direction.ASC, false);
    }

    /**
     * Create new instance of order definition with default case-sensitivity.
     * Default value of {@link #ignoreCase()} is {@code false}.
     *
     * @param property  entity property
     * @param direction direction of the ordering
     * @return new order definition instance
     */
    static Order create(String property, Direction direction) {
        return new OrderImpl(property, direction, false);
    }

    /**
     * Create new instance of order definition.
     *
     * @param property   entity property
     * @param direction  direction of the ordering
     * @param ignoreCase value of {@code true} for case-insensitive or {@code false} for case-sensitive ordering
     * @return new order definition instance
     */
    static Order create(String property, Direction direction, boolean ignoreCase) {
        return new OrderImpl(property, direction, ignoreCase);
    }

    /**
     * Entity property used for ordering.
     *
     * @return entity property
     */
    String property();

    /**
     * Direction of the ordering.
     *
     * @return direction of the ordering
     */
    Direction direction();

    /**
     * Whether ordering is case-insensitive.
     *
     * @return value of {@code true} when ordering is case-insensitive or {@code false} otherwise
     */
    boolean ignoreCase();

    /**
     * Direction of the ordering.
     */
    enum Direction {
        /**
         * Ordering in ascending direction.
         */
        ASC,
        /**
         * Ordering in descending direction.
         */
        DESC
    }

}
