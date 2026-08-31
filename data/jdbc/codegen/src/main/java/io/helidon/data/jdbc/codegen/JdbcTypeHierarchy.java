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

    private static final List<TypeName> METHOD_ANNOTATIONS = methodAnnotations();

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
        return context.typeHierarchyResolver()
                .effectiveInterfaceMethods(repository)
                .stream()
                .map(JdbcTypeHierarchy::validate)
                .filter(method -> method.elementModifiers().contains(Modifier.ABSTRACT))
                .toList();
    }

    private static TypedElementInfo validate(TypeHierarchyResolver.ResolvedMethod resolvedMethod) {
        TypedElementInfo method = resolvedMethod.method();
        List<TypedElementInfo> declarations = resolvedMethod.declarations();
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
            validateParameterBindings(signature, declarations);
        }
        return method;
    }

    private static void validateParameterBindings(ElementSignature signature, List<TypedElementInfo> declarations) {
        if (declarations.size() < 2) {
            return;
        }
        Optional<String> sql = declarations.getFirst()
                .findAnnotation(JdbcCodegenTypes.JDBC_STATEMENT)
                .flatMap(Annotation::stringValue);
        if (sql.isEmpty() || sql.get().isBlank()) {
            return;
        }

        TypedElementInfo first = declarations.getFirst();
        ParameterBindingContract firstContract = parameterBindingContract(first, sql.get());
        for (int index = 1; index < declarations.size(); index++) {
            TypedElementInfo declaration = declarations.get(index);
            ParameterBindingContract declarationContract = parameterBindingContract(declaration, sql.get());
            // The same named SQL must bind the same logical parameter positions across inherited declarations.
            if (!firstContract.equals(declarationContract)) {
                throw new CodegenException("Inherited repository method '" + signature.text()
                                                   + "' has incompatible named SQL parameter bindings.",
                                           first.originatingElementValue(),
                                           declaration.originatingElementValue());
            }
        }
    }

    private static ParameterBindingContract parameterBindingContract(TypedElementInfo method, String sql) {
        List<TypedElementInfo> parameters = method.parameterArguments();
        JdbcSqlParameterPlan plan = JdbcSqlParameterPlan.create(sql, parameters, method);
        List<ParameterBinding> bindings = plan.binds()
                .stream()
                .map(bind -> new ParameterBinding(parameterIndex(parameters, bind.parameter()),
                                                  bind.nullable(),
                                                  bind.nullJdbcTypeConstant()))
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
        for (TypeName annotation : METHOD_ANNOTATIONS) {
            if (!first.findAnnotation(annotation).equals(second.findAnnotation(annotation))) {
                return false;
            }
        }
        return true;
    }

    private static List<TypeName> methodAnnotations() {
        List<TypeName> annotations = new ArrayList<>(4 + JdbcCodegenTypes.TX_ANNOTATIONS.size());
        annotations.add(JdbcCodegenTypes.JDBC_STATEMENT);
        annotations.add(JdbcCodegenTypes.JDBC_EXECUTION);
        annotations.add(JdbcCodegenTypes.JDBC_GENERATED_KEYS);
        annotations.add(JdbcCodegenTypes.JDBC_ROW_MAPPER);
        annotations.addAll(JdbcCodegenTypes.TX_ANNOTATIONS);
        return List.copyOf(annotations);
    }

    private record ParameterBindingContract(List<ParameterBinding> bindings) {
    }

    private record ParameterBinding(int parameterIndex, boolean nullable, String nullJdbcTypeConstant) {
    }
}
