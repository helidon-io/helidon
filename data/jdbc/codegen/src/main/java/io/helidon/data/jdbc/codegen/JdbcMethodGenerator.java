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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.Annotation;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
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
     * @param context code-generation context
     */
    static void generate(RepositoryInfo repositoryInfo,
                         ClassModel.Builder classModel,
                         CodegenContext context) {
        List<TypedElementInfo> methods = repositoryInfo.interfaceInfo()
                .elementInfo()
                .stream()
                .filter(element -> element.kind() == ElementKind.METHOD)
                .filter(element -> element.elementModifiers().contains(Modifier.ABSTRACT))
                .toList();
        Map<String, Integer> generatedNames = new HashMap<>();
        List<JdbcMethodPlan> plans = new ArrayList<>(methods.size());
        for (TypedElementInfo method : methods) {
            JdbcMethodPlan plan = JdbcMethodPlan.create(method, context);
            String suffix = uniqueSuffix(method.elementName(), generatedNames);
            plan.sqlFieldName("SQL_" + suffix);
            plan.mapperFieldName("MAPPER_" + suffix);
            plans.add(plan);
        }

        List<MapperDependency> mapperDependencies = mapperDependencies(plans, classModel, context);
        JdbcRepositoryClassGenerator.generateConstructor(classModel, repositoryInfo, mapperDependencies);
        for (JdbcMethodPlan plan : plans) {
            classModel.addField(field -> field.name(plan.sqlFieldName())
                    .description("SQL declared by {@code " + plan.method().elementName() + "}.")
                    .type(TypeNames.STRING)
                    .isStatic(true)
                    .isFinal(true)
                    .addContentLiteral(plan.jdbcSql()));
            if (plan.mappingKind() == JdbcMethodPlan.MappingKind.RECORD) {
                JdbcRecordMapperGenerator.generate(plan, plan.mapperFieldName(), classModel, context);
            }
            classModel.addMethod(method -> generateMethod(plan, method));
        }
    }

    /**
     * Validates service injection and generic compatibility of an explicit mapper.
     *
     * @param plan method plan
     * @param context code-generation context
     */
    private static void validateExplicitMapper(JdbcMethodPlan plan, CodegenContext context) {
        TypeName mapperType = plan.explicitMapper();
        TypeInfo mapperInfo = context.typeInfo(mapperType)
                .orElseThrow(() -> JdbcMethodPlan.failure(plan.method(),
                                                          "Mapper type information is unavailable: "
                                                                  + mapperType.resolvedName()));
        String repositoryPackage = plan.method()
                .enclosingType()
                .map(TypeName::packageName)
                .orElse("");
        if (mapperInfo.kind() != ElementKind.CLASS
                || mapperInfo.elementModifiers().contains(Modifier.ABSTRACT)) {
            throw JdbcMethodPlan.failure(plan.method(), "Mapper must be a concrete class: "
                    + mapperType.resolvedName());
        }
        if (!mapperType.enclosingNames().isEmpty() && !mapperInfo.elementModifiers().contains(Modifier.STATIC)) {
            throw JdbcMethodPlan.failure(plan.method(), "Mapper must not be a non-static nested class: "
                    + mapperType.resolvedName());
        }
        if (!JdbcTypeAccessibility.accessible(context, mapperInfo, repositoryPackage)) {
            throw JdbcMethodPlan.failure(plan.method(), "Mapper is not accessible to generated code: "
                    + mapperType.resolvedName());
        }
        TypeName mapperInterface = findImplementedInterface(mapperInfo,
                                                            JdbcPersistenceTypes.ROW_MAPPER,
                                                            Map.of());
        if (mapperInterface == null
                || mapperInterface.typeArguments().size() != 1
                || !mapperInterface.typeArguments().getFirst().equals(plan.mappedType())) {
            throw JdbcMethodPlan.failure(plan.method(), "Mapper must implement JdbcClient.RowMapper<"
                    + plan.mappedType().resolvedName() + ">");
        }
    }

    /**
     * Creates the statically typed mapper-service dependencies used by generated repository methods.
     *
     * @param plans repository method plans
     * @param classModel generated repository class
     * @param context code-generation context
     * @return mapper dependencies in stable declaration order
     */
    private static List<MapperDependency> mapperDependencies(List<JdbcMethodPlan> plans,
                                                             ClassModel.Builder classModel,
                                                             CodegenContext context) {
        Map<MapperDependencyKey, List<JdbcMethodPlan>> groupedPlans = new LinkedHashMap<>();
        for (JdbcMethodPlan plan : plans) {
            if (plan.mappingKind() == JdbcMethodPlan.MappingKind.EXPLICIT) {
                validateExplicitMapper(plan, context);
                MapperDependencyKey key = new MapperDependencyKey(plan.explicitMapper(), plan.mappedType(), true);
                groupedPlans.computeIfAbsent(key, ignored -> new ArrayList<>()).add(plan);
            } else if (plan.mappingKind() == JdbcMethodPlan.MappingKind.SERVICE) {
                MapperDependencyKey key = new MapperDependencyKey(plan.mappedType(), plan.mappedType(), false);
                groupedPlans.computeIfAbsent(key, ignored -> new ArrayList<>()).add(plan);
            }
        }

        Map<String, Integer> fieldNames = new HashMap<>();
        fieldNames.put("jdbcClient", 1);
        List<MapperDependency> dependencies = new ArrayList<>(groupedPlans.size());
        for (Map.Entry<MapperDependencyKey, List<JdbcMethodPlan>> entry : groupedPlans.entrySet()) {
            MapperDependencyKey key = entry.getKey();
            List<JdbcMethodPlan> mappedPlans = entry.getValue();
            TypeName mapperContract = TypeName.builder(JdbcPersistenceTypes.ROW_MAPPER)
                    .addTypeArgument(key.mappedType())
                    .build();
            String baseName = lowerCamel(key.explicit() ? key.serviceType().className() : key.mappedType().className())
                    + (key.explicit() ? "" : "RowMapper");
            String fieldName = uniqueVariable(baseName, fieldNames);
            classModel.addField(field -> field.name(fieldName)
                    .description("Row mapper selected for generated repository methods.")
                    .type(mapperContract)
                    .isFinal(true));
            mappedPlans.forEach(plan -> plan.mapperFieldName(fieldName));

            TypeName parameterType = key.explicit() ? key.serviceType() : mapperContract;
            dependencies.add(new MapperDependency(parameterType, fieldName, fieldName));
        }
        return dependencies;
    }

    /**
     * Finds a generic interface through the mapper's interface and superclass
     * hierarchy.
     *
     * @param typeInfo candidate type
     * @param contract generic contract
     * @return implemented interface, or {@code null}
     */
    private static TypeName findImplementedInterface(TypeInfo typeInfo,
                                                     TypeName contract,
                                                     Map<String, TypeName> inheritedSubstitutions) {
        Map<String, TypeName> substitutions = substitutions(typeInfo, inheritedSubstitutions);
        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            TypeName resolvedType = substitute(interfaceInfo.typeName(), substitutions);
            if (resolvedType.genericTypeName().equals(contract)) {
                return resolvedType;
            }
            TypeInfo resolvedInfo = TypeInfo.builder(interfaceInfo)
                    .typeName(resolvedType)
                    .build();
            TypeName inherited = findImplementedInterface(resolvedInfo, contract, substitutions);
            if (inherited != null) {
                return inherited;
            }
        }
        return typeInfo.superTypeInfo()
                .map(superType -> {
                    TypeName resolvedType = substitute(superType.typeName(), substitutions);
                    TypeInfo resolvedInfo = TypeInfo.builder(superType)
                            .typeName(resolvedType)
                            .build();
                    return findImplementedInterface(resolvedInfo, contract, substitutions);
                })
                .orElse(null);
    }

    /**
     * Resolves the current declaration's type variables from its actual type
     * arguments.
     *
     * @param typeInfo current hierarchy node
     * @param inherited substitutions inherited from the child declaration
     * @return substitutions visible while traversing this node
     */
    private static Map<String, TypeName> substitutions(TypeInfo typeInfo,
                                                       Map<String, TypeName> inherited) {
        List<TypeName> arguments = typeInfo.typeName().typeArguments();
        if (arguments.isEmpty()) {
            return inherited;
        }
        List<String> parameters = typeInfo.declaredType().typeParameters();
        if (parameters.isEmpty()) {
            parameters = typeInfo.declaredType()
                    .typeArguments()
                    .stream()
                    .filter(TypeName::generic)
                    .map(TypeName::className)
                    .toList();
        }
        Map<String, TypeName> result = new LinkedHashMap<>(inherited);
        for (int index = 0; index < arguments.size() && index < parameters.size(); index++) {
            String parameter = parameters.get(index);
            int boundStart = parameter.indexOf(' ');
            result.put(boundStart < 0 ? parameter : parameter.substring(0, boundStart),
                       substitute(arguments.get(index), inherited));
        }
        return result;
    }

    /**
     * Substitutes generic variables recursively in one type name.
     *
     * @param type source type
     * @param substitutions known generic substitutions
     * @return resolved type
     */
    private static TypeName substitute(TypeName type, Map<String, TypeName> substitutions) {
        TypeName replacement = type.generic()
                && !type.array()
                && type.typeArguments().isEmpty()
                ? substitutions.get(type.className())
                : null;
        if (replacement != null) {
            return replacement;
        }
        TypeName.Builder builder = TypeName.builder(type)
                .typeArguments(type.typeArguments()
                                       .stream()
                                       .map(argument -> substitute(argument, substitutions))
                                       .toList())
                .lowerBounds(type.lowerBounds()
                                     .stream()
                                     .map(bound -> substitute(bound, substitutions))
                                     .toList())
                .upperBounds(type.upperBounds()
                                     .stream()
                                     .map(bound -> substitute(bound, substitutions))
                                     .toList());
        type.componentType()
                .map(component -> substitute(component, substitutions))
                .ifPresent(builder::componentType);
        return builder.build();
    }

    /**
     * Emits one repository method and its transaction annotations.
     *
     * @param plan method plan
     * @param method generated method
     */
    private static void generateMethod(JdbcMethodPlan plan, Method.Builder method) {
        method.name(plan.method().elementName())
                .description("Executes the JDBC statement declared by the repository method.")
                .returnType(plan.method().typeName())
                .addAnnotation(Annotation.create(Override.class));
        plan.method().parameterArguments()
                .forEach(parameter -> method.addParameter(Parameter.builder()
                                                            .name(parameter.elementName())
                                                            .type(parameter.typeName())
                                                            .build()));
        plan.method().throwsChecked().forEach(method::addThrows);
        for (TypeName txAnnotation : JdbcPersistenceTypes.TX_ANNOTATIONS) {
            plan.method().findAnnotation(txAnnotation).ifPresent(method::addAnnotation);
        }

        String statementName = localName(plan, "jdbcStatement");
        method.addContent(JdbcPersistenceTypes.JDBC_CLIENT_STATEMENT)
                .addContent(" ")
                .addContent(statementName)
                .addContent(" = jdbcClient.create(")
                .addContent(plan.sqlFieldName())
                .addContentLine(");");
        for (JdbcSqlParameterPlan.Bind bind : plan.parameterPlan().binds()) {
            addBind(method, statementName, bind);
        }
        addTerminal(plan, method, statementName);
    }

    /**
     * Emits an ordinary bind or a nullable typed-null branch.
     *
     * @param method generated method
     * @param statementName local statement variable
     * @param bind physical bind
     */
    private static void addBind(Method.Builder method,
                                String statementName,
                                JdbcSqlParameterPlan.Bind bind) {
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
        method.addContent("if (")
                .addContent(parameterName)
                .addContentLine(" == null) {")
                .increaseContentPadding()
                .addContent(statementName)
                .addContent(".bindNull(")
                .addContent(String.valueOf(bind.position()))
                .addContent(", ")
                .addContent(JdbcPersistenceTypes.JDBC_TYPE)
                .addContent(".")
                .addContent(bind.nullJdbcType())
                .addContentLine(");")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .increaseContentPadding()
                .addContent(statementName)
                .addContent(".bind(")
                .addContent(String.valueOf(bind.position()))
                .addContent(", ")
                .addContent(parameterName)
                .addContentLine(");")
                .decreaseContentPadding()
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
        default -> throw new AssertionError("Unknown JDBC return shape: " + plan.returnShape());
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
            throw new AssertionError("JDBC mapping does not use a mapper instance: " + plan.mappingKind());
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
