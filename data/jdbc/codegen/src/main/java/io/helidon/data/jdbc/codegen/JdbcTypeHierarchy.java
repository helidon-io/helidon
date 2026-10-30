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
import java.util.List;
import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.TypeHierarchyResolver;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Applies JDBC repository policy to methods resolved by common code generation.
 */
final class JdbcTypeHierarchy {

    private static final List<TypeName> EXACT_METHOD_ANNOTATIONS = exactMethodAnnotations();

    private JdbcTypeHierarchy() {
    }

    /**
     * Returns abstract repository methods after Java and JDBC inheritance rules
     * have been validated.
     *
     * @param repository repository interface
     * @param context current code generation round
     * @return resolved abstract methods in declaration order
     */
    static List<TypedElementInfo> abstractMethods(TypeInfo repository, RoundContext context) {
        TypeHierarchyResolver resolver = context.typeHierarchyResolver();
        List<TypeHierarchyResolver.ResolvedMethod> methods = resolver.effectiveInterfaceMethods(repository);
        List<TypedElementInfo> result = new ArrayList<>();
        for (TypeHierarchyResolver.ResolvedMethod resolved : methods) {
            TypedElementInfo method = resolved.method();
            if (!method.elementModifiers().contains(Modifier.ABSTRACT)) {
                validate(method, resolved.declarations(), context);
                continue;
            }
            List<Annotation> parameterAliases = new ArrayList<>();
            for (int index = 0; index < method.parameterArguments().size(); index++) {
                Annotation alias = null;
                // Data.Param belongs to the common API, but only Jdbc.Statement interprets its aliases right now.
                // Data.Query and Jakarta Persistence methods retain their existing parameter binding.
                if (method.hasAnnotation(JdbcCodegenTypes.JDBC_STATEMENT)) {
                    List<Annotation> values = resolver.parameterAnnotationCandidates(repository,
                                                                                     method,
                                                                                     index,
                                                                                     JdbcCodegenTypes.DATA_PARAM);
                    if (values.size() > 1) {
                        throw new CodegenException("Inherited @Data.Param aliases conflict at parameter " + (index + 1)
                                + " of method '" + method.elementName()
                                + "'. Declare an explicit @Data.Param on the child override to resolve the conflict.",
                                                   method.originatingElementValue());
                    }
                    if (!values.isEmpty()) {
                        alias = values.getFirst();
                    }
                }
                parameterAliases.add(alias);
            }
            // Compare inherited binding positions after applying effective aliases. Renamed Java parameters must not
            // make declarations of the same aliased contract appear incompatible.
            List<TypedElementInfo> declarations = resolved.declarations().stream()
                    .map(declaration -> withAliases(declaration, parameterAliases))
                    .toList();
            method = withAliases(method, parameterAliases);
            validate(method, declarations, context);
            result.add(method);
        }
        return List.copyOf(result);
    }

    private static TypedElementInfo withAliases(TypedElementInfo method, List<Annotation> aliases) {
        List<TypedElementInfo> parameters = new ArrayList<>();
        // Overlay only alias metadata. Preserve Java names for generated code and declared types for typed SQL NULL binding.
        for (int index = 0; index < method.parameterArguments().size(); index++) {
            TypedElementInfo parameter = method.parameterArguments().get(index);
            Annotation alias = aliases.get(index);
            if (alias != null) {
                parameter = TypedElementInfo.builder(parameter)
                        .annotations(parameter.annotations().stream()
                                .filter(annotation -> !annotation.typeName().equals(JdbcCodegenTypes.DATA_PARAM))
                                .toList())
                        .addAnnotation(alias)
                        .build();
            }
            parameters.add(parameter);
        }
        return TypedElementInfo.builder(method).parameterArguments(parameters).build();
    }

    private static void validate(TypedElementInfo method, List<TypedElementInfo> declarations, RoundContext context) {
        ElementSignature signature = TypeHierarchy.methodSignature(method);
        TypedElementInfo first = declarations.getFirst();
        for (int index = 1; index < declarations.size(); index++) {
            TypedElementInfo declaration = declarations.get(index);
            // Conflicting inherited annotations would make one generated implementation method represent two
            // contracts.
            if (!sameMethodAnnotations(first, declaration)) {
                throw new CodegenException("Inherited repository method '" + signature.text()
                                                   + "' has conflicting JDBC or transaction annotations.",
                                           first.originatingElementValue(),
                                           declaration.originatingElementValue());
            }
        }
        if (method.elementModifiers().contains(Modifier.ABSTRACT)) {
            validateParameterBindings(signature, declarations, context);
        }
    }

