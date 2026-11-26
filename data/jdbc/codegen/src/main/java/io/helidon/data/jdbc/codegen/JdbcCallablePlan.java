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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.codegen.RoundContext;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

record JdbcCallablePlan(List<Output> outputs, List<Output> components, TypedElementInfo control,
                        boolean consumer, boolean snapshot, boolean aggregate,
                        List<String> names, boolean namedOnly) {
    private static final Set<String> SNAPSHOT_TYPES = Set.of("BOOLEAN", "BIT", "TINYINT", "SMALLINT", "INTEGER",
            "BIGINT", "REAL", "FLOAT", "DOUBLE", "NUMERIC", "DECIMAL", "CHAR", "VARCHAR", "LONGVARCHAR",
            "NCHAR", "NVARCHAR", "LONGNVARCHAR", "BINARY", "VARBINARY", "LONGVARBINARY", "DATE", "TIME", "TIMESTAMP");

    private static final Map<String, Set<String>> INPUT_TYPES = Map.ofEntries(
            Map.entry("BIT", Set.of("BOOLEAN")),
            Map.entry("NUMERIC", Set.of("DECIMAL")),
            Map.entry("DECFLOAT", Set.of("DECIMAL")),
            Map.entry("FLOAT", Set.of("DOUBLE")),
            Map.entry("CHAR", Set.of("VARCHAR")),
            Map.entry("LONGVARCHAR", Set.of("VARCHAR")),
            Map.entry("NCHAR", Set.of("VARCHAR")),
            Map.entry("NVARCHAR", Set.of("VARCHAR")),
            Map.entry("LONGNVARCHAR", Set.of("VARCHAR")),
            Map.entry("JSON", Set.of("VARCHAR")),
            Map.entry("BINARY", Set.of("VARBINARY")),
            Map.entry("LONGVARBINARY", Set.of("VARBINARY")));

    static boolean selected(TypedElementInfo method, RoundContext context) {
        return JdbcExecutionSelection.create(method) == JdbcExecutionSelection.CALL
                || !declarations(method).isEmpty()
                || resultComponents(method.typeName(), context).stream()
                        .anyMatch(component -> !declarations(component).isEmpty());
    }

    static List<Annotation> declarations(TypedElementInfo declaration) {
        List<Annotation> result = new ArrayList<>();
        for (Annotation annotation : declaration.annotations()) {
            if (annotation.typeName().equals(JdbcCodegenTypes.JDBC_OUT)) {
                result.add(annotation);
            } else if (annotation.typeName().equals(JdbcCodegenTypes.JDBC_OUTS)) {
                result.addAll(annotation.annotationValues().orElse(List.of()));
            }
        }
        return List.copyOf(result);
    }

    static List<Output> methodOutputs(TypedElementInfo method, RoundContext context) {
        return normalizeOutputs(method, context).outputs();
    }

    static JdbcMethodPlan create(TypedElementInfo method, RoundContext context, String sql, JdbcSqlReference reference) {
        JdbcExecutionSelection execution = JdbcExecutionSelection.create(method);
        if (execution == JdbcExecutionSelection.QUERY || execution == JdbcExecutionSelection.UPDATE
                || method.hasAnnotation(JdbcCodegenTypes.JDBC_GENERATED_KEYS)) {
            throw JdbcMethodPlan.failure(method, "Callable outputs require CALL execution and cannot request generated keys.");
        }
        NormalizedOutputs normalized = normalizeOutputs(method, context);
        List<Output> outputs = normalized.outputs();
        boolean aggregate = !normalized.componentOrder().isEmpty();
        TypedElementInfo control = null;
        boolean consumer = false;
        List<TypedElementInfo> inputs = new ArrayList<>();
        for (TypedElementInfo parameter : method.parameterArguments()) {
            TypeName type = parameter.typeName();
            boolean isConsumer = type.genericTypeName().equals(TypeName.create(Consumer.class));
            boolean isMapper = type.genericTypeName().equals(JdbcCodegenTypes.CALL_MAPPER);
            if (isConsumer || isMapper) {
                if (parameter.hasAnnotation(JdbcCodegenTypes.DATA_PARAM)) {
                    throw JdbcMethodPlan.failure(parameter, "A callable callback cannot declare @Data.Param.");
                }
                if (control != null || type.typeArguments().size() != 1) {
                    throw JdbcMethodPlan.failure(parameter, "A callable method requires at most one fully typed callback.");
                }
                TypeName expected = isConsumer ? JdbcCodegenTypes.CALL_RESULT : method.typeName().boxed();
                if (!ResolvedType.create(type.typeArguments().getFirst()).equals(ResolvedType.create(expected))
                        || (isConsumer && !method.typeName().equals(TypeNames.PRIMITIVE_VOID))
                        || (!isConsumer && method.typeName().equals(TypeNames.PRIMITIVE_VOID))) {
                    throw JdbcMethodPlan.failure(parameter, "The callable callback type must match the method return contract.");
                }
                control = parameter;
                consumer = isConsumer;
            } else {
                if (!JdbcScalarTypes.isScalar(type)) {
                    throw JdbcMethodPlan.failure(parameter, "A callable input must have a supported scalar type.");
                }
                inputs.add(parameter);
            }
        }
        if (control != null && method.hasAnnotation(JdbcCodegenTypes.JDBC_ROW_MAPPER)) {
            throw JdbcMethodPlan.failure(method, "A callable callback cannot accompany a method row mapper.");
        }
        JdbcSqlParameterPlan configured = null;
        if (reference == null) {
            JdbcSqlParameterPlan.validateParameters(inputs, method);
        } else {
            configured = JdbcSqlParameterPlan.configured("", inputs, method, context.typeHierarchyResolver());
        }
        Parameters callParameters = parameters(method, context, reference == null ? sql : reference.defaultSql(),
                                               inputs, outputs, configured);
        List<Output> positioned = callParameters.outputs();
        JdbcSqlParameterPlan parameters = callParameters.plan();
        // Snapshot restrictions belong to the outputs terminal. Callbacks select their own detached result.
        boolean snapshot = control == null && method.typeName().equals(JdbcCodegenTypes.OUT_PARAMETERS);
        JdbcMethodPlan plan = JdbcMethodPlan.callableUnmapped(method, parameters);
        List<Output> mappedOutputs = new ArrayList<>();
        Map<String, Output> mappedByName = aggregate ? new HashMap<>() : Map.of();
        boolean discarded = method.typeName().equals(TypeNames.PRIMITIVE_VOID) || control != null;
        for (Output output : positioned) {
            JdbcMethodPlan mapping = null;
            if (snapshot && (!SNAPSHOT_TYPES.contains(output.type()) || !output.typeName().isEmpty())) {
                throw JdbcMethodPlan.failure(method, "A detached output snapshot requires canonical scalar registrations.");
            }
            if (aggregate || (!discarded && !snapshot && positioned.size() == 1)) {
                if (aggregate) {
                    mapping = outputMapping(method, output, context, output.valueType(), parameters);
                } else {
                    mapping = outputMapping(method, output, context, method.typeName(), parameters);
                    plan = mapping == null ? plan : mapping;
                }
            }
            Output mappedOutput = new Output(output.name(), output.marker(), output.type(), output.typeName(), output.scale(),
                                             output.index(), output.valueType(), mapping);
            mappedOutputs.add(mappedOutput);
            if (aggregate) {
                mappedByName.put(output.name(), mappedOutput);
            }
        }
        if (!discarded && !aggregate && !snapshot) {
            if (outputs.isEmpty()) {
                plan = JdbcMethodPlan.callableMapping(method, context, method.typeName(), parameters);
            } else if (outputs.size() != 1) {
                throw JdbcMethodPlan.failure(method, "Multiple outputs require a snapshot, callback, or output record.");
            }
        }
        if (snapshot && method.hasAnnotation(JdbcCodegenTypes.JDBC_ROW_MAPPER)) {
            throw JdbcMethodPlan.failure(method, "A snapshot cannot select a row mapper.");
        }
        List<Output> componentOutputs = aggregate ? normalized.componentOrder().stream()
                .map(mappedByName::get).toList() : List.of();
        plan.callable(new JdbcCallablePlan(List.copyOf(mappedOutputs), componentOutputs,
                                           control, consumer, snapshot, aggregate,
                                           callParameters.names(), callParameters.namedOnly()));
        plan.reference(reference);
        return plan;
    }

    private static NormalizedOutputs normalizeOutputs(TypedElementInfo method, RoundContext context) {
        List<Annotation> annotations = declarations(method);
        if (annotations.size() > 1
                && annotations.stream().anyMatch(annotation -> annotation.stringValue().orElse("").isEmpty())) {
            throw JdbcMethodPlan.failure(method, "An unnamed callable return must be the only output declaration.");
        }
        TypeName returnType = method.typeName();
        List<TypedElementInfo> recordComponents = resultComponents(returnType, context);
        for (TypedElementInfo component : recordComponents) {
            if (!declarations(component).isEmpty()) {
                throw JdbcMethodPlan.failure(method,
                        "Move output declarations from record components to the repository method and use component names.");
            }
        }
        boolean automatic = !returnType.equals(TypeNames.PRIMITIVE_VOID)
                && !returnType.equals(JdbcCodegenTypes.OUT_PARAMETERS)
                && method.parameterArguments().stream().noneMatch(parameter -> {
                    TypeName type = parameter.typeName().genericTypeName();
                    return type.equals(TypeName.create(Consumer.class)) || type.equals(JdbcCodegenTypes.CALL_MAPPER);
                });
        boolean aggregate = automatic && annotations.size() >= 2 && !recordComponents.isEmpty()
                && !returnType.isOptional() && !returnType.isList()
                && annotations.stream().allMatch(annotation -> !annotation.stringValue().orElse("").isEmpty());
        Map<String, TypeName> componentTypes = aggregate ? new LinkedHashMap<>() : Map.of();
        if (aggregate) {
            Set<String> names = new HashSet<>();
            for (Annotation annotation : annotations) {
                if (!names.add(annotation.stringValue().orElseThrow())) {
                    throw JdbcMethodPlan.failure(method, "Callable output names must be unique.");
                }
            }
            for (TypedElementInfo component : recordComponents) {
                componentTypes.put(component.elementName(), component.typeName());
                if (component.hasAnnotation(JdbcCodegenTypes.JDBC_ROW_MAPPER)) {
                    throw JdbcMethodPlan.failure(method,
                            "An aggregate cannot select component row mappers. Use CallMapper with explicit cursor mappers.");
                }
            }
            if (!names.equals(componentTypes.keySet())) {
                throw JdbcMethodPlan.failure(method,
                        "Method output names must match every record component exactly, including case. "
                                + "Use CallMapper for renamed outputs.");
            }
            if (method.hasAnnotation(JdbcCodegenTypes.JDBC_ROW_MAPPER)) {
                throw JdbcMethodPlan.failure(method,
                        "An aggregate cannot select a method row mapper. Use CallMapper with explicit cursor mappers.");
            }
        }
        List<Output> outputs = new ArrayList<>();
        for (Annotation annotation : annotations) {
            String marker = annotation.stringValue().orElse("");
            TypeName outputType = aggregate ? componentTypes.get(marker) : returnType;
            boolean inferred = automatic && (aggregate || annotations.size() == 1);
            if (!marker.isEmpty() && (!Character.isJavaIdentifierStart(marker.codePointAt(0))
                    || marker.codePoints().anyMatch(character -> !Character.isJavaIdentifierPart(character)))) {
                throw JdbcMethodPlan.failure(method, "A named callable output must follow SQL marker naming rules.");
            }
            String name = marker.isEmpty() ? "return" : marker;
            String typeName = annotation.stringValue("typeName").orElse("");
            String type;
            if (annotation.property("type").map(AnnotationProperty::explicit).orElse(false)) {
                type = annotation.stringValue("type").orElseThrow(() ->
                        JdbcMethodPlan.failure(method, "The callable output requires a JDBC registration type."));
            } else {
                if (!inferred || !typeName.isEmpty()) {
                    throw JdbcMethodPlan.failure(method,
                            "The callable output requires an explicit JDBC type because its registration cannot be inferred.");
                }
                TypeName scalar = JdbcScalarTypes.optionalScalarType(outputType).orElse(outputType);
                if (JdbcScalarTypes.isScalar(scalar)) {
                    type = JdbcScalarTypes.nullJdbcTypeConstant(scalar);
                    if (!SNAPSHOT_TYPES.contains(type)) {
                        throw JdbcMethodPlan.failure(method,
                                "The callable output requires an explicit JDBC type for this scalar type.");
                    }
                    if (method.hasAnnotation(JdbcCodegenTypes.JDBC_ROW_MAPPER)) {
                        throw JdbcMethodPlan.failure(method,
                                "A scalar cursor with a row mapper requires an explicit REF_CURSOR type.");
                    }
                } else {
                    type = "REF_CURSOR";
                }
            }
            int scale = annotation.intValue("scale").orElse(-1);
            if (name.isBlank() || (!typeName.isEmpty() && typeName.isBlank()) || scale < -1
                    || (scale >= 0 && (!typeName.isEmpty() || (!type.equals("NUMERIC") && !type.equals("DECIMAL"))))) {
                throw JdbcMethodPlan.failure(method, "The callable output name, database type name, or scale is invalid.");
            }
            outputs.add(new Output(name, marker, type, typeName, scale, 0, outputType, null));
        }
        return new NormalizedOutputs(List.copyOf(outputs), List.copyOf(componentTypes.keySet()));
    }

    private static Parameters parameters(TypedElementInfo method, RoundContext context, String sql,
                                         List<TypedElementInfo> inputs, List<Output> outputs,
                                         JdbcSqlParameterPlan configured) {
        Map<String, TypedElementInfo> inputNames = new HashMap<>();
        inputs.forEach(input -> inputNames.put(JdbcSqlParameterPlan.effectiveName(input), input));
        Map<String, Output> outputNames = new HashMap<>();
        boolean returns = outputs.stream().anyMatch(output -> output.marker().isEmpty());
        if (returns && inputNames.containsKey("return")) {
            throw JdbcMethodPlan.failure(method, "A leading callable return cannot be an input or INOUT parameter.");
        }
        List<String> names = new ArrayList<>();
        if (returns) {
            names.add("return");
        }
        names.addAll(inputs.stream().map(JdbcSqlParameterPlan::effectiveName).toList());
        for (Output output : outputs) {
            if (outputNames.putIfAbsent(output.name(), output) != null) {
                throw JdbcMethodPlan.failure(method, "Callable output names must be unique.");
            }
            if (!names.contains(output.name())) {
                names.add(output.name());
            }
            TypedElementInfo input = inputNames.get(output.name());
            if (input != null && !INPUT_TYPES.getOrDefault(output.type(), Set.of(output.type()))
                    .contains(JdbcScalarTypes.nullJdbcTypeConstant(input.typeName()))) {
                throw JdbcMethodPlan.failure(input, "The input type is incompatible with its INOUT registration.");
            }
        }
        boolean aliases = inputs.stream().anyMatch(input -> input.hasAnnotation(JdbcCodegenTypes.DATA_PARAM));
        boolean namedOnly = aliases || outputs.stream().anyMatch(output -> !output.marker().isEmpty());
        Parameters configuredParameters = configured == null ? null
                : new Parameters(new JdbcSqlParameterPlan(configured.sql(), configured.binds(), names.size()),
                                 outputs, List.copyOf(names), namedOnly);
        if (sql == null) {
            return configuredParameters;
        }
        JdbcSqlMarkerLexer.Result parsed;
        try {
            parsed = JdbcSqlMarkerLexer.parse(sql, true);
        } catch (IllegalArgumentException failure) {
            throw JdbcMethodPlan.failure(method, failure.getMessage());
        }
        if (configured == null && parsed.style() == JdbcSqlMarkerLexer.MarkerStyle.NAMED) {
            JdbcSqlParameterPlan.validateParameterNames(inputs, method, context.typeHierarchyResolver());
        }
        if (parsed.leadingReturn() != returns) {
            throw JdbcMethodPlan.failure(method, "A leading callable return requires exactly one unnamed method output.");
        }
        if (parsed.style() == JdbcSqlMarkerLexer.MarkerStyle.POSITIONAL && namedOnly) {
            throw JdbcMethodPlan.failure(method, aliases
                    ? "@Data.Param requires named SQL markers and cannot be used with positional SQL."
                    : "Positional callable arguments cannot declare ordinary outputs. Use named SQL markers.");
        }
        List<String> markers = new ArrayList<>(parsed.markers());
        if (parsed.style() == JdbcSqlMarkerLexer.MarkerStyle.POSITIONAL) {
            if (markers.size() != names.size()) {
                throw JdbcMethodPlan.failure(method,
                        "The positional callable marker count must match its inputs and optional leading return.");
            }
            markers = names;
        } else if (parsed.leadingReturn()) {
            markers.set(0, "return");
            if (markers.subList(1, markers.size()).contains("return")) {
                throw JdbcMethodPlan.failure(method, "A leading callable return reserves the logical name return.");
            }
        }
        if (!new HashSet<>(markers).equals(new HashSet<>(names))) {
            throw JdbcMethodPlan.failure(method, "Callable SQL markers must match every input and output declaration.");
        }
        List<Output> positioned = configured == null ? new ArrayList<>() : outputs;
        for (Output output : outputs) {
            int index = markers.indexOf(output.name());
            if (index < 0 || markers.lastIndexOf(output.name()) != index) {
                throw JdbcMethodPlan.failure(method, "An OUT or INOUT marker must occur exactly once.");
            }
            if (configured == null) {
                positioned.add(new Output(output.name(), output.marker(), output.type(), output.typeName(), output.scale(),
                                          index + 1, output.valueType(), null));
            }
        }
        if (configuredParameters != null) {
            // The inline default validates the contract. Deployment SQL supplies the actual positions.
            return configuredParameters;
        }
        List<JdbcSqlParameterPlan.Bind> binds = new ArrayList<>();
        for (int index = 0; index < markers.size(); index++) {
            TypedElementInfo input = inputNames.get(markers.get(index));
            if (input != null) {
                binds.add(JdbcSqlParameterPlan.binding(index + 1, input));
            }
        }
        return new Parameters(new JdbcSqlParameterPlan(parsed.sql(), binds, markers.size()),
                              List.copyOf(positioned), List.copyOf(names), namedOnly);
    }

    private static JdbcMethodPlan outputMapping(TypedElementInfo method, Output output, RoundContext context,
                                                TypeName type, JdbcSqlParameterPlan parameters) {
        if (output.type().equals("REF_CURSOR")) {
            return JdbcMethodPlan.callableMapping(method, context, type, parameters);
        }
        if ((!JdbcScalarTypes.isScalar(type) && JdbcScalarTypes.optionalScalarType(type).isEmpty())
                || method.hasAnnotation(JdbcCodegenTypes.JDBC_ROW_MAPPER)) {
            throw JdbcMethodPlan.failure(method, "A scalar output requires a supported scalar return type. "
                            + "Use CallMapper for scalar wrapper records.");
        }
        return null;
    }

    private static List<TypedElementInfo> resultComponents(TypeName type, RoundContext context) {
        while ((type.isOptional() || type.isList()) && type.typeArguments().size() == 1) {
            type = type.typeArguments().getFirst();
        }
        return components(type, context);
    }

    private static List<TypedElementInfo> components(TypeName type, RoundContext context) {
        TypeInfo info = context.typeInfo(type).orElse(null);
        if (info == null || info.kind() != ElementKind.RECORD) {
            return List.of();
        }
        return info.elementInfo().stream().filter(element -> element.kind() == ElementKind.RECORD_COMPONENT)
                .map(element -> {
                    TypedElementInfo.Builder builder = TypedElementInfo.builder(element)
                            .typeName(context.typeHierarchyResolver().resolveMemberType(info, type, element.typeName()));
                    // A METHOD annotation on a source record component is propagated to its accessor.
                    info.elementInfo().stream()
                            .filter(accessor -> accessor.kind() == ElementKind.METHOD
                                    && accessor.elementName().equals(element.elementName())
                                    && accessor.parameterArguments().isEmpty())
                            .flatMap(accessor -> declarations(accessor).stream()).forEach(builder::addAnnotation);
                    return builder.build();
                })
                .toList();
    }

    private record NormalizedOutputs(List<Output> outputs, List<String> componentOrder) {
    }

    private record Parameters(JdbcSqlParameterPlan plan, List<Output> outputs, List<String> names, boolean namedOnly) {
    }

    record Output(String name, String marker, String type, String typeName, int scale, int index,
                  TypeName valueType, JdbcMethodPlan mapping) {
    }
}
