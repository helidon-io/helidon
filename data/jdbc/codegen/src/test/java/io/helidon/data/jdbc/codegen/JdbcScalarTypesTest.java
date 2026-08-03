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
import java.math.BigInteger;
import java.sql.JDBCType;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcScalarTypesTest {

    private static final Map<Class<?>, JDBCType> REFERENCE_TYPES = Map.ofEntries(
            Map.entry(Boolean.class, JDBCType.BOOLEAN),
            Map.entry(Byte.class, JDBCType.TINYINT),
            Map.entry(Short.class, JDBCType.SMALLINT),
            Map.entry(Integer.class, JDBCType.INTEGER),
            Map.entry(Long.class, JDBCType.BIGINT),
            Map.entry(Float.class, JDBCType.REAL),
            Map.entry(Double.class, JDBCType.DOUBLE),
            Map.entry(BigDecimal.class, JDBCType.DECIMAL),
            Map.entry(String.class, JDBCType.VARCHAR),
            Map.entry(byte[].class, JDBCType.VARBINARY),
            Map.entry(LocalDate.class, JDBCType.DATE),
            Map.entry(LocalTime.class, JDBCType.TIME),
            Map.entry(LocalDateTime.class, JDBCType.TIMESTAMP),
            Map.entry(OffsetTime.class, JDBCType.TIME_WITH_TIMEZONE),
            Map.entry(OffsetDateTime.class, JDBCType.TIMESTAMP_WITH_TIMEZONE),
            Map.entry(java.sql.Date.class, JDBCType.DATE),
            Map.entry(java.sql.Time.class, JDBCType.TIME),
            Map.entry(Timestamp.class, JDBCType.TIMESTAMP));

    @Test
    void assignsTheCanonicalNullTypeToEverySupportedReferenceType() {
        REFERENCE_TYPES.forEach((javaType, jdbcType) -> {
            TypeName typeName = TypeName.create(javaType);
            assertThat(javaType.getTypeName(), JdbcScalarTypes.isScalar(typeName), is(true));
            assertThat(javaType.getTypeName(), JdbcScalarTypes.nullJdbcType(typeName), is(jdbcType));
        });
    }

    @Test
    void normalizesPrimitiveTypesToTheirWrapperMappings() {
        Map.of(boolean.class, Boolean.class,
               byte.class, Byte.class,
               short.class, Short.class,
               int.class, Integer.class,
               long.class, Long.class,
               float.class, Float.class,
               double.class, Double.class)
                .forEach((primitiveType, wrapperType) -> {
                    TypeName primitive = TypeName.create(primitiveType);
                    assertThat(primitiveType.getTypeName(), JdbcScalarTypes.isScalar(primitive), is(true));
                    assertThat(primitiveType.getTypeName(),
                               JdbcScalarTypes.nullJdbcType(primitive),
                               is(REFERENCE_TYPES.get(wrapperType)));
                });
    }

    @Test
    void rejectsTypesOutsideTheScalarContract() {
        TypeName bigInteger = TypeName.create(BigInteger.class);
        assertThat(JdbcScalarTypes.isScalar(bigInteger), is(false));
        assertThat(JdbcScalarTypes.isScalar(TypeName.create(String[].class)), is(false));
        assertThrows(IllegalArgumentException.class, () -> JdbcScalarTypes.nullJdbcType(bigInteger));
    }

    @Test
    void acceptsOnlyOptionalScalarComponents() {
        TypeName optionalString = TypeName.builder(TypeNames.OPTIONAL)
                .addTypeArgument(TypeNames.STRING)
                .build();
        TypeName optionalList = TypeName.builder(TypeNames.OPTIONAL)
                .addTypeArgument(TypeName.create(List.class))
                .build();

        assertThat(JdbcScalarTypes.optionalScalarType(optionalString), is(TypeNames.STRING));
        assertThat(JdbcScalarTypes.optionalScalarType(optionalList), nullValue());
        assertThat(JdbcScalarTypes.optionalScalarType(TypeNames.STRING), nullValue());
    }
}
