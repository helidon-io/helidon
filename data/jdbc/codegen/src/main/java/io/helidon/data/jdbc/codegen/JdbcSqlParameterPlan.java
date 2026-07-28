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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.TypedElementInfo;

/**
 * Compile-time correspondence between repository parameters and JDBC positions.
 */
record JdbcSqlParameterPlan(String sql, List<Bind> binds) {

    /**
     * Creates an immutable parameter plan.
     *
     * @param sql positional SQL
     * @param binds ordered bindings
     */
    JdbcSqlParameterPlan {
    }

    /**
     * Validates parameter types and resolves named or positional markers.
     *
     * @param sql declared SQL
     * @param parameters repository method parameters
     * @param method enclosing method for diagnostics
     * @return validated parameter plan
     */
    static JdbcSqlParameterPlan create(String sql,
                                       List<TypedElementInfo> parameters,
                                       TypedElementInfo method) {
        validateParameters(parameters, method);
        JdbcSqlMarkerLexer.Result parsed;
        try {
            parsed = JdbcSqlMarkerLexer.parse(sql);
        } catch (IllegalArgumentException e) {
            throw failure(method, e.getMessage());
        }
        List<Bind> binds = switch (parsed.style()) {
            case NONE -> noMarkers(parameters, method);
            case NAMED -> namedBindings(parsed.markers(), parameters, method);
            case POSITIONAL -> positionalBindings(parsed.markers().size(), parameters, method);
        };
        return new JdbcSqlParameterPlan(parsed.sql(), List.copyOf(binds));
    }

    /**
     * Validates the fixed declarative scalar input boundary.
     *
     * @param parameters repository parameters
     * @param method enclosing method
     */
    private static void validateParameters(List<TypedElementInfo> parameters, TypedElementInfo method) {
        Set<String> names = new HashSet<>();
        for (TypedElementInfo parameter : parameters) {
            if (!names.add(parameter.elementName())) {
                throw failure(method, "Duplicate repository parameter name: " + parameter.elementName());
            }
            if (!JdbcMethodPlan.isScalar(parameter.typeName())) {
                throw failure(method, "Unsupported declarative SQL parameter type: "
                        + parameter.typeName().resolvedName());
            }
        }
    }

    /**
     * Validates a statement without markers.
     *
     * @param parameters repository parameters
     * @param method enclosing method
     * @return empty bind list
     */
    private static List<Bind> noMarkers(List<TypedElementInfo> parameters, TypedElementInfo method) {
        if (!parameters.isEmpty()) {
            throw failure(method, "Repository parameters are not used by SQL");
        }
        return List.of();
    }

    /**
     * Resolves named markers in encounter order and permits repeated names.
     *
     * @param markers ordered marker names
     * @param parameters repository parameters
     * @param method enclosing method
     * @return physical bindings
     */
    private static List<Bind> namedBindings(List<String> markers,
                                            List<TypedElementInfo> parameters,
                                            TypedElementInfo method) {
        Map<String, TypedElementInfo> byName = new HashMap<>(parameters.size());
        parameters.forEach(parameter -> byName.put(parameter.elementName(), parameter));
        Set<String> used = new HashSet<>();
        List<Bind> binds = new ArrayList<>(markers.size());
        for (int index = 0; index < markers.size(); index++) {
            String marker = markers.get(index);
            TypedElementInfo parameter = byName.get(marker);
            if (parameter == null) {
                throw failure(method, "SQL marker ':" + marker + "' has no matching repository parameter");
            }
            used.add(marker);
            binds.add(binding(index + 1, parameter));
        }
        for (TypedElementInfo parameter : parameters) {
            if (!used.contains(parameter.elementName())) {
                throw failure(method, "Repository parameter is not used by SQL: " + parameter.elementName());
            }
        }
        return binds;
    }

    /**
     * Maps positional markers to parameters in Java declaration order.
     *
     * @param markerCount number of real JDBC markers
     * @param parameters repository parameters
     * @param method enclosing method
     * @return physical bindings
     */
    private static List<Bind> positionalBindings(int markerCount,
                                                 List<TypedElementInfo> parameters,
                                                 TypedElementInfo method) {
        if (markerCount != parameters.size()) {
            throw failure(method, "Positional SQL marker count " + markerCount
                    + " does not match repository parameter count " + parameters.size());
        }
        List<Bind> binds = new ArrayList<>(markerCount);
        for (int index = 0; index < markerCount; index++) {
            binds.add(binding(index + 1, parameters.get(index)));
        }
        return binds;
    }

    /**
     * Creates one bind and records how a nullable reference is bound.
     *
     * @param position one-based JDBC position
     * @param parameter repository parameter
     * @return binding
     */
    private static Bind binding(int position, TypedElementInfo parameter) {
        boolean nullable = !parameter.typeName().primitive() || parameter.typeName().array();
        return new Bind(position,
                        parameter,
                        nullable,
                        nullable ? JdbcMethodPlan.nullJdbcType(parameter.typeName()) : "");
    }

    /**
     * Creates a code-generation diagnostic.
     *
     * @param method enclosing method
     * @param message diagnostic text
     * @return code-generation exception
     */
    private static CodegenException failure(TypedElementInfo method, String message) {
        return new CodegenException(message, method.originatingElementValue());
    }

    /**
     * One physical JDBC bind.
     *
     * @param position one-based JDBC position
     * @param parameter repository parameter
     * @param nullable whether generated code accepts a null argument
     * @param nullJdbcType JDBCType constant used for null
     */
    record Bind(int position,
                TypedElementInfo parameter,
                boolean nullable,
                String nullJdbcType) {
    }
}
