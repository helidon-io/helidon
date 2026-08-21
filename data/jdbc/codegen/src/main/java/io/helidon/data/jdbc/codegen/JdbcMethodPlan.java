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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

/**
 * Validated compile-time plan for one JDBC repository method.
 * <p>
 * Planning resolves execution, materialized cardinality, mapping, generated
 * keys, and SQL bindings before source emission. No generated method needs to
 * interpret annotations or SQL structure at runtime.
 */
final class JdbcMethodPlan {

    private static final Set<TypeName> UNSUPPORTED_RESULT_TYPES = Set.of(TypeNames.SET,
                                                                        TypeNames.MAP,
                                                                        TypeName.create(Stream.class));

    private final TypedElementInfo method;
    private final Operation operation;
    private final ReturnShape returnShape;
    private final MappingKind mappingKind;
    private final TypeName mappedType;
    private final JdbcSqlParameterPlan parameterPlan;
    private final List<String> generatedColumns;
    private final TypeName explicitMapper;
    private final List<TypedElementInfo> recordComponents;
    private String sqlFieldName;
    private String mapperFieldName;

    /**
     * Creates a fully validated method plan.
     *
     * @param method repository method
     * @param operation resolved operation
     * @param returnShape materialized cardinality
     * @param mappingKind mapping strategy
     * @param mappedType mapped type
     * @param parameterPlan SQL parameter plan
     * @param generatedColumns generated columns
     * @param explicitMapper explicit mapper type
     * @param recordComponents validated canonical record components
     */
    private JdbcMethodPlan(TypedElementInfo method,
                           Operation operation,
                           ReturnShape returnShape,
                           MappingKind mappingKind,
                           TypeName mappedType,
                           JdbcSqlParameterPlan parameterPlan,
                           List<String> generatedColumns,
                           TypeName explicitMapper,
                           List<TypedElementInfo> recordComponents) {
        this.method = method;
        this.operation = operation;
        this.returnShape = returnShape;
        this.mappingKind = mappingKind;
        this.mappedType = mappedType;
        this.parameterPlan = parameterPlan;
        this.generatedColumns = generatedColumns;
        this.explicitMapper = explicitMapper;
        this.recordComponents = List.copyOf(recordComponents);
    }

    /**
     * Validates and plans one abstract repository method.
     *
     * @param method repository method
     * @param roundContext current generation round
     * @return validated plan
     */
    static JdbcMethodPlan create(TypedElementInfo method,
                                 RoundContext roundContext) {
        Annotation statement = method.findAnnotation(JdbcPersistenceTypes.JDBC_STATEMENT)
                .orElseThrow(() -> failure(method,
                                           "An abstract JDBC repository method must declare @Jdbc.Statement."));
        String sql = statement.stringValue()
                .orElseThrow(() -> failure(method, "The @Jdbc.Statement annotation must declare SQL statement."));
        if (sql.isBlank()) {
            throw failure(method, "The SQL statement declared by @Jdbc.Statement must not be blank.");
        }

        Return result = returnPlan(method);
        boolean generatedKeys = method.hasAnnotation(JdbcPersistenceTypes.JDBC_GENERATED_KEYS);
        Annotation rowMapperAnnotation = method.findAnnotation(JdbcPersistenceTypes.JDBC_ROW_MAPPER).orElse(null);
        boolean rowMapperRequested = rowMapperAnnotation != null;
        TypeName explicitMapper = rowMapperAnnotation == null
                ? null
                : rowMapperAnnotation.typeValue()
                        .filter(type -> !TypeNames.BOXED_VOID.equals(type))
                        .orElse(null);
        Operation operation = operation(method, result, generatedKeys, rowMapperRequested);
        if (generatedKeys) {
            if (operation == Operation.QUERY) {
                throw failure(method, "@Jdbc.GeneratedKeys can be used only with UPDATE execution.");
            }
            // Generated keys use UPDATE semantics but need a separate plan for preparation and result mapping.
            operation = Operation.GENERATED_KEYS;
        }

        validateReturn(method, operation, result);
        validateMappedTypeParameters(method, operation, result.mappedType());
        List<String> generatedColumns = generatedColumns(method, generatedKeys);
        Mapping mapping = mapping(method,
                                  roundContext,
                                  operation,
                                  result.mappedType(),
                                  rowMapperRequested,
                                  explicitMapper);
        JdbcSqlParameterPlan parameters = JdbcSqlParameterPlan.create(sql, method.parameterArguments(), method);
        return new JdbcMethodPlan(method,
                                  operation,
                                  result.shape(),
                                  mapping.kind(),
                                  result.mappedType(),
                                  parameters,
                                  generatedColumns,
                                  explicitMapper,
                                  mapping.recordComponents());
    }

