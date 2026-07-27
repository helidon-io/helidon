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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
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
    /** Standard SQL-null type selected for each supported Java scalar. */
    private static final Map<String, String> NULL_TYPES = Map.ofEntries(
            Map.entry(Boolean.class.getName(), "BOOLEAN"),
            Map.entry(Byte.class.getName(), "TINYINT"),
            Map.entry(Short.class.getName(), "SMALLINT"),
            Map.entry(Integer.class.getName(), "INTEGER"),
            Map.entry(Long.class.getName(), "BIGINT"),
            Map.entry(Float.class.getName(), "REAL"),
            Map.entry(Double.class.getName(), "DOUBLE"),
            Map.entry("java.math.BigDecimal", "DECIMAL"),
            Map.entry(String.class.getName(), "VARCHAR"),
            Map.entry("byte[]", "VARBINARY"),
            Map.entry("java.time.LocalDate", "DATE"),
            Map.entry("java.time.LocalTime", "TIME"),
            Map.entry("java.time.LocalDateTime", "TIMESTAMP"),
            Map.entry("java.time.OffsetTime", "TIME_WITH_TIMEZONE"),
            Map.entry("java.time.OffsetDateTime", "TIMESTAMP_WITH_TIMEZONE"),
            Map.entry("java.sql.Date", "DATE"),
            Map.entry("java.sql.Time", "TIME"),
            Map.entry("java.sql.Timestamp", "TIMESTAMP"));
    private static final Set<String> SCALAR_TYPES = NULL_TYPES.keySet();

    private final TypedElementInfo method;
    private final Operation operation;
    private final ReturnShape returnShape;
    private final MappingKind mappingKind;
    private final TypeName mappedType;
    private final JdbcSqlParameterPlan parameterPlan;
    private final List<String> generatedColumns;
    private final TypeName explicitMapper;
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
     */
    private JdbcMethodPlan(TypedElementInfo method,
                           Operation operation,
                           ReturnShape returnShape,
                           MappingKind mappingKind,
                           TypeName mappedType,
                           JdbcSqlParameterPlan parameterPlan,
                           List<String> generatedColumns,
                           TypeName explicitMapper) {
        this.method = method;
        this.operation = operation;
        this.returnShape = returnShape;
        this.mappingKind = mappingKind;
        this.mappedType = mappedType;
        this.parameterPlan = parameterPlan;
        this.generatedColumns = generatedColumns;
        this.explicitMapper = explicitMapper;
    }

    /**
     * Validates and plans one abstract repository method.
     *
     * @param method repository method
     * @param context code-generation context
     * @return validated plan
     */
    static JdbcMethodPlan create(TypedElementInfo method, CodegenContext context) {
        Annotation statement = method.findAnnotation(JdbcPersistenceTypes.JDBC_STATEMENT)
                .orElseThrow(() -> failure(method, "An abstract JDBC repository method requires @Jdbc.Statement"));
        String sql = statement.stringValue()
                .orElseThrow(() -> failure(method, "@Jdbc.Statement value is missing"));
        if (sql.isBlank()) {
            throw failure(method, "@Jdbc.Statement SQL must not be blank");
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
                throw failure(method, "@Jdbc.GeneratedKeys requires UPDATE execution");
            }
            operation = Operation.GENERATED_KEYS;
        }

        validateReturn(method, operation, result);
        List<String> generatedColumns = generatedColumns(method, generatedKeys);
        MappingKind mappingKind = mappingKind(method,
                                              context,
                                              operation,
                                              result.mappedType(),
                                              rowMapperRequested,
                                              explicitMapper);
        JdbcSqlParameterPlan parameters = JdbcSqlParameterPlan.create(sql, method.parameterArguments(), method);
        return new JdbcMethodPlan(method,
                                  operation,
                                  result.shape(),
                                  mappingKind,
                                  result.mappedType(),
                                  parameters,
                                  generatedColumns,
                                  explicitMapper);
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
        String requested = method.findAnnotation(JdbcPersistenceTypes.JDBC_EXECUTION)
                .flatMap(Annotation::stringValue)
                .orElse("AUTO");
        if ("QUERY".equals(requested)) {
            return Operation.QUERY;
        }
        if ("UPDATE".equals(requested)) {
            return Operation.UPDATE;
        }
        if (!"AUTO".equals(requested)) {
            throw failure(method, "Unsupported @Jdbc.Execution value: " + requested);
        }
        if (generatedKeys) {
            return Operation.UPDATE;
        }
        if (rowMapper || result.shape() == ReturnShape.OPTIONAL || result.shape() == ReturnShape.LIST) {
            return Operation.QUERY;
        }
        TypeName returnType = method.typeName();
        if (returnType.equals(TypeNames.PRIMITIVE_VOID)) {
            return Operation.UPDATE;
        }
        if (returnType.equals(TypeNames.PRIMITIVE_INT) || returnType.equals(TypeNames.PRIMITIVE_LONG)) {
            throw failure(method, "Cannot infer JDBC execution from primitive " + returnType.fqName()
                    + " return type; add @Jdbc.Execution(Jdbc.ExecutionType.QUERY) or "
                    + "@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)");
        }
        return Operation.QUERY;
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
            throw failure(method, "Repository result requires one concrete generic argument: " + type.resolvedName());
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
                throw failure(method, "UPDATE execution must return void, primitive int, or primitive long");
            }
            return;
        }
        if (method.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
            throw failure(method, "QUERY and generated-key methods require a materialized result");
        }
        String raw = result.mappedType().genericTypeName().fqName();
        if ("java.util.Set".equals(raw)
                || "java.util.Map".equals(raw)
                || "java.util.stream.Stream".equals(raw)
                || (result.mappedType().array() && !isScalar(result.mappedType()))) {
            throw failure(method, "Unsupported JDBC repository return type: " + result.mappedType().resolvedName());
        }
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
        for (String column : columns) {
            if (column.isBlank()) {
                throw failure(method, "@Jdbc.GeneratedKeys column names must not be blank");
            }
            if (!unique.add(column.toLowerCase(Locale.ROOT))) {
                throw failure(method, "Duplicate @Jdbc.GeneratedKeys column name: " + column);
            }
        }
        return List.copyOf(columns);
    }

    /**
     * Selects scalar, record, service, or explicit mapping.
     *
     * @param method repository method
     * @param context code-generation context
     * @param operation resolved operation
     * @param mappedType mapped type
     * @param rowMapperRequested whether {@code @Jdbc.RowMapper} is present
     * @param explicitMapper explicit mapper type
     * @return mapping kind
     */
    private static MappingKind mappingKind(TypedElementInfo method,
                                           CodegenContext context,
                                           Operation operation,
                                           TypeName mappedType,
                                           boolean rowMapperRequested,
                                           TypeName explicitMapper) {
        if (operation == Operation.UPDATE) {
            if (rowMapperRequested) {
                throw failure(method, "@Jdbc.RowMapper is not valid on an update-count method");
            }
            return MappingKind.NONE;
        }
        if (rowMapperRequested) {
            if (mappedType.primitive()) {
                throw failure(method, "@Jdbc.RowMapper does not support primitive result type "
                        + mappedType.resolvedName());
            }
            return explicitMapper == null ? MappingKind.SERVICE : MappingKind.EXPLICIT;
        }
        if (isScalar(mappedType)) {
            return MappingKind.SCALAR;
        }
        TypeInfo typeInfo = context.typeInfo(mappedType.genericTypeName())
                .orElseThrow(() -> failure(method, "Mapped result type information is unavailable: "
                        + mappedType.resolvedName()));
        if (typeInfo.kind() == ElementKind.RECORD) {
            return MappingKind.RECORD;
        }
        throw failure(method, "Non-scalar JDBC result must be a record or declare @Jdbc.RowMapper: "
                + mappedType.resolvedName());
    }

    /**
     * Tests whether a type belongs to the provider's fixed scalar table.
     *
     * @param type candidate type
     * @return whether the type is supported
     */
    static boolean isScalar(TypeName type) {
        if (type.array()) {
            return type.componentType().map(TypeName::fqName).filter("byte"::equals).isPresent();
        }
        return SCALAR_TYPES.contains(type.boxed().genericTypeName().fqName());
    }

    /**
     * Returns the standard JDBC null type for a supported scalar.
     *
     * @param type scalar type
     * @return JDBCType constant name
     */
    static String nullJdbcType(TypeName type) {
        String key = type.array() ? "byte[]" : type.boxed().genericTypeName().fqName();
        String jdbcType = NULL_TYPES.get(key);
        if (jdbcType == null) {
            throw new IllegalArgumentException("Unsupported JDBC scalar type: " + type.resolvedName());
        }
        return jdbcType;
    }

    /**
     * Resolves an exact Optional scalar component.
     *
     * @param type candidate record component type
     * @return scalar argument, or {@code null}
     */
    static TypeName optionalScalarType(TypeName type) {
        if (!type.genericTypeName().equals(JdbcPersistenceTypes.OPTIONAL) || type.typeArguments().size() != 1) {
            return null;
        }
        TypeName valueType = type.typeArguments().getFirst();
        return valueType.wildcard() || !isScalar(valueType) ? null : valueType;
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
     * Internal operation selected before source emission.
     */
    enum Operation {
        /** Query rows. */
        QUERY,
        /** Ordinary update count. */
        UPDATE,
        /** Update followed by generated-key rows. */
        GENERATED_KEYS
    }

    /**
     * Supported materialized cardinalities.
     */
    enum ReturnShape {
        /** Exactly one value. */
        ITEM,
        /** Zero or one value. */
        OPTIONAL,
        /** All values. */
        LIST
    }

    /**
     * Supported mapping strategies.
     */
    enum MappingKind {
        /** No row mapping for an update count. */
        NONE,
        /** Built-in scalar column-one mapping. */
        SCALAR,
        /** Generated record construction. */
        RECORD,
        /** Application mapper selected by its exact generic service contract. */
        SERVICE,
        /** Application mapper selected by its concrete service type. */
        EXPLICIT
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
