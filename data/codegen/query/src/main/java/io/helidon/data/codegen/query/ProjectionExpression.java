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

/**
 * Projection expression.
 */
public class ProjectionExpression {

    private final ProjectionOperator operator;
    private final ProjectionParameter<?> parameter;

    private ProjectionExpression(ProjectionOperator operator, ProjectionParameter<?> parameter) {
        this.operator = operator;
        this.parameter = parameter;
    }

    private ProjectionExpression(ProjectionOperator operator) {
        this(operator, null);
    }

    /**
     * Create projection expression with {@link ProjectionOperator#First} operator.
     *
     * @param count count parameter of the {@code First} operator.
     * @return new instance of projection expression
     */
    public static ProjectionExpression createFirst(int count) {
        return new ProjectionExpression(ProjectionOperator.First, ProjectionParameter.createInteger(count));
    }

    /**
     * Create projection expression with {@link ProjectionOperator#Count} operator.
     *
     * @return new instance of projection expression
     */
    public static ProjectionExpression createCount() {
        return new ProjectionExpression(ProjectionOperator.Count);
    }

    /**
     * Create projection expression with {@link ProjectionOperator#Exists} operator.
     *
     * @return new instance of projection expression
     */
    public static ProjectionExpression createExists() {
        return new ProjectionExpression(ProjectionOperator.Exists);
    }

    /**
     * Create projection expression with {@link ProjectionOperator#Min} operator.
     *
     * @return new instance of projection expression
     */
    public static ProjectionExpression createMin() {
        return new ProjectionExpression(ProjectionOperator.Min);
    }

    /**
     * Create projection expression with {@link ProjectionOperator#Max} operator.
     *
     * @return new instance of projection expression
     */
    public static ProjectionExpression createMax() {
        return new ProjectionExpression(ProjectionOperator.Max);
    }

    /**
     * Create projection expression with {@link ProjectionOperator#Sum} operator.
     *
     * @return new instance of projection expression
     */
    public static ProjectionExpression createSum() {
        return new ProjectionExpression(ProjectionOperator.Sum);
    }

    /**
     * Create projection expression with {@link ProjectionOperator#Avg} operator.
     *
     * @return new instance of projection expression
     */
    public static ProjectionExpression createAvg() {
        return new ProjectionExpression(ProjectionOperator.Avg);
    }

    /**
     * Projection expression operator.
     *
     * @return the operator
     */
    public ProjectionOperator operator() {
        return operator;
    }

    /**
     * Projection expression parameter.
     * Required when {@link ProjectionOperator#hasParameter()} returns true. Currently only
     * {@link ProjectionOperator#First} requires single numeric parameter.
     *
     * @return the parameter
     */
    public Optional<ProjectionParameter<?>> parameter() {
        return Optional.ofNullable(parameter);
    }

}
