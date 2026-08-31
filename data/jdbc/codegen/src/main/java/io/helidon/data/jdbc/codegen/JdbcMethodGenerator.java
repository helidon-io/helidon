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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.Annotation;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.data.codegen.common.RepositoryInfo;

/**
 * Emits repository implementations as direct calls to the JDBC client.
 */
final class JdbcMethodGenerator {

    /**
     * Prevents construction of the generator utility.
     */
    private JdbcMethodGenerator() {
    }

    /**
     * Plans every abstract repository method before emitting fields or methods.
     *
     * @param codegenContext current code generation context
     * @param roundContext current generation round
     * @param repositoryInfo repository metadata
     * @param classModel generated implementation
     */
    static void generate(CodegenContext codegenContext,
                         RoundContext roundContext,
                         RepositoryInfo repositoryInfo,
                         ClassModel.Builder classModel) {
        // Validate and name every method first so constructor dependencies and fields are complete before emission.
        List<TypedElementInfo> methods = JdbcTypeHierarchy.abstractMethods(repositoryInfo.interfaceInfo(), roundContext);
        Set<String> generatedNames = new HashSet<>();
        List<JdbcMethodPlan> plans = new ArrayList<>(methods.size());
        for (TypedElementInfo method : methods) {
            JdbcMethodPlan plan = JdbcMethodPlan.create(method, roundContext);
            String baseSuffix = CodegenUtil.toConstantName(
                    codegenContext.uniqueName(repositoryInfo.interfaceInfo(), method));
            String suffix = baseSuffix;
            // Constant conversion is lossy: distinct Java names such as findValue and find_Value normalize to the
            // same suffix. Reserve the final identifier as well as relying on signature-aware overload naming.
            for (int index = 2; !generatedNames.add(suffix); index++) {
                suffix = baseSuffix + "_" + index;
            }
            plan.sqlFieldName(JdbcCodegenConstants.SQL_FIELD_PREFIX + suffix);
            plan.mapperFieldName(JdbcCodegenConstants.MAPPER_FIELD_PREFIX + suffix);
            plans.add(plan);
        }

        int nullableBindCount = 0;
        for (JdbcMethodPlan plan : plans) {
            for (JdbcSqlParameterPlan.Bind bind : plan.parameterPlan().binds()) {
                if (bind.nullable()) {
                    nullableBindCount++;
                }
            }
        }
        final String bindParameterMethodName;
        if (nullableBindCount > 1) {
            Set<String> repositoryMethodNames = new HashSet<>();
            collectMethodNames(repositoryInfo.interfaceInfo(), new HashSet<>(), repositoryMethodNames);
            String candidate = JdbcCodegenConstants.BIND_PARAMETER_METHOD_NAME;
            int suffix = 2;
            // The helper is private but it still participates in Java member lookup, so inherited repository names
            // are reserved before generating a reusable nullable bind method.
            while (repositoryMethodNames.contains(candidate)) {
                candidate = JdbcCodegenConstants.BIND_PARAMETER_METHOD_NAME + suffix++;
            }
            bindParameterMethodName = candidate;
        } else {
            bindParameterMethodName = null;
        }

        List<MapperDependency> mapperDependencies = mapperDependencies(plans, classModel);
        JdbcRepositoryClassGenerator.generateConstructor(classModel, repositoryInfo, mapperDependencies);
        Map<ResolvedType, JdbcMethodPlan> recordMappers = new LinkedHashMap<>();
        for (JdbcMethodPlan plan : plans) {
            classModel.addField(field -> field.name(plan.sqlFieldName())
                    .type(TypeNames.STRING)
                    .isStatic(true)
                    .isFinal(true)
                    .addContentLiteral(plan.jdbcSql()));
            if (plan.mappingKind() == JdbcMethodPlan.MappingKind.RECORD) {
                // Avoid duplicate record mappers by reusing the first mapper planned for each exact mapped type.
                // The shared field keeps the variable name derived from that first repository method. ResolvedType
                // retains nested generic arguments that TypeName equality deliberately omits.
                JdbcMethodPlan firstPlan = recordMappers.putIfAbsent(ResolvedType.create(plan.mappedType()), plan);
                if (firstPlan == null) {
                    JdbcRecordMapperGenerator.generate(plan, plan.mapperFieldName(), classModel);
                } else {
                    plan.mapperFieldName(firstPlan.mapperFieldName());
                }
            }
            classModel.addMethod(method -> generateMethod(plan, method, bindParameterMethodName));
        }
        if (bindParameterMethodName != null) {
            classModel.addMethod(method -> generateBindParameter(method, bindParameterMethodName));
        }
    }

