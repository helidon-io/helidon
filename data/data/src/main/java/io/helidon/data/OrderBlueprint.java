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
 * Single rule of the dynamic ordering of the query result.
 * <p>
 * {@link Order} class is the single item of the {@link java.util.List} stored in {@link Sort}.
 * It represents single ordering rule. E.g. in JPQL query
 * {@code SELECT t FROM Type t WHERE t.name = :name ORDER BY t.name DESC, t.id} this is the
 * {@code t.name DESC} and {@code t.id} as two separate {@link Order} instances.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(OrderSupport.class)
interface OrderBlueprint {

    /**
     * Query result ordering with no rules set (unsorted).
     */
    Order[] UNSORTED = new Order[] {};

    /**
     * Entity property used for ordering.
     * <p>
     * E.g. this is the path expression in Jakarta Persistence API. In following JPQL query:
     * {@code SELECT t FROM Type t WHERE t.name = :name ORDER BY t.name} the entity property
     * is the {@code t.name} path expression after {@code ORDER} BY keyword.
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
     * Whether ordering is case-sensitive.
     * Default value is {@code true}.
     *
     * @return value of {@code true} when ordering is case-sensitive or {@code false} otherwise
     */
    @Option.Default("true")
    boolean caseSensitive();

}
