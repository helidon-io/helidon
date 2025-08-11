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
 * Data query projection.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(ProjectionSupport.class)
interface ProjectionBlueprint {

    /**
     * Projection action, e.g. {@code SELECT}, {@code DELETE}, {@code UPDATE}.
     *
     * @return the projection action
     */
    ProjectionAction action();

    /**
     * Projection return type limitation.
     *
     * @return the return type limitation
     */
    Optional<ProjectionResult> result();

    /**
     * Projection expression.
     *
     * @return the projection expression.
     */
    Optional<ProjectionExpression> expression();

    /**
     * Projection property.
     *
     * @return the projection property.
     */
    Optional<Property> property();

    /**
     * Whether projection is distinct.
     *
     * @return value of {@code true} when projection is distinct or {@code false} otherwise
     */
    @Option.DefaultBoolean(false)
    boolean distinct();

}