    /**
     * Creates the statically typed mapper-service dependencies used by generated repository methods.
     *
     * @param plans repository method plans
     * @param classModel generated repository class
     * @return mapper dependencies in stable declaration order
     */
    private static List<MapperDependency> mapperDependencies(List<JdbcMethodPlan> plans,
                                                             ClassModel.Builder classModel) {
        // Share one injected dependency between methods with the same mapper selection.
        // LinkedHashMap preserves first-use order for stable generated source.
        Map<MapperDependencyKey, List<JdbcMethodPlan>> groupedPlans = new LinkedHashMap<>();
        for (JdbcMethodPlan plan : plans) {
            if (plan.mappingKind() == JdbcMethodPlan.MappingKind.EXPLICIT) {
                MapperDependencyKey key = new MapperDependencyKey(ResolvedType.create(plan.explicitMapper()),
                                                                  ResolvedType.create(plan.mappedType()),
                                                                  true);
                groupedPlans.computeIfAbsent(key, _ -> new ArrayList<>()).add(plan);
            } else if (plan.mappingKind() == JdbcMethodPlan.MappingKind.SERVICE) {
                ResolvedType mappedType = ResolvedType.create(plan.mappedType());
                MapperDependencyKey key = new MapperDependencyKey(mappedType, mappedType, false);
                groupedPlans.computeIfAbsent(key, _ -> new ArrayList<>()).add(plan);
            }
        }

        Set<String> dependencyNames = new HashSet<>();
        // Mapper fields and their constructor parameters intentionally share one name. Reserve the client
        // parameter name so a mapper can never replace client selection infrastructure.
        dependencyNames.add(JdbcCodegenConstants.JDBC_CLIENT_NAME);
        List<MapperDependency> dependencies = new ArrayList<>(groupedPlans.size());
        for (Map.Entry<MapperDependencyKey, List<JdbcMethodPlan>> entry : groupedPlans.entrySet()) {
            MapperDependencyKey key = entry.getKey();
            List<JdbcMethodPlan> mappedPlans = entry.getValue();
            TypeName mapperContract = TypeName.builder(JdbcCodegenTypes.ROW_MAPPER)
                    .addTypeArgument(key.mappedType().type())
                    .build();
            // An explicit mapper type already describes its role when its simple name ends in Mapper. For service
            // selection, the name is derived from the mapped result type, so append RowMapper to distinguish the
            // dependency from a mapped value. The suffix also prevents a type such as Class from producing a Java
            // keyword after its first character is converted to lowercase.
            String className = key.explicit()
                    ? key.serviceType().type().className()
                    : key.mappedType().type().className();
            String variableName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
            String baseName = key.explicit() && className.endsWith("Mapper")
                    ? variableName
                    : variableName + JdbcCodegenConstants.ROW_MAPPER_SUFFIX;
            // Keep the natural name for the first dependency and add a number only when that name is already used.
            String candidate = baseName;
            for (int index = 2; !dependencyNames.add(candidate); index++) {
                candidate = baseName + index;
            }
            String fieldName = candidate;
            classModel.addField(field -> field.name(fieldName)
                    .type(mapperContract)
                    .isFinal(true));
            mappedPlans.forEach(plan -> plan.mapperFieldName(fieldName));

            // Inject an explicit mapper by its concrete service type, then retain it through RowMapper<T>.
            TypeName parameterType = key.explicit() ? key.serviceType().type() : mapperContract;
            dependencies.add(new MapperDependency(parameterType, fieldName, fieldName));
        }
        return dependencies;
    }

    /**
     * Emits one repository method.
     * Transaction annotations remain on the repository contract. Common service
     * code generation merges those annotations into the generated method metadata used
     * for interception.
     *
     * @param plan method plan
     * @param method generated method
     * @param bindParameterMethodName shared nullable-bind helper, or {@code null} to emit the branch inline
     */
    private static void generateMethod(JdbcMethodPlan plan,
                                       Method.Builder method,
                                       String bindParameterMethodName) {
        method.name(plan.method().elementName())
                .returnType(plan.method().typeName())
                .addAnnotation(Annotation.create(Override.class));
        plan.method().typeParameters()
                .forEach(typeParameter -> method.addGenericArgument(TypeArgument.create(typeParameter)));
        plan.method().parameterArguments()
                .forEach(parameter -> method.addParameter(Parameter.builder()
                                                            .name(parameter.elementName())
                                                            .type(parameter.typeName())
                                                            .build()));
        plan.method().throwsChecked().forEach(method::addThrows);
        Set<String> parameterNames = new HashSet<>();
        for (TypedElementInfo parameter : plan.method().parameterArguments()) {
            parameterNames.add(parameter.elementName());
        }
        String statementName = JdbcCodegenConstants.JDBC_STATEMENT_NAME;
        int statementNameSuffix = 2;
        // Repository parameter names are in method scope, so the generated statement variable must avoid them.
        while (parameterNames.contains(statementName)) {
            statementName = JdbcCodegenConstants.JDBC_STATEMENT_NAME + statementNameSuffix++;
        }
        method.addContent(JdbcCodegenTypes.JDBC_CLIENT_STATEMENT)
                .addContent(" ")
                .addContent(statementName)
                .addContent(" = ")
                .addContent(JdbcCodegenTypes.JDBC_CLIENT)
                .addContent(".createGenerated(")
                .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                .addContent(", ")
                .addContent(plan.sqlFieldName())
                .addContent(", ")
                .addContent(String.valueOf(plan.parameterPlan().parameterCount()))
                .addContentLine(");");
        for (JdbcSqlParameterPlan.Bind bind : plan.parameterPlan().binds()) {
            addBind(method, statementName, bind, bindParameterMethodName);
        }
        addTerminal(plan, method, statementName);
    }