    /**
     * Creates a code-generation diagnostic attached to a repository method.
     *
     * @param method repository method
     * @param message diagnostic text
     * @return code-generation exception
     */
    static CodegenException failure(TypedElementInfo method, String message) {
        return new CodegenException(message, method.originatingElementValue());
    }

    TypedElementInfo method() {
        return method;
    }

    Operation operation() {
        return operation;
    }

    ReturnShape returnShape() {
        return returnShape;
    }

    MappingKind mappingKind() {
        return mappingKind;
    }

    TypeName mappedType() {
        return mappedType;
    }

    JdbcSqlParameterPlan parameterPlan() {
        return parameterPlan;
    }

    String jdbcSql() {
        return parameterPlan.sql();
    }

    List<String> generatedColumns() {
        return generatedColumns;
    }

    TypeName explicitMapper() {
        return explicitMapper;
    }

    List<TypedElementInfo> recordComponents() {
        return recordComponents;
    }

    String sqlFieldName() {
        return sqlFieldName;
    }

    /**
     * Records the generated SQL constant name.
     *
     * @param sqlFieldName field name
     */
    void sqlFieldName(String sqlFieldName) {
        this.sqlFieldName = sqlFieldName;
    }

    String mapperFieldName() {
        return mapperFieldName;
    }

    /**
     * Records the generated record mapper constant name.
     *
     * @param mapperFieldName field name
     */
    void mapperFieldName(String mapperFieldName) {
        this.mapperFieldName = mapperFieldName;
    }

    /**
     * Resolves AUTO or an explicit execution choice.
     *
     * @param method repository method
     * @param result return plan
     * @param generatedKeys whether keys were requested
     * @param rowMapper whether an explicit mapper was selected
     * @return internal operation
     */
    private static Operation operation(TypedElementInfo method,
                                       Return result,
                                       boolean generatedKeys,
                                       boolean rowMapper) {
        ExecutionSelection requested = executionSelection(method);
        if (requested == ExecutionSelection.QUERY) {
            return Operation.QUERY;
        }
        if (requested == ExecutionSelection.UPDATE) {
            return Operation.UPDATE;
        }
        if (generatedKeys) {
            return Operation.UPDATE;
        }
        // AUTO inference uses only the Java method shape because the provider must not parse SQL to guess intent.
        if (rowMapper || result.shape() == ReturnShape.OPTIONAL || result.shape() == ReturnShape.LIST) {
            return Operation.QUERY;
        }
        TypeName returnType = method.typeName();
        if (returnType.equals(TypeNames.PRIMITIVE_VOID)) {
            return Operation.UPDATE;
        }
        if (returnType.equals(TypeNames.PRIMITIVE_INT) || returnType.equals(TypeNames.PRIMITIVE_LONG)) {
            throw failure(method, "JDBC execution cannot be inferred from the primitive '" + returnType.fqName()
                    + "' return type. Declare @Jdbc.Execution(Jdbc.ExecutionType.QUERY) or "
                    + "@Jdbc.Execution(Jdbc.ExecutionType.UPDATE).");
        }
        return Operation.QUERY;
    }

    /**
     * Reads the public execution choice into the internal planning model.
     *
     * @param method repository method
     * @return requested execution
     */
    private static ExecutionSelection executionSelection(TypedElementInfo method) {
        String value = method.findAnnotation(JdbcPersistenceTypes.JDBC_EXECUTION)
                .flatMap(Annotation::stringValue)
                .orElse(ExecutionSelection.AUTO.name());
        try {
            return ExecutionSelection.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw failure(method, "@Jdbc.Execution does not support the value '" + value + "'.");
        }
    }

    /**
     * Resolves the materialized return shape and mapped type.
     *
     * @param method repository method
     * @return return plan
     */
    private static Return returnPlan(TypedElementInfo method) {
        TypeName returnType = method.typeName();
        if (returnType.isOptional()) {
            return new Return(ReturnShape.OPTIONAL, singleTypeArgument(method, returnType));
        }
        if (returnType.isList()) {
            return new Return(ReturnShape.LIST, singleTypeArgument(method, returnType));
        }
        return new Return(ReturnShape.ITEM, returnType);
    }

    /**
     * Extracts one concrete generic result argument.
     *
     * @param method repository method
     * @param type generic return type
     * @return concrete mapped type
     */
    private static TypeName singleTypeArgument(TypedElementInfo method, TypeName type) {
        if (type.typeArguments().size() != 1 || type.typeArguments().getFirst().wildcard()) {
            throw failure(method, "Repository result type '" + type.resolvedName()
                    + "' must have one concrete generic argument.");
        }
        return type.typeArguments().getFirst();
    }

