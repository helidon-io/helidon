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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Order definition of the query result ordering.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(OrderSupport.class)
interface OrderBlueprint {

    /**
     * Query result ordering with no ordering set (unsorted).
     */
    Order[] UNSORTED = new Order[] {};

    /**
     * Entity property used for ordering.
     *
     * @return entity property
     */
    String property();

    /**
     * Direction of the ordering.
     * Default value is {@link OrderDirection#ASC}.
     *
     * @return direction of the ordering
     */
    @Option.Default("ASC")
    OrderDirection direction();

    /**
     * Whether ordering is case-insensitive.
     * Default value is {@code false}.
     *
     * @return value of {@code true} when ordering is case-insensitive or {@code false} otherwise
     */
    @Option.Default("false")
    boolean ignoreCase();

}
