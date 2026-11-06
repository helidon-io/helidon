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
import io.helidon.codegen.TypeHierarchyResolver;
import io.helidon.common.types.Annotation;
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
        binds = List.copyOf(binds);
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
        JdbcSqlMarkerLexer.Result parsed;
        try {
            parsed = JdbcSqlMarkerLexer.parse(sql);
        } catch (IllegalArgumentException e) {
            throw failure(method, e.getMessage());
        }
        validateParameters(parameters, method, parsed.style() == JdbcSqlMarkerLexer.MarkerStyle.NAMED);
        if (parsed.style() == JdbcSqlMarkerLexer.MarkerStyle.POSITIONAL
                && parameters.stream().anyMatch(parameter -> parameter.hasAnnotation(JdbcCodegenTypes.DATA_PARAM))) {
            throw failure(method, "@Data.Param requires named SQL markers and cannot be used with positional SQL.");
        }
        List<Bind> binds = switch (parsed.style()) {
            case NONE -> noMarkers(parameters, method);
            case NAMED -> namedBindings(parsed.markers(), parameters, method);
            case POSITIONAL -> positionalBindings(parsed.markers().size(), parameters, method);
        };
        return new JdbcSqlParameterPlan(parsed.sql(), binds);
    }

    /**
     * Resolves logical inputs without reading deployment SQL. Positions remain
     * unknown until the resolved SQL supplies its immutable definition.
     *
     * @param name The reference selects SQL from application configuration.
     * @param parameters The repository inputs.
     * @param method The enclosing method.
     * @param resolver The compiler environment used to validate parameter names.
     * @return The logical binding plan.
     */
    static JdbcSqlParameterPlan configured(String name,
                                            List<TypedElementInfo> parameters,
                                            TypedElementInfo method,
                                            TypeHierarchyResolver resolver) {
        validateParameters(parameters, method);
        for (int index = 0; index < parameters.size(); index++) {
            TypedElementInfo parameter = parameters.get(index);
            if (!parameter.hasAnnotation(JdbcCodegenTypes.DATA_PARAM) && !resolver.parameterNameAvailable(parameter)) {
                throw failure(method, "The name of parameter " + (index + 1) + " of method " + method.elementName()
                        + " is unavailable. Declare @Data.Param, recompile the parent with -parameters, "
                        + "or redeclare the method in this repository with an explicit parameter name.");
            }
        }
        return new JdbcSqlParameterPlan(name, parameters.stream().map(parameter -> binding(0, parameter)).toList());
    }

    /**
     * Returns the alias when present and otherwise uses the compiler exposed Java name.
     *
     * @param parameter The repository parameter supplies the name.
     * @return The effective binding name.
     */
    static String effectiveName(TypedElementInfo parameter) {
        return parameter.findAnnotation(JdbcCodegenTypes.DATA_PARAM)
                .flatMap(Annotation::stringValue)
                .orElse(parameter.elementName());
    }

    /**
     * Returns the number of binding entries. For literal SQL, each entry represents
     * a physical marker position. For configured SQL, each entry represents a logical input.
     *
     * @return The count includes every binding entry.
     */
    int parameterCount() {
        return binds.size();
    }

    /**
     * Validates scalar inputs after any batch sources have been expanded.
     *
     * @param parameters The inputs supply scalar bindings.
     * @param method The method declares the repository operation.
     * @param uniqueNames Whether names must identify distinct logical inputs.
     */
    private static void validateParameters(List<TypedElementInfo> parameters, TypedElementInfo method, boolean uniqueNames) {
        Set<String> names = new HashSet<>();
        for (TypedElementInfo parameter : parameters) {
            String name = effectiveName(parameter);
            if (parameter.hasAnnotation(JdbcCodegenTypes.DATA_PARAM)) {
                String invalidAliasMessage = "The @Data.Param value for parameter '" + parameter.elementName()
                        + "' must not be empty and must follow Java identifier character rules. "
                        + "Do not include the ':' prefix or whitespace.";
                JdbcSqlMarkerLexer.Result alias;
                try {
                    // Reuse the SQL scanner so alias validation accepts exactly the same Unicode marker names as SQL.
                    alias = JdbcSqlMarkerLexer.parse(":" + name);
                } catch (IllegalArgumentException _) {
                    throw failure(method, invalidAliasMessage);
                }
                // Parsing a valid prefix is not enough. The entire value must become exactly one unchanged marker name.
                if (name.isBlank() || !alias.sql().equals("?") || !alias.markers().equals(List.of(name))) {
                    throw failure(method, invalidAliasMessage);
                }
            }
            // An alias must also remain distinct from Java names used by parameters without an alias.
            if (uniqueNames && !names.add(name)) {
                throw failure(method, "The effective repository parameter name '" + name + "' is duplicated.");
            }
            if (!JdbcScalarTypes.isScalar(parameter.typeName())) {
                throw failure(method, "Declarative SQL does not support the parameter type '"
                        + parameter.typeName().resolvedName() + "'.");
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
            throw failure(method, "The repository parameters are not used by the SQL statement.");
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
        // Index by binding name without renaming the parameter metadata used by generated Java code.
        parameters.forEach(parameter -> byName.put(effectiveName(parameter), parameter));
        Set<String> used = new HashSet<>();
        List<Bind> binds = new ArrayList<>(markers.size());
        // Named SQL binds in marker encounter order because repeated names must produce repeated JDBC positions.
        for (int index = 0; index < markers.size(); index++) {
            String marker = markers.get(index);
            TypedElementInfo parameter = byName.get(marker);
            if (parameter == null) {
                throw failure(method, "SQL marker ':" + marker + "' has no matching repository parameter. "
                        + "Declare @Data.Param, use positional SQL, or preserve Java parameter names with -parameters.");
            }
            used.add(marker);
            binds.add(binding(index + 1, parameter));
        }
        for (TypedElementInfo parameter : parameters) {
            if (!used.contains(effectiveName(parameter))) {
                throw failure(method, "Repository parameter '" + effectiveName(parameter)
                                              + "' is not used by the SQL statement.");
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
            throw failure(method, "The SQL statement contains " + markerCount + " positional "
                    + countNoun(markerCount, "marker") + ", but the repository method declares " + parameters.size()
                    + " " + countNoun(parameters.size(), "parameter") + ".");
        }
        List<Bind> binds = new ArrayList<>(markerCount);
        for (int index = 0; index < markerCount; index++) {
            binds.add(binding(index + 1, parameters.get(index)));
        }
        return binds;
    }

    /**
     * Returns a singular or plural count noun.
     *
     * @param count item count
     * @param noun singular noun
     * @return noun matching the count
     */
    private static String countNoun(int count, String noun) {
        return count == 1 ? noun : noun + "s";
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
                        JdbcScalarTypes.nullJdbcTypeConstant(parameter.typeName()));
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
     * @param nullJdbcTypeConstant name of the {@code JDBCType} constant used for null
     */
    record Bind(int position,
                TypedElementInfo parameter,
                boolean nullable,
                String nullJdbcTypeConstant) {
    }
}
