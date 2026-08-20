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
import java.util.stream.Collectors;

import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.Annotation;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
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
     * @param repositoryInfo repository metadata
     * @param classModel generated implementation
     * @param roundContext current generation round
     */
    static void generate(RepositoryInfo repositoryInfo,
                         ClassModel.Builder classModel,
                         RoundContext roundContext) {
        // Validate and name every method first so constructor dependencies and fields are complete before emission.
        List<TypedElementInfo> methods = JdbcTypeHierarchy.abstractMethods(repositoryInfo.interfaceInfo(), roundContext);
        Map<String, Integer> generatedNames = new HashMap<>();
        List<JdbcMethodPlan> plans = new ArrayList<>(methods.size());
        for (TypedElementInfo method : methods) {
            JdbcMethodPlan plan = JdbcMethodPlan.create(method, roundContext);
            String suffix = uniqueSuffix(method.elementName(), generatedNames);
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
            while (repositoryMethodNames.contains(candidate)) {
                candidate = JdbcCodegenConstants.BIND_PARAMETER_METHOD_NAME + suffix++;
            }
            bindParameterMethodName = candidate;
        } else {
            bindParameterMethodName = null;
        }

        List<MapperDependency> mapperDependencies = mapperDependencies(plans, classModel);
        JdbcRepositoryClassGenerator.generateConstructor(classModel, repositoryInfo, mapperDependencies);
        for (JdbcMethodPlan plan : plans) {
            classModel.addField(field -> field.name(plan.sqlFieldName())
                    .type(TypeNames.STRING)
                    .isStatic(true)
                    .isFinal(true)
                    .addContentLiteral(plan.jdbcSql()));
            if (plan.mappingKind() == JdbcMethodPlan.MappingKind.RECORD) {
                JdbcRecordMapperGenerator.generate(plan, plan.mapperFieldName(), classModel);
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
                MapperDependencyKey key = new MapperDependencyKey(plan.explicitMapper(), plan.mappedType(), true);
                groupedPlans.computeIfAbsent(key, ignored -> new ArrayList<>()).add(plan);
            } else if (plan.mappingKind() == JdbcMethodPlan.MappingKind.SERVICE) {
                MapperDependencyKey key = new MapperDependencyKey(plan.mappedType(), plan.mappedType(), false);
                groupedPlans.computeIfAbsent(key, ignored -> new ArrayList<>()).add(plan);
            }
        }

        Map<String, Integer> fieldNames = new HashMap<>();
        // Reserve the client name so an application type named JdbcClient cannot create a field collision.
        fieldNames.put(JdbcCodegenConstants.JDBC_CLIENT_NAME, 1);
        List<MapperDependency> dependencies = new ArrayList<>(groupedPlans.size());
        for (Map.Entry<MapperDependencyKey, List<JdbcMethodPlan>> entry : groupedPlans.entrySet()) {
            MapperDependencyKey key = entry.getKey();
            List<JdbcMethodPlan> mappedPlans = entry.getValue();
            TypeName mapperContract = TypeName.builder(JdbcPersistenceTypes.ROW_MAPPER)
                    .addTypeArgument(key.mappedType())
                    .build();
            String baseName = lowerCamel(key.explicit() ? key.serviceType().className() : key.mappedType().className())
                    + (key.explicit() ? "" : JdbcCodegenConstants.ROW_MAPPER_SUFFIX);
            String fieldName = uniqueVariable(baseName, fieldNames);
            classModel.addField(field -> field.name(fieldName)
                    .type(mapperContract)
                    .isFinal(true));
            mappedPlans.forEach(plan -> plan.mapperFieldName(fieldName));

            // Inject an explicit mapper by its concrete service type, then retain it through RowMapper<T>.
            TypeName parameterType = key.explicit() ? key.serviceType() : mapperContract;
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
        String statementName = localName(plan, JdbcCodegenConstants.JDBC_STATEMENT_NAME);
        method.addContent(JdbcPersistenceTypes.JDBC_CLIENT_STATEMENT)
                .addContent(" ")
                .addContent(statementName)
                .addContent(" = ")
                .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                .addContent(".create(")
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
            method.addContent(bindParameterMethodName)
                    .addContent("(")
                    .addContent(statementName)
                    .addContent(", ")
                    .addContent(String.valueOf(bind.position()))
                    .addContent(", ")
                    .addContent(parameterName)
                    .addContent(", ")
                    .addContent(JdbcPersistenceTypes.JDBC_TYPE)
                    .addContent(".")
                    .addContent(bind.nullJdbcTypeConstant())
                    .addContentLine(");");
            return;
        }
        method.addContent("if (")
                .addContent(parameterName)
                .addContentLine(" == null) {")
                .addContent(statementName)
                .addContent(".bindNull(")
                .addContent(String.valueOf(bind.position()))
                .addContent(", ")
                .addContent(JdbcPersistenceTypes.JDBC_TYPE)
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
                        .type(JdbcPersistenceTypes.JDBC_CLIENT_STATEMENT))
                .addParameter(parameter -> parameter.name("index")
                        .type(TypeNames.PRIMITIVE_INT))
                .addParameter(parameter -> parameter.name("value")
                        .type(TypeNames.OBJECT))
                .addParameter(parameter -> parameter.name("nullType")
                        .type(JdbcPersistenceTypes.JDBC_TYPE))
                .addContentLine("if (value == null) {")
                .addContentLine("statement.bindNull(index, nullType);")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .addContentLine("statement.bind(index, value);")
                .addContentLine("}");
    }

    /**
     * Emits the update or mapped result terminal.
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
        if (plan.mappingKind() == JdbcMethodPlan.MappingKind.RECORD) {
            method.addContent(plan.mapperFieldName());
        } else if (plan.mappingKind() == JdbcMethodPlan.MappingKind.SERVICE
                || plan.mappingKind() == JdbcMethodPlan.MappingKind.EXPLICIT) {
            method.addContent(plan.mapperFieldName());
        } else {
            throw new AssertionError("The JDBC mapping kind '" + plan.mappingKind()
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
     * Finds a local variable name that does not collide with a method parameter.
     *
     * @param plan method plan
     * @param base preferred name
     * @return unique local name
     */
    private static String localName(JdbcMethodPlan plan, String base) {
        Set<String> parameterNames = plan.method().parameterArguments()
                .stream()
                .map(TypedElementInfo::elementName)
                .collect(Collectors.toSet());
        String candidate = base;
        int suffix = 2;
        while (parameterNames.contains(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    /**
     * Creates a stable constant suffix and disambiguates overloaded methods.
     *
     * @param methodName repository method name
     * @param names previously allocated base names
     * @return unique constant suffix
     */
    private static String uniqueSuffix(String methodName, Map<String, Integer> names) {
        String base = constantCase(methodName);
        int count = names.merge(base, 1, Integer::sum);
        return count == 1 ? base : base + "_" + count;
    }

    /**
     * Creates a unique generated variable name.
     *
     * @param baseName preferred name
     * @param names previously allocated names
     * @return unique name
     */
    private static String uniqueVariable(String baseName, Map<String, Integer> names) {
        int count = names.merge(baseName, 1, Integer::sum);
        return count == 1 ? baseName : baseName + count;
    }

    /**
     * Converts a simple type name to lower camel case.
     *
     * @param value simple type name
     * @return lower-camel name
     */
    private static String lowerCamel(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Converts a Java method name to an upper underscore constant name.
     *
     * @param value method name
     * @return constant name
     */
    private static String constantCase(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (index > 0 && Character.isUpperCase(current) && Character.isLowerCase(value.charAt(index - 1))) {
                result.append('_');
            }
            result.append(Character.toUpperCase(current));
        }
        return result.toString();
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
    private record MapperDependencyKey(TypeName serviceType, TypeName mappedType, boolean explicit) {
    }
}
