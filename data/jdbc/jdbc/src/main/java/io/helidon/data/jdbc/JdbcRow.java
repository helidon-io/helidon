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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.data.DataException;

/**
 * Implements the row view available during a mapper callback.
 * <p>
 * The row belongs to the thread that created it. Calls from another thread
 * fail before accessing the result set. Expiration changes the visible state
 * without waiting for application work, so a retained row cannot delay
 * provider cleanup.
 */
final class JdbcRow implements JdbcClient.Row {

    // This list must stay aligned with the scalar types accepted by code generation.
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
                                                                 OffsetTime.class,
                                                                 OffsetDateTime.class,
                                                                 Date.class,
                                                                 Time.class,
                                                                 Timestamp.class);

    // ResultSet.getObject accepts reference types rather than primitive class tokens.
    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = Map.of(boolean.class, Boolean.class,
                                                                             byte.class, Byte.class,
                                                                             short.class, Short.class,
                                                                             int.class, Integer.class,
                                                                             long.class, Long.class,
                                                                             float.class, Float.class,
                                                                             double.class, Double.class);

    private final ResultSet resultSet;
    private final JdbcColumnLayout columns;
    private final JdbcOperation operation;
    private final Thread callbackThread;

    // Volatile makes expiration visible to code that retained the row after the callback.
    private volatile boolean active = true;

    /**
     * Creates a view of one result row for a mapper callback.
     *
     * @param resultSet result set owned by the provider
     * @param columns validated column layout
     * @param operation current operation
     */
    JdbcRow(ResultSet resultSet, JdbcColumnLayout columns, JdbcOperation operation) {
        this.resultSet = resultSet;
        this.columns = columns;
        this.operation = operation;
        this.callbackThread = Thread.currentThread();
    }

    /**
     * Tests whether the runtime can bind or read a scalar type directly.
     *
     * @param type candidate type
     * @return whether the scalar is supported
     */
    static boolean supportedScalar(Class<?> type) {
        return SUPPORTED_TYPES.contains(normalized(type));
    }

    /**
     * Marks this row as no longer available to its mapper callback.
     * Later reads fail before accessing the result set.
     */
    void expire() {
        active = false;
    }

    @Override
    public <T> Optional<T> optional(int index, Class<T> type) {
        ensureReadable();
        validateIndex(index);
        return Optional.ofNullable(read(index, type));
    }

    @Override
    public <T> Optional<T> optional(String label, Class<T> type) {
        ensureReadable();
        Objects.requireNonNull(label, "The column label must not be null.");
        if (label.isBlank()) {
            throw new IllegalArgumentException("The column label must not be blank.");
        }
        return Optional.ofNullable(read(columns.index(label), type));
    }

    @Override
    public <T> T required(int index, Class<T> type) {
        ensureReadable();
        validateIndex(index);
        T value = read(index, type);
        if (value == null) {
            throw new DataException("Required result column " + index + " contains SQL NULL.");
        }
        return value;
    }

    @Override
    public <T> T required(String label, Class<T> type) {
        ensureReadable();
        Objects.requireNonNull(label, "The column label must not be null.");
        if (label.isBlank()) {
            throw new IllegalArgumentException("The column label must not be blank.");
        }
        T value = read(columns.index(label), type);
        if (value == null) {
            throw new DataException("Required result column '" + label + "' contains SQL NULL.");
        }
        return value;
    }

    /**
     * Reads a supported scalar while translating any driver failure.
     *
     * @param index one-based column index
     * @param requestedType requested scalar type
     * @param <T> scalar type
     * @return database value, possibly {@code null}
     */
    private <T> T read(int index, Class<T> requestedType) {
        Objects.requireNonNull(requestedType, "The target type must not be null.");
        Class<?> targetType = normalized(requestedType);
        if (!SUPPORTED_TYPES.contains(targetType)) {
            throw new IllegalArgumentException("JDBC does not support the scalar type '"
                                                       + requestedType.getTypeName() + "'.");
        }
        try {
            Object value;
            if (targetType == byte[].class) {
                // getBytes is the portable JDBC path for binary values.
                value = resultSet.getBytes(index);
            } else {
                value = resultSet.getObject(index, targetType);
            }
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        } catch (SQLException e) {
            throw JdbcExceptionTranslator.translate(operation, e);
        } catch (RuntimeException ignored) {
            // Some drivers report conversion or protocol failures as runtime exceptions. Sanitize them here,
            // while their JDBC origin is known, so exceptions thrown by application row mappers remain unchanged.
            throw JdbcExceptionTranslator.resultValueFailure();
        }
    }

    /**
     * Checks an index before delegating to the driver.
     *
     * @param index one-based column index
     */
    private void validateIndex(int index) {
        if (index < 1 || index > columns.columnCount()) {
            throw new IllegalArgumentException("The result column index must be between 1 and "
                                                       + columns.columnCount() + ". The requested index was "
                                                       + index + ".");
        }
    }

    /**
     * Verifies that the current callback can read this row.
     */
    private void ensureReadable() {
        if (!active) {
            throw new IllegalStateException("A JDBC row is valid only during its mapper callback.");
        }
        if (Thread.currentThread() != callbackThread) {
            throw new IllegalStateException("A JDBC row may be read only by its mapper callback thread.");
        }
    }

    /**
     * Converts primitive class tokens to their wrapper equivalents.
     *
     * @param type requested type
     * @return normalized type
     */
    private static Class<?> normalized(Class<?> type) {
        Objects.requireNonNull(type, "The target type must not be null.");
        return type.isPrimitive() ? PRIMITIVE_WRAPPERS.getOrDefault(type, type) : type;
    }
}
