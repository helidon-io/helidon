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

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Defines the scalar types supported by JDBC repository code generation.
 */
final class JdbcScalarTypes {

    // Defines the scalar types accepted for implicit result mapping and
    // declarative parameters. JDBC permitted parameter types come from the
    // Appendix B mappings. Generated repositories use the canonical type
    // shown below to bind a null argument. Keep the runtime scalar list
    // aligned with this table.
    //
    // +--------------------+------------------------------------+-------------------------+
    // | Java type          | JDBC permitted parameter types     | Canonical null type     |
    // +--------------------+------------------------------------+-------------------------+
    // | boolean / Boolean  | BIT, BOOLEAN                       | BOOLEAN                 |
    // | byte / Byte        | TINYINT                            | TINYINT                 |
    // | short / Short      | SMALLINT                           | SMALLINT                |
    // | int / Integer      | INTEGER                            | INTEGER                 |
    // | long / Long        | BIGINT                             | BIGINT                  |
    // | float / Float      | REAL                               | REAL                    |
    // | double / Double    | DOUBLE                             | DOUBLE                  |
    // | BigDecimal         | DECIMAL, NUMERIC, DECFLOAT         | DECIMAL                 |
    // | String             | CHAR, VARCHAR, LONGVARCHAR, NCHAR, | VARCHAR                 |
    // |                    | NVARCHAR, LONGNVARCHAR, JSON       |                         |
    // | byte[]             | BINARY, VARBINARY, LONGVARBINARY   | VARBINARY               |
    // | LocalDate          | DATE                               | DATE                    |
    // | java.sql.Date      | DATE                               | DATE                    |
    // | LocalTime          | TIME                               | TIME                    |
    // | java.sql.Time      | TIME                               | TIME                    |
    // | LocalDateTime      | TIMESTAMP                          | TIMESTAMP               |
    // | java.sql.Timestamp | TIMESTAMP                          | TIMESTAMP               |
    // | OffsetTime         | TIME_WITH_TIMEZONE                 | TIME_WITH_TIMEZONE      |
    // | OffsetDateTime     | TIMESTAMP_WITH_TIMEZONE            | TIMESTAMP_WITH_TIMEZONE |
    // +--------------------+------------------------------------+-------------------------+
    private static final Map<TypeName, JDBCType> NULL_TYPES = Map.ofEntries(
            Map.entry(TypeName.create(Boolean.class), JDBCType.BOOLEAN),
            Map.entry(TypeName.create(Byte.class), JDBCType.TINYINT),
            Map.entry(TypeName.create(Short.class), JDBCType.SMALLINT),
            Map.entry(TypeName.create(Integer.class), JDBCType.INTEGER),
            Map.entry(TypeName.create(Long.class), JDBCType.BIGINT),
            Map.entry(TypeName.create(Float.class), JDBCType.REAL),
            Map.entry(TypeName.create(Double.class), JDBCType.DOUBLE),
            Map.entry(TypeName.create(BigDecimal.class), JDBCType.DECIMAL),
            Map.entry(TypeName.create(String.class), JDBCType.VARCHAR),
            Map.entry(TypeName.create(byte[].class), JDBCType.VARBINARY),
            Map.entry(TypeName.create(LocalDate.class), JDBCType.DATE),
            Map.entry(TypeName.create(LocalTime.class), JDBCType.TIME),
            Map.entry(TypeName.create(LocalDateTime.class), JDBCType.TIMESTAMP),
            Map.entry(TypeName.create(OffsetTime.class), JDBCType.TIME_WITH_TIMEZONE),
            Map.entry(TypeName.create(OffsetDateTime.class), JDBCType.TIMESTAMP_WITH_TIMEZONE),
            Map.entry(TypeName.create(java.sql.Date.class), JDBCType.DATE),
            Map.entry(TypeName.create(java.sql.Time.class), JDBCType.TIME),
            Map.entry(TypeName.create(Timestamp.class), JDBCType.TIMESTAMP));

    /**
     * Prevents construction of the constant holder.
     */
    private JdbcScalarTypes() {
    }

    /**
     * Checks whether a type belongs to the provider's fixed scalar table.
     *
     * @param type candidate type
     * @return whether the type is supported
     */
    static boolean isScalar(TypeName type) {
        return NULL_TYPES.containsKey(normalized(type));
    }

    /**
     * Returns the standard JDBC null type for a supported scalar.
     *
     * @param type scalar type
     * @return JDBC type used to bind null
     * @throws IllegalArgumentException if the type is not supported
     */
    static JDBCType nullJdbcType(TypeName type) {
        JDBCType jdbcType = NULL_TYPES.get(normalized(type));
        if (jdbcType == null) {
            throw new IllegalArgumentException("Unsupported JDBC scalar type: " + type.resolvedName());
        }
        return jdbcType;
    }

    /**
     * Returns the scalar argument of an exact {@code Optional} type.
     *
     * @param type candidate record component type
     * @return scalar argument, or {@code null} when the type is not supported
     */
    static TypeName optionalScalarType(TypeName type) {
        if (!type.genericTypeName().equals(TypeNames.OPTIONAL) || type.typeArguments().size() != 1) {
            return null;
        }
        TypeName valueType = type.typeArguments().getFirst();
        return valueType.wildcard() || !isScalar(valueType) ? null : valueType;
    }

    private static TypeName normalized(TypeName type) {
        return type.array() ? type : type.boxed().genericTypeName();
    }
}
