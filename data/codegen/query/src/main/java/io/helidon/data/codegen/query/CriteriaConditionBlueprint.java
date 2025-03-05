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
package io.helidon.data.codegen.query;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Criteria condition.
 * Condition always consists of entity property, criteria parameters and condition operator,
 * e.g. {@code entity.age <= :age}
 */
@Prototype.Blueprint
@Prototype.CustomMethods(CriteriaConditionSupport.class)
interface CriteriaConditionBlueprint {

    /**
     * Condition operator.
     *
     * @return the condition operator
     */
    @Option.Default("Equal")
    CriteriaOperator operator();

    /**
     * Condition entity property.
     *
     * @return the entity property
     */
    Property property();

    /**
     * Condition parameters.
     * Number of required parameters are specified by {@link CriteriaOperator#paramsCount()}.
     * Parameters may be supplied as part of {@link DataQuery} definition or as part of query
     * transformation process (e.g. as query method parameters).
     *
     * @return the condition parameters
     */
    Optional<CriteriaParameters> parameters();

    /**
     * Whether condition is negated.
     *
     * @return value of {@code true} when condition is negated or {@code false} otherwise
     */
    @Option.DefaultBoolean(false)
    boolean not();

    /**
     * Whether {@link String} arguments are case-insensitive.
     * @return value of {@code true} when {@link String} arguments are case-insensitive or {@code false} otherwise
     */
    @Option.DefaultBoolean(false)
    boolean ignoreCase();

}