    /**
     * Validates operation-specific return rules.
     *
     * @param method repository method
     * @param operation resolved operation
     * @param result return plan
     */
    private static void validateReturn(TypedElementInfo method, Operation operation, Return result) {
        if (operation == Operation.UPDATE) {
            TypeName type = method.typeName();
            if (!type.equals(TypeNames.PRIMITIVE_VOID)
                    && !type.equals(TypeNames.PRIMITIVE_INT)
                    && !type.equals(TypeNames.PRIMITIVE_LONG)) {
                throw failure(method, "UPDATE execution must return void, primitive int, or primitive long.");
            }
            return;
        }
        if (method.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
            throw failure(method, "QUERY and generated keys operations must return a materialized result.");
        }
        TypeName rawType = result.mappedType().genericTypeName();
        if (UNSUPPORTED_RESULT_TYPES.contains(rawType)
                || (result.mappedType().array() && !JdbcScalarTypes.isScalar(result.mappedType()))) {
            throw failure(method, "JDBC repositories do not support the return type '"
                    + result.mappedType().resolvedName() + "'.");
        }
    }

    /**
     * Rejects a mapped result whose type is scoped to the repository method.
     *
     * <p>Generated mapping needs the mapped type in class-scoped mapper state
     * or a class literal, so a method-scoped type cannot satisfy that contract.
     * Type parameters remain valid where no mapping or binding contract needs
     * to resolve them, including generic checked exceptions.</p>
     *
     * @param method repository method
     * @param operation resolved operation
     * @param mappedType mapped result type
     */
    private static void validateMappedTypeParameters(TypedElementInfo method,
                                                     Operation operation,
                                                     TypeName mappedType) {
        if (operation == Operation.UPDATE) {
            return;
        }
        for (TypeName typeParameter : method.typeParameters()) {
            String parameterName = typeParameter.className();
            if (usesTypeParameter(mappedType, parameterName)) {
                throw failure(method, "A JDBC mapped result cannot use the method type parameter '"
                        + parameterName + "'.");
            }
        }
    }

    /**
     * Determines whether a type, including its nested bounds and components,
     * refers to a named method type parameter.
     *
     * @param type type to inspect
     * @param parameterName type-parameter name
     * @return whether the type parameter is used
     */
    private static boolean usesTypeParameter(TypeName type, String parameterName) {
        if (type.generic() && type.className().equals(parameterName)) {
            return true;
        }
        return type.typeArguments().stream().anyMatch(it -> usesTypeParameter(it, parameterName))
                || type.lowerBounds().stream().anyMatch(it -> usesTypeParameter(it, parameterName))
                || type.upperBounds().stream().anyMatch(it -> usesTypeParameter(it, parameterName))
                || type.componentType().map(it -> usesTypeParameter(it, parameterName)).orElse(false);
    }

    /**
     * Reads and validates generated column names.
     *
     * @param method repository method
     * @param generatedKeys whether the annotation is present
     * @return immutable column names
     */
    private static List<String> generatedColumns(TypedElementInfo method, boolean generatedKeys) {
        if (!generatedKeys) {
            return List.of();
        }
        List<String> columns = method.annotation(JdbcPersistenceTypes.JDBC_GENERATED_KEYS)
                .stringValues()
                .orElse(List.of());
        Set<String> unique = new HashSet<>();
        // Column-name resolution belongs to the JDBC driver. Reject only an exact duplicate so quoted,
        // case-distinct database identifiers remain representable, while preserving spelling and declaration order.
        for (String column : columns) {
            if (column.isBlank()) {
                throw failure(method, "@Jdbc.GeneratedKeys column names must not be blank.");
            }
            if (!unique.add(column)) {
                throw failure(method, "The @Jdbc.GeneratedKeys column name '" + column + "' is duplicated.");
            }
        }
        return List.copyOf(columns);
    }

    /**
     * Resolves and validates scalar, record, service, or explicit mapping.
     *
     * @param method repository method
     * @param roundContext current generation round
     * @param operation resolved operation
     * @param mappedType mapped type
     * @param rowMapperRequested whether {@code @Jdbc.RowMapper} is present
     * @param explicitMapper explicit mapper type
     * @return validated mapping metadata
     */
    private static Mapping mapping(TypedElementInfo method,
                                   RoundContext roundContext,
                                   Operation operation,
                                   TypeName mappedType,
                                   boolean rowMapperRequested,
                                   TypeName explicitMapper) {
        if (operation == Operation.UPDATE) {
            if (rowMapperRequested) {
                throw failure(method, "@Jdbc.RowMapper is not valid on an update count method.");
            }
            return Mapping.of(MappingKind.NONE);
        }
        if (rowMapperRequested) {
            if (mappedType.primitive()) {
                throw failure(method, "@Jdbc.RowMapper does not support the primitive result type '"
                        + mappedType.resolvedName() + "'.");
            }
            if (explicitMapper == null) {
                return Mapping.of(MappingKind.SERVICE);
            }
            validateExplicitMapper(method,
                                   roundContext,
                                   mappedType,
                                   explicitMapper);
            return Mapping.of(MappingKind.EXPLICIT);
        }
        if (JdbcScalarTypes.isScalar(mappedType)) {
            return Mapping.of(MappingKind.SCALAR);
        }
        // RoundContext presents records known to javac and records generated earlier in this processing run
        // through the same TypeInfo contract.
        TypeInfo typeInfo = roundContext.typeInfo(mappedType.genericTypeName())
                .orElseThrow(() -> failure(method, "Type information is unavailable for mapped result '"
                        + mappedType.resolvedName() + "'."));
        if (typeInfo.kind() == ElementKind.RECORD) {
            List<TypedElementInfo> components = typeInfo.elementInfo()
                    .stream()
                    .filter(element -> element.kind() == ElementKind.RECORD_COMPONENT)
                    .toList();
            validateRecordComponents(method, components);
            return new Mapping(MappingKind.RECORD, components);
        }
        throw failure(method, "The JDBC result type '" + mappedType.resolvedName()
                + "' must be scalar, be a record, or declare @Jdbc.RowMapper.");
    }

