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
import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

import io.helidon.data.DataException;
import io.helidon.data.NoResultException;
import io.helidon.data.NonUniqueResultException;

final class JdbcCallResult implements JdbcClient.CallResult {
    private final JdbcOperation operation;
    private final CallableStatement statement;
    private final List<JdbcCallOutput> outputs;
    private final JdbcConnectionLease lease;
    private final Thread thread = Thread.currentThread();
    private final Set<String> selectedCursors = new HashSet<>();
    private JdbcClient.CallResultKind kind;
    private long count;
    private int event;
    private boolean open = true;
    private boolean legacyCounts;
    private boolean directSelected;
    private boolean terminalActive;
    private boolean cursorPending;
    private ResultSet current;
    private Throwable failure;

    JdbcCallResult(JdbcOperation operation, CallableStatement statement,
                   List<JdbcCallOutput> outputs, JdbcConnectionLease lease) {
        this.operation = operation;
        this.statement = statement;
        this.outputs = outputs;
        this.lease = lease;
    }

    @Override
    public JdbcClient.CallResultKind resultKind() {
        check();
        return kind;
    }

    @Override
    public void next() {
        check();
        if (terminalActive) {
            throw new IllegalStateException("A row terminal must finish before advancement.");
        }
        if (kind == JdbcClient.CallResultKind.END) {
            return;
        }
        process(() -> {
            closeCurrent();
            boolean rows = JdbcExceptionTranslator.invoke("advancing callable results",
                    () -> statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT));
            event++;
            directSelected = false;
            selectEvent(rows);
            return Boolean.TRUE;
        });
    }

    @Override
    public void skipRemainingResults() {
        check();
        while (kind != JdbcClient.CallResultKind.END) {
            next();
        }
        if (terminalActive) {
            throw new IllegalStateException("A row terminal must finish before result traversal.");
        }
    }

    @Override
    public <T> JdbcClient.Rows<T> rows(JdbcClient.RowMapper<T> mapper) {
        Objects.requireNonNull(mapper, "The row mapper must not be null.");
        check();
        if (kind != JdbcClient.CallResultKind.ROWS || directSelected) {
            throw new IllegalStateException("The current event must contain unselected rows.");
        }
        directSelected = true;
        return new CallRows<>(mapper, null, event);
    }

    @Override
    public long updateCount() {
        check();
        if (kind != JdbcClient.CallResultKind.UPDATE_COUNT) {
            throw new IllegalStateException("The current event does not contain an update count.");
        }
        return count;
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output class must not be null.");
        Optional<T> value = optional(name, type);
        return value.orElseGet(() -> process(() -> {
            throw new IllegalArgumentException("The required output contains SQL NULL.");
        }));
    }

    @Override
    public <T> Optional<T> optional(String name, Class<T> type) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(type, "The output class must not be null.");
        JdbcCallOutput output = output(name);
        if (output.type() == JDBCType.REF_CURSOR || !JdbcScalarAccess.supported(type)) {
            throw new IllegalArgumentException("The output requires a supported scalar class.");
        }
        return process(() -> {
            Object value = JdbcExceptionTranslator.invoke("reading a callable scalar output", () -> {
                int index = output.index();
                Class<?> target = JdbcScalarAccess.normalized(type);
                Object scalar;
                if (target == Boolean.class) {
                    scalar = statement.getBoolean(index);
                } else if (target == Byte.class) {
                    scalar = statement.getByte(index);
                } else if (target == Short.class) {
                    scalar = statement.getShort(index);
                } else if (target == Integer.class) {
                    scalar = statement.getInt(index);
                } else if (target == Long.class) {
                    scalar = statement.getLong(index);
                } else if (target == Float.class) {
                    scalar = statement.getFloat(index);
                } else if (target == Double.class) {
                    scalar = statement.getDouble(index);
                } else if (target == BigDecimal.class) {
                    scalar = statement.getBigDecimal(index);
                } else if (target == String.class) {
                    scalar = statement.getString(index);
                } else if (target == byte[].class) {
                    byte[] bytes = statement.getBytes(index);
                    return bytes == null ? null : bytes.clone();
                } else if (target == Date.class || target == LocalDate.class) {
                    Date date = statement.getDate(index);
                    return date == null ? null : target == LocalDate.class ? date.toLocalDate() : new Date(date.getTime());
                } else if (target == Time.class || target == LocalTime.class) {
                    if (target == LocalTime.class) {
                        return statement.getObject(index, LocalTime.class);
                    }
                    Time time = statement.getTime(index);
                    return time == null ? null : new Time(time.getTime());
                } else if (target == Timestamp.class || target == LocalDateTime.class) {
                    if (target == LocalDateTime.class) {
                        // UTC preserves local SQL fields without applying the JVM time zone or daylight saving rules.
                        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                        Timestamp timestamp = statement.getTimestamp(index, calendar);
                        return timestamp == null ? null : LocalDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
                    }
                    Timestamp timestamp = statement.getTimestamp(index);
                    if (timestamp == null) {
                        return null;
                    }
                    Timestamp copy = new Timestamp(timestamp.getTime());
                    copy.setNanos(timestamp.getNanos());
                    return copy;
                } else {
                    throw new IllegalArgumentException("The output class is unsupported.");
                }
                return statement.wasNull() ? null : scalar;
            });
            @SuppressWarnings("unchecked")
            T detached = (T) value;
            return Optional.ofNullable(detached);
        });
    }

    @Override
    public <T> JdbcClient.Rows<T> cursor(String name, JdbcClient.RowMapper<T> mapper) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(mapper, "The row mapper must not be null.");
        JdbcCallOutput output = selectCursor(name);
        cursorPending = true;
        return new CallRows<>(mapper, output, event);
    }

    @Override
    public <T> Optional<JdbcClient.Rows<T>> optionalCursor(String name, JdbcClient.RowMapper<T> mapper) {
        Objects.requireNonNull(name, "The output name must not be null.");
        Objects.requireNonNull(mapper, "The row mapper must not be null.");
        JdbcCallOutput output = selectCursor(name);
        return process(() -> {
            current = readCursor(output);
            if (current == null) {
                return Optional.empty();
            }
            cursorPending = true;
            return Optional.of(new CallRows<>(mapper, output, event));
        });
    }

    void start() {
        process(() -> {
            selectEvent(JdbcExceptionTranslator.invoke("executing a callable statement", statement::execute));
            return Boolean.TRUE;
        });
    }

    Throwable finish(Throwable escaped) {
        Throwable primary = JdbcExceptionTranslator.mergeProcessingFailures(failure, escaped);
        if (primary == null) {
            try {
                // A selected but unconsumed cursor is discarded with the statement.
                cursorPending = false;
                skipRemainingResults();
            } catch (RuntimeException | Error caught) {
                primary = caught;
            }
        }
        open = false;
        if (primary != null) {
            lease.processingFailed();
        }
        try {
            closeCurrent();
        } catch (Throwable caught) {
            lease.processingFailed();
            primary = JdbcExceptionTranslator.mergeOperationCleanup(primary, "closing callable rows", caught);
        }
        return primary;
    }

    private void check() {
        if (!open || Thread.currentThread() != thread || failure != null) {
            throw new IllegalStateException("The callable result is expired, failed, or accessed from another thread.");
        }
    }

    private JdbcCallOutput output(String name) {
        check();
        if (kind != JdbcClient.CallResultKind.END || cursorPending || terminalActive) {
            throw new IllegalStateException("Outputs require END with no active row channel.");
        }
        return outputs.stream().filter(output -> output.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The output name is not registered."));
    }

    private JdbcCallOutput selectCursor(String name) {
        JdbcCallOutput output = output(name);
        if (output.type() != JDBCType.REF_CURSOR) {
            throw new IllegalArgumentException("The output is not registered as REF_CURSOR.");
        }
        if (!selectedCursors.add(name)) {
            throw new IllegalStateException("The cursor has already been selected.");
        }
        return output;
    }

    private ResultSet readCursor(JdbcCallOutput output) throws SQLException {
        Object value = JdbcExceptionTranslator.invoke("reading a callable REF_CURSOR output",
                () -> statement.getObject(output.index()));
        if (value == null) {
            return null;
        }
        if (!(value instanceof ResultSet resultSet)) {
            throw new DataException("The JDBC driver did not return a result set for a REF_CURSOR output.");
        }
        return resultSet;
    }

    private void selectEvent(boolean rows) throws SQLException {
        if (rows) {
            kind = JdbcClient.CallResultKind.ROWS;
            current = JdbcExceptionTranslator.invoke("reading callable direct rows", statement::getResultSet);
            if (current == null) {
                throw new DataException("The JDBC driver reported rows without a result set.");
            }
        } else {
            if (!legacyCounts) {
                try {
                    count = statement.getLargeUpdateCount();
                } catch (SQLFeatureNotSupportedException | UnsupportedOperationException _) {
                    legacyCounts = true;
                } catch (RuntimeException caught) {
                    throw (RuntimeException) JdbcExceptionTranslator.sanitize("reading a callable update count", caught);
                }
            }
            if (legacyCounts) {
                count = JdbcExceptionTranslator.invoke("reading a callable update count", statement::getUpdateCount);
            }
            kind = count == -1 ? JdbcClient.CallResultKind.END : JdbcClient.CallResultKind.UPDATE_COUNT;
        }
    }

    private void closeCurrent() throws SQLException {
        ResultSet closing = current;
        current = null;
        if (closing != null) {
            JdbcExceptionTranslator.invokeVoid("closing callable rows", closing::close);
        }
    }

    private <T> T process(Action<T> action) {
        try {
            return action.run();
        } catch (SQLException caught) {
            RuntimeException translated = JdbcExceptionTranslator.translate(operation, caught);
            retain(translated);
            throw translated;
        } catch (RuntimeException | Error caught) {
            retain(caught);
            throw caught;
        }
    }

    private void retain(Throwable caught) {
        if (failure == null) {
            lease.processingFailed();
        }
        failure = JdbcExceptionTranslator.mergeProcessingFailures(failure, caught);
    }

    @FunctionalInterface
    private interface Action<T> {
        T run() throws SQLException;
    }

    private final class CallRows<T> implements JdbcClient.Rows<T> {
        private final JdbcClient.RowMapper<T> mapper;
        private final JdbcCallOutput cursor;
        private final int selectedEvent;
        private boolean consumed;

        private CallRows(JdbcClient.RowMapper<T> mapper, JdbcCallOutput cursor, int selectedEvent) {
            this.mapper = mapper;
            this.cursor = cursor;
            this.selectedEvent = selectedEvent;
        }

        @Override
        public T one() {
            return materialize(1).getFirst();
        }

        @Override
        public Optional<T> optional() {
            return materialize(0).stream().findFirst();
        }

        @Override
        public List<T> list() {
            return materialize(-1);
        }

        private List<T> materialize(int cardinality) {
            check();
            if (consumed || selectedEvent != event || terminalActive) {
                throw new IllegalStateException("The callable row stage has already been consumed or expired.");
            }
            consumed = true;
            terminalActive = true;
            return process(() -> {
                try {
                    if (cursor != null && current == null) {
                        current = readCursor(cursor);
                    }
                    if (current == null) {
                        throw new IllegalArgumentException("The required cursor contains SQL NULL.");
                    }
                    JdbcColumnLayout columns = JdbcColumnLayout.create(
                            JdbcExceptionTranslator.invoke("reading callable column metadata", current::getMetaData), operation);
                    List<T> values = new ArrayList<>();
                    while (JdbcExceptionTranslator.invoke("advancing callable rows", current::next)) {
                        if (cardinality >= 0 && !values.isEmpty()) {
                            throw new NonUniqueResultException("The callable result contains more than one row.");
                        }
                        JdbcRow row = new JdbcRow(current, columns, operation,
                                                  JdbcCallResult.this::check, JdbcCallResult.this::retain);
                        T value;
                        try {
                            value = mapper.map(row);
                            if (failure instanceof RuntimeException runtime) {
                                throw runtime;
                            }
                            if (failure instanceof Error error) {
                                throw error;
                            }
                            if (value == null) {
                                throw new DataException("The callable row mapper returned null.");
                            }
                        } finally {
                            row.expire();
                        }
                        values.add(value);
                    }
                    if (cardinality == 1 && values.isEmpty()) {
                        throw new NoResultException("The callable result contains no rows.");
                    }
                    return List.copyOf(values);
                } catch (SQLException caught) {
                    RuntimeException translated = JdbcExceptionTranslator.translate(operation, caught);
                    retain(translated);
                    throw translated;
                } catch (RuntimeException | Error caught) {
                    retain(caught);
                    throw caught;
                } finally {
                    terminalActive = false;
                    cursorPending = false;
                    try {
                        closeCurrent();
                    } catch (SQLException | RuntimeException | Error caught) {
                        if (failure == null) {
                            throw caught;
                        }
                        failure = JdbcExceptionTranslator.mergeOperationCleanup(failure, "closing callable rows", caught);
                    }
                }
            });
        }
    }
}
