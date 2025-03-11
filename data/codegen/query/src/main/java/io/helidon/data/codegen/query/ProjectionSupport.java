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

import io.helidon.builder.api.Prototype;

// ProjectionBlueprint custom methods
class ProjectionSupport {

    private ProjectionSupport() {
        throw new UnsupportedOperationException("No instances of ProjectionSupport are allowed");
    }

    /**
     * Create {@code SELECT} projection with no additional arguments.
     * Query will return all and unmodified entity instances.
     *
     * @return new instance of {@link Projection}
     */
    @Prototype.FactoryMethod
    static Projection select() {
        return Projection.builder()
                .action(ProjectionAction.Select)
                .build();
    }

    /**
     * Create {@code SELECT DISTINCT} projection with no additional arguments.
     * Query will return only distinct but unmodified entity instances.
     *
     * @return new instance of {@link Projection}
     */
    @Prototype.FactoryMethod
    static Projection selectDistinct() {
        return Projection.builder()
                .action(ProjectionAction.Select)
                .distinct(true)
                .build();
    }

    /**
     * Create {@code SELECT} projection with returned records count limit.
     * Query will return first {@code count} of unmodified entity instances.
     *
     * @param count returned records count limit
     * @return new instance of {@link Projection}
     */
    @Prototype.FactoryMethod
    static Projection selectFirst(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Value of count must be greater than 0");
        }
        return Projection.builder()
                .action(ProjectionAction.Select)
                .expression(ProjectionExpression.createFirst(count))
                .build();
    }

    /**
     * Create {@code SELECT COUNT} projection.
     * Query will return number of records matching query criteria.
     *
     * @return new instance of {@link Projection}
     */
    @Prototype.FactoryMethod
    static Projection selectCount() {
        return Projection.builder()
                .action(ProjectionAction.Select)
                .expression(ProjectionExpression.createCount())
                .build();
    }

    /**
     * Create {@code SELECT} projection to check whether at least one records matching query criteria exists.
     *
     * @return new instance of {@link Projection}
     */
    @Prototype.FactoryMethod
    static Projection selectExists() {
        return Projection.builder()
                .action(ProjectionAction.Select)
                .expression(ProjectionExpression.createExists())
                .build();
    }

    /**
     * Create {@code DELETE} DML statement.
     *
     * @return new instance of {@link Projection}
     */
    @Prototype.FactoryMethod
    static Projection delete() {
        return Projection.builder()
                .action(ProjectionAction.Delete)
                .build();
    }

    /**
     * Create {@code UPDATE} DML statement.
     *
     * @return new instance of {@link Projection}
     */
    @Prototype.FactoryMethod
    static Projection update() {
        return Projection.builder()
                .action(ProjectionAction.Update)
                .build();
    }

}