    /**
     * Validates service injection and generic compatibility of an explicitly
     * selected mapper before any source is emitted.
     *
     * @param method repository method
     * @param roundContext current generation round
     * @param mappedType mapper result type
     * @param mapperType explicit mapper type
     */
    private static void validateExplicitMapper(TypedElementInfo method,
                                               RoundContext roundContext,
                                               TypeName mappedType,
                                               TypeName mapperType) {
        TypeInfo mapperInfo = roundContext.typeInfo(mapperType)
                .orElseThrow(() -> failure(method,
                                           "Type information is unavailable for mapper '"
                                                   + mapperType.resolvedName() + "'."));
        if (mapperInfo.kind() != ElementKind.CLASS
                || mapperInfo.elementModifiers().contains(Modifier.ABSTRACT)) {
            throw failure(method, "Mapper '" + mapperType.resolvedName() + "' must be a concrete class.");
        }
        if (!mapperType.enclosingNames().isEmpty() && !mapperInfo.elementModifiers().contains(Modifier.STATIC)) {
            throw failure(method, "Mapper '" + mapperType.resolvedName()
                    + "' must not be a nonstatic nested class.");
        }
        TypeName mapperInterface = roundContext.typeHierarchyResolver()
                .resolveSupertype(mapperType, JdbcPersistenceTypes.ROW_MAPPER)
                .orElse(null);
        // ResolvedType keeps parameterized result types exact, so a mapper for List<Foo> cannot satisfy List<Bar>.
        if (mapperInterface == null
                || mapperInterface.typeArguments().size() != 1
                || !ResolvedType.create(mapperInterface.typeArguments().getFirst())
                        .equals(ResolvedType.create(mappedType))) {
            throw failure(method, "The mapper must implement JdbcClient.RowMapper<"
                    + mappedType.resolvedName() + ">.");
        }
    }

    /**
     * Restricts record components to scalars and explicit Optional scalars.
     *
     * @param method repository method
     * @param components canonical record components
     */
    private static void validateRecordComponents(TypedElementInfo method, List<TypedElementInfo> components) {
        for (TypedElementInfo component : components) {
            if (!JdbcScalarTypes.isScalar(component.typeName())
                    && JdbcScalarTypes.optionalScalarType(component.typeName()).isEmpty()) {
                throw failure(method,
                              "Record component '" + component.elementName() + "' uses the unsupported type '"
                                      + component.typeName().resolvedName() + "'.");
            }
        }
    }

    /**
     * Internal operation selected before source emission.
     */
    enum Operation {
        QUERY,
        UPDATE,
        GENERATED_KEYS
    }

    /**
     * Supported materialized cardinalities.
     */
    enum ReturnShape {
        ITEM,
        OPTIONAL,
        LIST
    }

    /**
     * Supported mapping strategies.
     */
    enum MappingKind {
        NONE,
        SCALAR,
        RECORD,
        SERVICE,
        EXPLICIT
    }

    /**
     * Public execution choices used while resolving an operation.
     */
    private enum ExecutionSelection {
        AUTO,
        QUERY,
        UPDATE
    }

    /**
     * Mapping strategy paired with any metadata needed by source emission.
     *
     * @param kind selected mapping strategy
     * @param recordComponents canonical record components, otherwise empty
     */
    private record Mapping(MappingKind kind, List<TypedElementInfo> recordComponents) {
        private static Mapping of(MappingKind kind) {
            return new Mapping(kind, List.of());
        }
    }

    /**
     * Return cardinality paired with its mapped type.
     *
     * @param shape cardinality
     * @param mappedType mapped type
     */
    private record Return(ReturnShape shape, TypeName mappedType) {
    }
}