    /**
     * Emits an ordinary bind or a nullable typed-null branch.
     *
     * @param method generated method
     * @param statementName local statement variable
     * @param bind physical bind
     * @param bindParameterMethodName shared nullable-bind helper, or {@code null} to emit the branch inline
     */
    private static void addBind(Method.Builder method,
                                String statementName,
                                JdbcSqlParameterPlan.Bind bind,
                                String bindParameterMethodName) {
        String parameterName = bind.parameter().elementName();
        if (!bind.nullable()) {
            method.addContent(statementName)
                    .addContent(".bind(")
                    .addContent(String.valueOf(bind.position()))
                    .addContent(", ")
                    .addContent(parameterName)
                    .addContentLine(");");
            return;
        }
        if (bindParameterMethodName != null) {
            // Multiple nullable binds share one generated branch to keep repeated named markers from expanding the
            // same typed null decision at every physical JDBC position.
            method.addContent(bindParameterMethodName)
                    .addContent("(")
                    .addContent(statementName)
                    .addContent(", ")
                    .addContent(String.valueOf(bind.position()))
                    .addContent(", ")
                    .addContent(parameterName)
                    .addContent(", ")
                    .addContent(JdbcCodegenTypes.JDBC_TYPE)
                    .addContent(".")
                    .addContent(bind.nullJdbcTypeConstant())
                    .addContentLine(");");
            return;
        }
        method.addContent("if (")
                .addContent(parameterName)
                .addContentLine(" == null) {")
                .addContent(JdbcCodegenTypes.JDBC_CLIENT)
                .addContent(".bindNull(")
                .addContent(statementName)
                .addContent(", ")
                .addContent(String.valueOf(bind.position()))
                .addContent(", ")
                .addContent(JdbcCodegenTypes.JDBC_TYPE)
                .addContent(".")
                .addContent(bind.nullJdbcTypeConstant())
                .addContentLine(");")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .addContent(statementName)
                .addContent(".bind(")
                .addContent(String.valueOf(bind.position()))
                .addContent(", ")
                .addContent(parameterName)
                .addContentLine(");")
                .addContentLine("}");
    }

    /**
     * Emits the shared branch that binds a reference parameter as a scalar
     * value or a typed SQL null.
     *
     * @param method generated helper method
     * @param methodName collision-safe helper name
     */
    private static void generateBindParameter(Method.Builder method, String methodName) {
        method.name(methodName)
                .accessModifier(AccessModifier.PRIVATE)
                .addParameter(parameter -> parameter.name("statement")
                        .type(JdbcCodegenTypes.JDBC_CLIENT_STATEMENT))
                .addParameter(parameter -> parameter.name("index")
                        .type(TypeNames.PRIMITIVE_INT))
                .addParameter(parameter -> parameter.name("value")
                        .type(TypeNames.OBJECT))
                .addParameter(parameter -> parameter.name("nullType")
                        .type(JdbcCodegenTypes.JDBC_TYPE))
                .addContentLine("if (value == null) {")
                .addContent(JdbcCodegenTypes.JDBC_CLIENT)
                .addContentLine(".bindNull(statement, index, nullType);")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .addContentLine("statement.bind(index, value);")
                .addContentLine("}");
    }

