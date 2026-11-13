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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import io.helidon.codegen.RoundContext;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

/**
 * Plans source capture and ordered scalar inputs for literal or configured batches.
 */
record JdbcBatchPlan(List<Source> sources, List<Value> values, JdbcSqlParameterPlan parameters,
                     List<String> inputNames, boolean namedOnly) {
    static JdbcBatchPlan create(String sql, TypedElementInfo method, RoundContext context) {
        return create(sql, null, method, context);
    }

    static JdbcBatchPlan configured(JdbcSqlReference reference, TypedElementInfo method, RoundContext context) {
        return create(reference.key(), reference, method, context);
    }

    private static JdbcBatchPlan create(String sql, JdbcSqlReference reference,
                                        TypedElementInfo method, RoundContext context) {
        if (!method.typeName().equals(TypeNames.PRIMITIVE_VOID)
                && !method.typeName().equals(JdbcCodegenTypes.JDBC_BATCH_RESULT)) {
            throw JdbcMethodPlan.failure(method, "A BATCH repository method must return void or JdbcClient.BatchResult. "
                    + "Use JdbcClient.BatchResult to inspect individual execution outcomes and update counts.");
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
                    throw JdbcMethodPlan.failure(method, "A batch parameter must be a supported scalar value or an Iterable, "
                            + "Iterator, or one-dimensional array of supported elements.");
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
            if (element.generic() || element.wildcard()) {
                throw JdbcMethodPlan.failure(method, "Batch source elements cannot use wildcards or unresolved type parameters.");
            }
            if (JdbcScalarTypes.isScalar(element)) {
                values.add(new Value(TypedElementInfo.builder(parameter).typeName(element).build(), sourceIndex, null));
                continue;
            }
            if (element.array()) {
                throw JdbcMethodPlan.failure(method, "A batch source element that is an array must have type byte[].");
            }
            TypeInfo record = context.typeInfo(element).orElse(null);
            if (record == null || record.kind() != ElementKind.RECORD) {
                throw JdbcMethodPlan.failure(method, "A batch source element must be a supported scalar or flat record.");
            }
            if (parameter.hasAnnotation(JdbcCodegenTypes.DATA_PARAM)) {
                throw JdbcMethodPlan.failure(method,
                        "A batch record source cannot declare @Data.Param. Use its record component names.");
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
                throw JdbcMethodPlan.failure(method, "A batch record must declare at least one supported scalar component.");
            }
        }
        if (sources.isEmpty()) {
            throw JdbcMethodPlan.failure(method, "A BATCH method must declare at least one Iterable, Iterator, array, "
                    + "or varargs input source.");
        }
        List<TypedElementInfo> inputs = values.stream().map(Value::parameter).toList();
        List<String> names = List.of();
        boolean namedOnly = false;
        JdbcSqlParameterPlan parameters;
        if (reference == null) {
            parameters = JdbcSqlParameterPlan.create(sql, inputs, method);
        } else {
            JdbcSqlParameterPlan.validateParameters(inputs, method, false);
            namedOnly = inputs.stream().anyMatch(parameter -> parameter.hasAnnotation(JdbcCodegenTypes.DATA_PARAM));
            boolean reliable = values.stream().allMatch(value -> value.component() != null
                    || value.parameter().hasAnnotation(JdbcCodegenTypes.DATA_PARAM)
                    || context.typeHierarchyResolver().parameterNameAvailable(value.parameter()));
            List<String> candidates = inputs.stream().map(JdbcSqlParameterPlan::effectiveName).toList();
            if (reliable && new HashSet<>(candidates).size() == candidates.size()) {
                names = candidates;
            }
            if (namedOnly && names.isEmpty()) {
                throw JdbcMethodPlan.failure(method, "A configured batch method using @Data.Param requires a known, unique "
                        + "name for every input. Use distinct record component names, alias scalar parameters, "
                        + "or compile the parent interface with -parameters.");
            }
            List<JdbcSqlParameterPlan.Bind> binds = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                binds.add(JdbcSqlParameterPlan.binding(i + 1, inputs.get(i)));
            }
            parameters = new JdbcSqlParameterPlan(sql, binds);
            if (reference.defaultSql() != null) {
                JdbcSqlParameterPlan.create(reference.defaultSql(), inputs, method, !names.isEmpty());
            }
        }
        return new JdbcBatchPlan(List.copyOf(sources), List.copyOf(values), parameters, names, namedOnly);
    }

    record Source(TypedElementInfo parameter, TypeName element, boolean array, boolean iterable) {
    }

    record Value(TypedElementInfo parameter, int sourceIndex, String component) {
    }
}
