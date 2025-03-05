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

import java.util.ArrayList;
import java.util.List;

/*
 * This is query by method name specific condition model.
 * Common criteria abstraction would require parentheses structures support and more complex model.
 */

/**
 * Data query criteria.
 * Criteria expression from method name has no nested parentheses structures.
 * It's just flat list with individual operator expressions separated by {@code AND} / {@code OR}.
 */
public class Criteria {

    // First operator expression
    private final CriteriaCondition firstExpression;

    // Next operator expressions with joining logical operator
    private final List<CriteriaConditionNext> nextExpressions;

    private Criteria(CriteriaCondition firstExpression, List<CriteriaConditionNext> nextExpressions) {
        this.firstExpression = firstExpression;
        this.nextExpressions = nextExpressions;
    }

    /**
     * Create new instance of criteria expression with a single condition.
     *
     * @param condition the expression condition
     * @return new instance of criteria expression
     */
    public static Criteria create(CriteriaCondition condition) {
        return Criteria.builder()
                .condition(condition)
                .build();
    }

    /**
     * Create criteria expression builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * First condition of the criteria expression.
     *
     * @return the first condition
     */
    public CriteriaCondition first() {
        return firstExpression;
    }

    /**
     * Next condition of the criteria expression with joining logical operator.
     *
     * @return the next condition
     */
    public List<CriteriaConditionNext> next() {
        return nextExpressions;
    }

    /**
     * Next condition of the criteria expression with joining logical operator.
     * {@code index} starts from {@code 0}.
     *
     * @param index next operator expression index
     * @return criteria operator expression with joining logical operator
     */
    public CriteriaConditionNext next(int index) {
        return nextExpressions.get(index);
    }

    // Criteria builder has specific API to be used with method name parser
    // so blueprint usage won't help much.
    /**
     * {@link Criteria} builder.
     * Conditions and logical operators are added as they are in query statement:
     * <pre>
     *     builder.condition(...)
     *             .and()
     *             .condition(...)
     *             .or()
     *             .condition(...)
     *             .build();
     * </pre>
     */
    public static class Builder implements io.helidon.common.Builder<Builder, Criteria> {

        private final List<CriteriaConditionNext> nextExpressions;
        private CriteriaCondition firstExpression;
        private LogicalOperator logicalOperator;

        private Builder() {
            this.firstExpression = null;
            this.logicalOperator = null;
            this.nextExpressions = new ArrayList<>();
        }

        /**
         * Add condition to the criteria expression.
         *
         * @param condition the expression condition
         * @return this builder
         */
        public Builder condition(CriteriaCondition condition) {
            if (firstExpression == null) {
                if (logicalOperator != null) {
                    throw new IllegalStateException(
                            "Logical operator " + logicalOperator + " was set before first condition");
                }
                firstExpression = condition;
            } else {
                if (logicalOperator == null) {
                    throw new IllegalStateException(
                            "Logical operator was not set before 2nd and later condition");

                }
                nextExpressions.add(CriteriaConditionNext.create(logicalOperator, condition));
                logicalOperator = null;
            }
            return this;
        }

        /**
         * Add logical operator {@code AND} to join next condition.
         *
         * @return this builder
         */
        public Builder and() {
            this.logicalOperator = LogicalOperator.AND;
            return this;
        }

        /**
         * Add logical operator {@code OR} to join next condition.
         *
         * @return this builder
         */
        public Builder or() {
            this.logicalOperator = LogicalOperator.OR;
            return this;
        }

        /**
         * Check whether expression being built is empty (contains no conditions).
         *
         * @return value of {@code true} when expression is empty or {@code false} otherwise
         */
        public boolean isEmpty() {
            return firstExpression == null;
        }

        @Override
        public Criteria build() {
            return new Criteria(firstExpression, nextExpressions);
        }

    }

}
