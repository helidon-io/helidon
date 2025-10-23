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

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Dynamic ordering of the query result.
 * <p>
 * {@link Sort} class represents the whole {@link List} of query ordering rules. E.g. in JPQL query
 * {@code SELECT t FROM Type t WHERE t.name = :name ORDER BY t.name DESC, t.id} this is the whole
 * content of the {@code ORDER BY} clause.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(SortSupport.class)
interface SortBlueprint {

    /**
     * {@link List} of query ordering rules.
     * Default value is an empty {@link List} to return unordered result.
     *
     * @return {@link List} of order definitions
     */
    @Option.Singular
    @Option.Default("UNSORTED")
    List<Order> orderBy();

    /**
     * Whether any order definitions are set.
     *
     * @return value of {@code true} when at least one order definition is set or {@code false} otherwise
     */
    default boolean isSorted() {
        return !orderBy().isEmpty();
    }

}
