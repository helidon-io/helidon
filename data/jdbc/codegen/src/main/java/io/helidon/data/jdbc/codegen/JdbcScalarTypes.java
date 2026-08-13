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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Map;
import java.util.Optional;

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
    private static final Map<TypeName, String> NULL_TYPE_CONSTANTS = Map.ofEntries(
            Map.entry(TypeName.create(Boolean.class), "BOOLEAN"),
            Map.entry(TypeName.create(Byte.class), "TINYINT"),
            Map.entry(TypeName.create(Short.class), "SMALLINT"),
            Map.entry(TypeName.create(Integer.class), "INTEGER"),
            Map.entry(TypeName.create(Long.class), "BIGINT"),
            Map.entry(TypeName.create(Float.class), "REAL"),
            Map.entry(TypeName.create(Double.class), "DOUBLE"),
            Map.entry(TypeName.create(BigDecimal.class), "DECIMAL"),
            Map.entry(TypeName.create(String.class), "VARCHAR"),
            Map.entry(TypeName.create(byte[].class), "VARBINARY"),
            Map.entry(TypeName.create(LocalDate.class), "DATE"),
            Map.entry(TypeName.create(LocalTime.class), "TIME"),
            Map.entry(TypeName.create(LocalDateTime.class), "TIMESTAMP"),
            Map.entry(TypeName.create(OffsetTime.class), "TIME_WITH_TIMEZONE"),
            Map.entry(TypeName.create(OffsetDateTime.class), "TIMESTAMP_WITH_TIMEZONE"),
            Map.entry(TypeName.create("java.sql.Date"), "DATE"),
            Map.entry(TypeName.create("java.sql.Time"), "TIME"),
            Map.entry(TypeName.create("java.sql.Timestamp"), "TIMESTAMP"));

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
        return NULL_TYPE_CONSTANTS.containsKey(normalized(type));
    }

    /**
     * Returns the standard {@code JDBCType} constant name for a supported scalar.
     *
     * @param type scalar type
     * @return {@code JDBCType} constant name used to bind null
     * @throws IllegalArgumentException if the type is not supported
     */
    static String nullJdbcTypeConstant(TypeName type) {
        String constant = NULL_TYPE_CONSTANTS.get(normalized(type));
        if (constant == null) {
            throw new IllegalArgumentException("JDBC does not support the scalar type '"
                                                       + type.resolvedName() + "'.");
        }
        return constant;
    }

    /**
     * Returns the scalar argument of an exact {@code Optional} type.
     *
     * @param type candidate record component type
     * @return scalar argument, or empty when the type is not supported
     */
    static Optional<TypeName> optionalScalarType(TypeName type) {
        if (!type.genericTypeName().equals(TypeNames.OPTIONAL) || type.typeArguments().size() != 1) {
            return Optional.empty();
        }
        TypeName valueType = type.typeArguments().getFirst();
        return valueType.wildcard() || !isScalar(valueType) ? Optional.empty() : Optional.of(valueType);
    }

    private static TypeName normalized(TypeName type) {
        return type.array() ? type : type.boxed().genericTypeName();
    }
}