    private static void validateParameterBindings(ElementSignature signature, List<TypedElementInfo> declarations,
                                                  RoundContext context) {
        if (declarations.size() < 2) {
            return;
        }
        Optional<String> value = declarations.getFirst()
                .findAnnotation(JdbcCodegenTypes.JDBC_STATEMENT).flatMap(Annotation::stringValue);
        if (value.isPresent() && JdbcSqlReference.parse(value.get(), declarations.getFirst()) != null) {
            List<ConfiguredParameterBinding> bindings = configuredParameterBindings(declarations.getFirst());
            for (TypedElementInfo declaration : declarations) {
                if (!bindings.equals(configuredParameterBindings(declaration))) {
                    throw new CodegenException("Inherited configured statements have incompatible parameter names or types.",
                                               declaration.originatingElementValue());
                }
            }
            return;
        }
        Optional<String> sql = declarations.getFirst()
                .findAnnotation(JdbcCodegenTypes.JDBC_STATEMENT)
                .flatMap(Annotation::stringValue);
        if (sql.isEmpty() || sql.get().isBlank()) {
            return;
        }

        TypedElementInfo first = declarations.getFirst();
        ParameterBindingContract firstContract = parameterBindingContract(first, sql.get(), context);
        for (int index = 1; index < declarations.size(); index++) {
            TypedElementInfo declaration = declarations.get(index);
            ParameterBindingContract declarationContract = parameterBindingContract(declaration, sql.get(), context);
            // The same named SQL must bind the same logical parameter positions across inherited declarations.
            if (!firstContract.equals(declarationContract)) {
                throw new CodegenException("Inherited repository method '" + signature.text()
                                                   + "' has incompatible named SQL parameter bindings.",
                                           first.originatingElementValue(),
                                           declaration.originatingElementValue());
            }
        }
    }

    private static List<ConfiguredParameterBinding> configuredParameterBindings(TypedElementInfo method) {
        return method.parameterArguments().stream()
                .map(parameter -> new ConfiguredParameterBinding(JdbcSqlParameterPlan.effectiveName(parameter),
                                                                  parameter.typeName()))
                .toList();
    }

    private static ParameterBindingContract parameterBindingContract(TypedElementInfo method, String sql, RoundContext context) {
        List<TypedElementInfo> parameters = method.parameterArguments();
        if (JdbcExecutionSelection.create(method) == JdbcExecutionSelection.BATCH) {
            JdbcBatchPlan batch = JdbcBatchPlan.create(sql, method, context);
            List<ParameterBinding> bindings = batch.parameters().binds().stream().map(bind -> {
                JdbcBatchPlan.Value value = batch.values().stream()
                        .filter(candidate -> candidate.parameter() == bind.parameter()).findFirst().orElseThrow();
                TypedElementInfo parameter = value.sourceIndex() < 0 ? value.parameter()
                        : batch.sources().get(value.sourceIndex()).parameter();
                return new ParameterBinding(parameterIndex(parameters, parameter), bind.nullable(),
                                            bind.nullJdbcTypeConstant(), value.component());
            }).toList();
            return new ParameterBindingContract(bindings);
        }
        JdbcSqlParameterPlan plan = JdbcSqlParameterPlan.create(sql, parameters, method);
        List<ParameterBinding> bindings = plan.binds()
                .stream()
                .map(bind -> new ParameterBinding(parameterIndex(parameters, bind.parameter()),
                                                  bind.nullable(),
                                                  bind.nullJdbcTypeConstant(), null))
                .toList();
        return new ParameterBindingContract(bindings);
    }

    private static int parameterIndex(List<TypedElementInfo> parameters, TypedElementInfo parameter) {
        for (int index = 0; index < parameters.size(); index++) {
            if (parameters.get(index) == parameter) {
                return index;
            }
        }
        throw new IllegalStateException("The JDBC parameter plan contains a repository parameter that is not recognized.");
    }

    private static boolean sameMethodAnnotations(TypedElementInfo first, TypedElementInfo second) {
        for (TypeName annotation : EXACT_METHOD_ANNOTATIONS) {
            if (!first.findAnnotation(annotation).equals(second.findAnnotation(annotation))) {
                return false;
            }
        }
        return JdbcExecutionSelection.create(first) == JdbcExecutionSelection.create(second);
    }

    private static List<TypeName> exactMethodAnnotations() {
        List<TypeName> annotations = new ArrayList<>(3 + JdbcCodegenTypes.TX_ANNOTATIONS.size());
        annotations.add(JdbcCodegenTypes.JDBC_STATEMENT);
        annotations.add(JdbcCodegenTypes.JDBC_GENERATED_KEYS);
        annotations.add(JdbcCodegenTypes.JDBC_ROW_MAPPER);
        annotations.addAll(JdbcCodegenTypes.TX_ANNOTATIONS);
        return List.copyOf(annotations);
    }

    private record ParameterBindingContract(List<ParameterBinding> bindings) {
    }

    private record ParameterBinding(int parameterIndex, boolean nullable, String nullJdbcTypeConstant, String component) {
    }

    private record ConfiguredParameterBinding(String name, TypeName type) {
    }
}
