/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.codegen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.helidon.codegen.RoundContext;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

/**
 * Adapts source elements and constants to the existing physical binding plan.
 */
record JdbcBatchPlan(List<Source> sources, List<Value> values, JdbcSqlParameterPlan parameters) {
    static JdbcBatchPlan create(String sql, TypedElementInfo method, RoundContext context) {
        if (!method.typeName().equals(TypeNames.PRIMITIVE_VOID)
                && !method.typeName().equals(TypeName.create("io.helidon.data.jdbc.JdbcClient.BatchResult"))) {
            throw JdbcMethodPlan.failure(method, "BATCH execution must return void or JdbcClient.BatchResult.");
        }
        if (method.hasAnnotation(JdbcCodegenTypes.JDBC_GENERATED_KEYS)
                || method.hasAnnotation(JdbcCodegenTypes.JDBC_ROW_MAPPER)) {
            throw JdbcMethodPlan.failure(method, "BATCH execution cannot request generated keys or row mapping.");
        }
        List<Source> sources = new ArrayList<>();
        List<Value> values = new ArrayList<>();
        for (TypedElementInfo parameter : method.parameterArguments()) {
            TypeName type = parameter.typeName();
            boolean array = type.array() && (!JdbcScalarTypes.isScalar(type) || type.vararg());
            TypeName iterable = array || JdbcScalarTypes.isScalar(type) ? null
                    : context.typeHierarchyResolver().resolveSupertype(type, TypeName.create(Iterable.class)).orElse(null);
            TypeName iterator = array || iterable != null || JdbcScalarTypes.isScalar(type) ? null
                    : context.typeHierarchyResolver().resolveSupertype(type, TypeName.create(Iterator.class))
                            .orElse(null);
            if (!array && iterable == null && iterator == null) {
                if (!JdbcScalarTypes.isScalar(type)) {
                    throw JdbcMethodPlan.failure(method, "A batch input must be a supported scalar or source.");
                }
                values.add(new Value(parameter, -1, null));
                continue;
            }
            TypeName container = iterable == null ? iterator : iterable;
            TypeName element;
            if (array) {
                element = type.componentType().orElseThrow();
                if (element.array()) {
                    throw JdbcMethodPlan.failure(method, "Batch source arrays must have one dimension.");
                }
            } else {
                if (container.typeArguments().size() != 1) {
                    throw JdbcMethodPlan.failure(method, "A batch source must declare one concrete element type.");
                }
                element = container.typeArguments().getFirst();
            }
            int sourceIndex = sources.size();
            sources.add(new Source(parameter, element, array, iterable != null));
            if (JdbcScalarTypes.isScalar(element)) {
                values.add(new Value(TypedElementInfo.builder(parameter).typeName(element).build(), sourceIndex, null));
                continue;
            }
            if (element.generic() || element.wildcard() || element.array()) {
                throw JdbcMethodPlan.failure(method, "A batch source element must be a concrete scalar or flat record.");
            }
            TypeInfo record = context.typeInfo(element).orElse(null);
            if (record == null || record.kind() != ElementKind.RECORD) {
                throw JdbcMethodPlan.failure(method, "A batch source element must be a supported scalar or flat record.");
            }
            int before = values.size();
            for (TypedElementInfo component : record.elementInfo()) {
                if (component.kind() == ElementKind.RECORD_COMPONENT) {
                    TypeName componentType = context.typeHierarchyResolver()
                            .resolveMemberType(record, element, component.typeName());
                    if (!JdbcScalarTypes.isScalar(componentType)) {
                        throw JdbcMethodPlan.failure(method, "Every batch record component must be a supported scalar.");
                    }
                    values.add(new Value(TypedElementInfo.builder(component).typeName(componentType).build(),
                                         sourceIndex, component.elementName()));
                }
            }
            if (values.size() == before) {
                throw JdbcMethodPlan.failure(method, "A batch record must contribute at least one binding.");
            }
        }
        if (sources.isEmpty()) {
            throw JdbcMethodPlan.failure(method, "BATCH execution requires at least one contributing source.");
        }
        JdbcSqlParameterPlan parameters = JdbcSqlParameterPlan.create(sql,
                values.stream().map(Value::parameter).toList(), method);
        return new JdbcBatchPlan(List.copyOf(sources), List.copyOf(values), parameters);
    }

    record Source(TypedElementInfo parameter, TypeName element, boolean array, boolean iterable) {
    }

    record Value(TypedElementInfo parameter, int sourceIndex, String component) {
    }
}