    /**
     * Emits the update or mapped result terminal.
     * <p>
     * An optional scalar query delegates the documented no-row and SQL NULL
     * collapse to the client's scalar rows stage. An optional scalar
     * generated-key mapper produces an inner optional for SQL nullability, so
     * the emitted terminal flattens it with the outer row-presence optional to
     * preserve the same declarative result contract.
     *
     * @param plan method plan
     * @param method generated method
     * @param statementName local statement variable
     */
    private static void addTerminal(JdbcMethodPlan plan,
                                    Method.Builder method,
                                    String statementName) {
        // Use only terminals that finish JDBC work and materialize results before the repository method returns.
        if (plan.operation() == JdbcMethodPlan.Operation.UPDATE) {
            if (plan.method().typeName().equals(TypeNames.PRIMITIVE_VOID)) {
                method.addContent(statementName).addContentLine(".execute();");
            } else if (plan.method().typeName().equals(TypeNames.PRIMITIVE_INT)) {
                method.addContent("return ")
                        .addContent(Math.class)
                        .addContent(".toIntExact(")
                        .addContent(statementName)
                        .addContentLine(".execute());");
            } else {
                method.addContent("return ").addContent(statementName).addContentLine(".execute();");
            }
            return;
        }

        method.addContent("return ").addContent(statementName);
        addMapping(plan, method);
        switch (plan.returnShape()) {
        case ITEM -> method.addContentLine(".one();");
        case OPTIONAL -> {
            method.addContent(".optional()");
            if (plan.mappingKind() == JdbcMethodPlan.MappingKind.SCALAR
                    && plan.operation() == JdbcMethodPlan.Operation.GENERATED_KEYS) {
                // The scalar mapper returns Optional<T> so SQL NULL and no row both become Optional.empty().
                method.addContent(".flatMap(value -> value)");
            }
            method.addContentLine(";");
        }
        case LIST -> method.addContentLine(".list();");
        default -> throw new AssertionError("The JDBC return shape '" + plan.returnShape() + "' is not recognized.");
        }
    }

    /**
     * Emits scalar, generated record, or explicit mapper selection.
     *
     * @param plan method plan
     * @param method generated method
     */
    private static void addMapping(JdbcMethodPlan plan, Method.Builder method) {
        if (plan.operation() == JdbcMethodPlan.Operation.GENERATED_KEYS) {
            if (plan.mappingKind() == JdbcMethodPlan.MappingKind.SCALAR) {
                JdbcScalarMapperGenerator.addGeneratedKeyMapping(method,
                                                                 plan.mappedType(),
                                                                 plan.generatedColumns(),
                                                                 plan.returnShape()
                                                                         == JdbcMethodPlan.ReturnShape.OPTIONAL);
            } else {
                method.addContent(".generatedKeys()");
                JdbcScalarMapperGenerator.addGeneratedKeyColumns(method, plan.generatedColumns());
                method.addContent(".map(");
                addMapper(plan, method);
                method.addContent(")");
            }
        } else if (plan.mappingKind() == JdbcMethodPlan.MappingKind.SCALAR) {
            JdbcScalarMapperGenerator.addQueryMapping(method, plan.mappedType());
        } else {
            method.addContent(".map(");
            addMapper(plan, method);
            method.addContent(")");
        }
    }

    /**
     * Emits a generated mapper field or an injected mapper service field.
     *
     * @param plan method plan
     * @param method generated method
     */
    private static void addMapper(JdbcMethodPlan plan, Method.Builder method) {
        switch (plan.mappingKind()) {
        case RECORD, SERVICE, EXPLICIT -> method.addContent(plan.mapperFieldName());
        default -> throw new AssertionError("The JDBC mapping kind '" + plan.mappingKind()
                                                     + "' does not use a mapper instance.");
        }
    }

    /**
     * Collects every declared repository method name so a generated private
     * helper cannot conflict with an inherited instance method.
     *
     * @param typeInfo current repository interface
     * @param visited visited interface declarations
     * @param methodNames collected method names
     */
    private static void collectMethodNames(TypeInfo typeInfo,
                                           Set<String> visited,
                                           Set<String> methodNames) {
        if (!visited.add(typeInfo.typeName().genericTypeName().resolvedName())) {
            return;
        }
        typeInfo.elementInfo()
                .stream()
                .filter(element -> element.kind() == ElementKind.METHOD)
                .map(TypedElementInfo::elementName)
                .forEach(methodNames::add);
        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            collectMethodNames(interfaceInfo, visited, methodNames);
        }
    }

    /**
     * One statically typed mapper injection point and its repository field.
     *
     * @param parameterType injected service type
     * @param parameterName generated constructor parameter name
     * @param fieldName generated mapper field name
     */
    record MapperDependency(TypeName parameterType,
                            String parameterName,
                            String fieldName) {
    }

    /**
     * Groups repository methods that share one exact mapper-service resolution.
     *
     * @param serviceType explicit mapper service type, or mapped type for generic selection
     * @param mappedType mapper result type
     * @param explicit whether the annotation selected a concrete service type
     */
    private record MapperDependencyKey(ResolvedType serviceType, ResolvedType mappedType, boolean explicit) {
    }
}
