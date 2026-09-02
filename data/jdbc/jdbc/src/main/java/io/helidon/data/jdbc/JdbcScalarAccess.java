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
package io.helidon.data.jdbc;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Provides the fixed portable JDBC access paths for supported scalar values.
 */
final class JdbcScalarAccess {

    // This list must stay aligned with the scalar types accepted by code generation.
    // OffsetTime and OffsetDateTime are deliberately excluded because the supported databases do not provide a
    // lossless, single-column mapping: MySQL discards offsets, PostgreSQL normalizes offset date-times, and Oracle
    // has no standalone SQL time-with-time-zone type. Applications can bind supported local temporal fields plus
    // offset seconds, or use an explicitly encoded String representation.
    private static final Set<Class<?>> SUPPORTED_TYPES = Set.of(Boolean.class,
                                                                 Byte.class,
                                                                 Short.class,
                                                                 Integer.class,
                                                                 Long.class,
                                                                 Float.class,
                                                                 Double.class,
                                                                 BigDecimal.class,
                                                                 String.class,
                                                                 byte[].class,
                                                                 LocalDate.class,
                                                                 LocalTime.class,
                                                                 LocalDateTime.class,
                                                                 Date.class,
                                                                 Time.class,
                                                                 Timestamp.class);

    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = Map.of(boolean.class, Boolean.class,
                                                                             byte.class, Byte.class,
                                                                             short.class, Short.class,
                                                                             int.class, Integer.class,
                                                                             long.class, Long.class,
                                                                             float.class, Float.class,
                                                                             double.class, Double.class);

    /**
     * Prevents construction of the scalar access utility.
     */
    private JdbcScalarAccess() {
    }

    /**
     * Binds one supported non-null scalar through its portable JDBC setter.
     *
     * @param statement prepared statement
     * @param index one-based parameter index
     * @param value supported non-null scalar value
     * @throws SQLException when the JDBC driver rejects the binding
     */
    static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        Objects.requireNonNull(statement, "The prepared statement must not be null.");
        Objects.requireNonNull(value, "The bind value must not be null.");
        if (value instanceof Boolean booleanValue) {
            statement.setBoolean(index, booleanValue);
        } else if (value instanceof Byte byteValue) {
            statement.setByte(index, byteValue);
        } else if (value instanceof Short shortValue) {
            statement.setShort(index, shortValue);
        } else if (value instanceof Integer integerValue) {
            statement.setInt(index, integerValue);
        } else if (value instanceof Long longValue) {
            statement.setLong(index, longValue);
        } else if (value instanceof Float floatValue) {
            statement.setFloat(index, floatValue);
        } else if (value instanceof Double doubleValue) {
            statement.setDouble(index, doubleValue);
        } else if (value instanceof BigDecimal decimalValue) {
            statement.setBigDecimal(index, decimalValue);
        } else if (value instanceof String stringValue) {
            statement.setString(index, stringValue);
        } else if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
        } else if (value instanceof Timestamp timestampValue) {
            statement.setTimestamp(index, timestampValue);
        } else if (value instanceof Time timeValue) {
            statement.setTime(index, timeValue);
        } else if (value instanceof Date dateValue) {
            statement.setDate(index, dateValue);
        } else if (value instanceof LocalDate
                || value instanceof LocalTime
                || value instanceof LocalDateTime) {
            statement.setObject(index, value);
        } else {
            throw new IllegalArgumentException("JDBC does not support bind values of type '"
                                                       + value.getClass().getTypeName() + "'.");
        }
    }

    /**
     * Converts a primitive scalar class token to its wrapper equivalent.
     *
     * @param type requested scalar type
     * @return normalized scalar type
     */
    static Class<?> normalized(Class<?> type) {
        Objects.requireNonNull(type, "The target type must not be null.");
        return type.isPrimitive() ? PRIMITIVE_WRAPPERS.getOrDefault(type, type) : type;
    }

    /**
     * Reads one supported scalar through its portable JDBC getter.
     *
     * @param resultSet current result set
     * @param index one-based column index
     * @param targetType normalized supported scalar type
     * @return database value, possibly {@code null}
     * @throws SQLException when the JDBC driver rejects the conversion
     */
    static Object read(ResultSet resultSet, int index, Class<?> targetType) throws SQLException {
        Objects.requireNonNull(resultSet, "The result set must not be null.");
        Objects.requireNonNull(targetType, "The target type must not be null.");
        if (targetType == Boolean.class) {
            return nullable(resultSet, resultSet.getBoolean(index));
        } else if (targetType == Byte.class) {
            // JDBC 4.3 Appendix B permits getByte for SMALLINT, while pgjdbc
            // org.postgresql.jdbc.PgResultSet#getObject(int, Class) omits Byte because PostgreSQL has no TINYINT.
            // Use the portable getter so a SMALLINT column can satisfy Helidon's Byte scalar contract.
            return nullable(resultSet, resultSet.getByte(index));
        } else if (targetType == Short.class) {
            return nullable(resultSet, resultSet.getShort(index));
        } else if (targetType == Integer.class) {
            return nullable(resultSet, resultSet.getInt(index));
        } else if (targetType == Long.class) {
            return nullable(resultSet, resultSet.getLong(index));
        } else if (targetType == Float.class) {
            return nullable(resultSet, resultSet.getFloat(index));
        } else if (targetType == Double.class) {
            return nullable(resultSet, resultSet.getDouble(index));
        } else if (targetType == BigDecimal.class) {
            return resultSet.getBigDecimal(index);
        } else if (targetType == String.class) {
            return resultSet.getString(index);
        } else if (targetType == byte[].class) {
            return resultSet.getBytes(index);
        } else if (targetType == Date.class) {
            return resultSet.getDate(index);
        } else if (targetType == Time.class) {
            return resultSet.getTime(index);
        } else if (targetType == Timestamp.class) {
            return resultSet.getTimestamp(index);
        } else if (targetType == LocalDate.class
                || targetType == LocalTime.class
                || targetType == LocalDateTime.class) {
            return resultSet.getObject(index, targetType);
        }
        throw new IllegalArgumentException("JDBC does not support the scalar type '"
                                                   + targetType.getTypeName() + "'.");
    }

    /**
     * Tests whether the runtime can bind or read a scalar type directly.
     *
     * @param type candidate type
     * @return whether the scalar is supported
     */
    static boolean supported(Class<?> type) {
        return SUPPORTED_TYPES.contains(normalized(type));
    }

    /**
     * Converts a primitive JDBC getter result to its nullable wrapper form.
     *
     * @param resultSet result set whose last column was read
     * @param value primitive getter result
     * @return {@code null} for SQL NULL, otherwise the boxed getter result
     * @throws SQLException when the driver cannot report the null state
     */
    private static Object nullable(ResultSet resultSet, Object value) throws SQLException {
        return resultSet.wasNull() ? null : value;
    }
}
