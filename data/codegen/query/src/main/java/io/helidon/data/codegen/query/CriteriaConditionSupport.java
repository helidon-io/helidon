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

import java.util.Objects;

import io.helidon.builder.api.Prototype;

// CriteriaExpressionBlueprint custom methods
class CriteriaConditionSupport {

    private CriteriaConditionSupport() {
        throw new UnsupportedOperationException("No instances of CriteriaConditionSupport are allowed");
    }

    /**
     * Create criteria condition with {@link CriteriaOperator#Equal}.
     * Condition parameter is set and will not be required externally.
     *
     * @param property  condition entity property
     * @param parameter condition parameter
     * @return new instance of criteria condition
     */
    @Prototype.FactoryMethod
    static CriteriaCondition createEqual(Property property, CharSequence parameter) {
        Objects.requireNonNull(property, "Value of property is null");
        Objects.requireNonNull(parameter, "Value of parameter is null");
        return CriteriaCondition.builder()
                .operator(CriteriaOperator.Equal)
                .property(property)
                .parameters(new CriteriaParametersSingle(parameter))
                .build();
    }

    /**
     * Create criteria condition with {@link CriteriaOperator#Equal}.
     * Condition parameter is not set and must be provided later in query transformation stage.
     *
     * @param property condition entity property
     * @return new instance of criteria condition
     */
    @Prototype.FactoryMethod
    static CriteriaCondition createEqual(Property property) {
        Objects.requireNonNull(property, "Value of property is null");
        return CriteriaCondition.builder()
                .operator(CriteriaOperator.Equal)
                .property(property)
                .build();
    }

}
