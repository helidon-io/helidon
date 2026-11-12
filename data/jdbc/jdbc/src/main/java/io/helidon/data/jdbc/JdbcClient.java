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

import java.sql.JDBCType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.service.registry.Service;

/**
 * Executes JDBC statements for applications and generated repositories.
 * <p>
 * Creating and binding a statement performs no database work. A terminal
 * operation acquires JDBC resources, consumes results within its scope, and
 * releases its operation resources before returning. Materialized terminals
 * return detached values. The client is safe to share.
 * Statement and result stages are single use and are not safe for concurrent
 * use.
 * <p>
 * A client created programmatically obtains and closes one connection for
 * each terminal operation. It does not participate in a transaction established
 * by a transaction annotation on its caller. A configured data source name is
 * resolved through the global service registry. When an existing
 * {@link javax.sql.DataSource} is supplied directly, the application retains
 * ownership of the data source, while the client closes each connection it
 * obtains from it.
 * <p>
 * Annotation based applications inject a registry managed client with the
 * {@code jdbc} {@link io.helidon.data.Data.ProviderType} and a {@link Service.Named}
 * qualifier. A registry managed client uses Helidon's local transaction
 * support and participates in the transaction surrounding the intercepted
 * service invocation.
 */
@Api.Incubating
@Api.Since("27.0.0")
@Service.Contract
public interface JdbcClient extends RuntimeType.Api<JdbcClientConfig> {

    /**
     * Creates a builder for a JDBC client.
     *
     * @return new JDBC client builder
     */
    static JdbcClientConfig.Builder builder() {
        return JdbcClientConfig.builder();
    }

    /**
     * Creates a JDBC client from an immutable configuration.
     *
     * @param config JDBC client configuration
     * @return configured JDBC client
     * @throws NullPointerException if the configuration is {@code null}
     * @throws io.helidon.data.DataException if the configuration is invalid,
     *                                          a named data source cannot be resolved,
     *                                          or a JDBC driver cannot be resolved for direct connection settings
     */
    static JdbcClient create(JdbcClientConfig config) {
        Objects.requireNonNull(config, "The JDBC client configuration must not be null.");
        return JdbcClientImpl.create(config);
    }

    /**
     * Creates a JDBC client after updating a new builder.
     *
     * @param consumer builder updates
     * @return configured JDBC client
     * @throws NullPointerException if the consumer is {@code null}
     * @throws io.helidon.data.DataException if the configuration is invalid,
     *                                          a named data source cannot be resolved,
     *                                          or a JDBC driver cannot be resolved for direct connection settings
     */
    static JdbcClient create(Consumer<JdbcClientConfig.Builder> consumer) {
        Objects.requireNonNull(consumer, "The JDBC client builder consumer must not be null.");
        return builder().update(consumer).build();
    }

    /**
     * Creates a description of one JDBC statement from positional SQL.
     * <p>
     * A terminal operation supplies the complete SQL string to the driver as
     * one {@link java.sql.PreparedStatement}. Helidon does not parse database
     * grammar, split scripts, or validate statement boundaries specific to a
     * database. The supported contract therefore requires exactly one SQL
     * statement. SQL scripts, batches, compound or multiple statement strings
     * specific to a JDBC driver, SQL that controls
     * transactions, and commands that change connection or session state,
     * including auto-commit mode, are not supported.
     * <p>
     * A single schema definition statement may be executed when the terminal
     * operation does not participate in a Helidon local JDBC transaction. The
     * operation then uses its own connection in auto-commit mode, and the
     * statement executes as an independent auto-commit operation.
     * Schema definition statements and any other statements with semantics
     * specific to a database that implicitly commit or otherwise end a
     * transaction are not supported while participating in a Helidon local
     * JDBC transaction. This restriction applies regardless of whether the
     * database supports transactional DDL.
     * <p>
     * Helidon treats the SQL as opaque: it does not classify statements or
     * reliably detect an implicit commit. If unsupported SQL is executed
     * within a local transaction, a database may commit pending work while
     * leaving JDBC auto-commit disabled. A later rollback cannot undo that
     * committed work. Consequently, a terminal operation or transaction
     * failure does not establish that the database made no changes, and
     * applications must not retry the operation automatically.
     * <p>
     * The SQL is trusted executable application input. This method does not
     * sanitize data concatenated into the SQL text. Represent untrusted values
     * with {@code ?} markers and supply them through
     * {@link Statement#bind(int, Object)}. Bind markers represent values only.
     * Select identifiers, operators, sort directions, and other SQL structure
     * from an explicit application allowlist.
     * <p>
     * Marker recognition uses a portable lexical policy. Question marks
     * inside ordinary strings enclosed in single quotes, PostgreSQL escape and
     * dollar quoted strings, identifiers quoted with double quotes or
     * backticks, Oracle alternative strings, and conventional comments are
     * ignored. Square brackets are ordinary punctuation. A doubled question
     * mark is preserved as driver escape syntax rather than counted as bind
     * markers. A {@code --} comment requires following whitespace, a control
     * character, or the end of input. Other sequences containing two hyphens
     * are rejected because their meaning differs among SQL dialects. Nested
     * block comments are rejected.
     *
     * @param sql one SQL statement containing zero or more {@code ?} markers
     * @return statement description
     * @throws NullPointerException if the SQL is {@code null}
     * @throws IllegalArgumentException if the SQL is blank, malformed, or contains named markers
     */
    Statement create(String sql);

    /**
     * Prepares an immutable SQL definition without acquiring JDBC resources.
     * The returned supplier is safe to share and creates a fresh single-use statement
     * on each invocation without rescanning SQL. Statements are not safe for concurrent mutation.
     * Named, positional, and marker-free SQL are supported. A leading callable return
     * may precede named arguments for CALL execution. Other mixed marker styles are rejected.
     * Statement boundaries, trusted input, resource ownership, and transaction
     * restrictions described by {@link #create(String)} apply.
     * Preparation captures no transaction context. Registry managed clients determine transaction
     * participation at execution time. Standalone clients remain independent of an enclosing managed transaction.
     * Retain the supplier to reuse its definition. The client retains no history of definitions.
     *
     * @param sql The SQL text defines one trusted statement.
     * @return The supplier creates fresh stages and owns no JDBC resources.
     * @throws NullPointerException The SQL is null.
     * @throws IllegalArgumentException The SQL is blank, malformed, or mixes marker styles.
     * @throws UnsupportedOperationException The provider does not support reusable definitions.
     */
    default Supplier<Statement> prepare(String sql) {
        Objects.requireNonNull(sql, "The SQL statement must not be null.");
        throw new UnsupportedOperationException("This JDBC client does not support reusable statement definitions.");
    }

    /**
     * Prepares a reusable SQL definition with logical parameter names supplied in order.
     * Names must be unique and follow Java identifier character rules, without a colon prefix.
     * Named SQL must use exactly these names and may repeat markers. Positional SQL
     * must have one marker per name and associates names with positions in list order.
     * For CALL, include every IN, OUT, and INOUT parameter once. Include return first
     * for a leading callable return. Bind only inputs and register outputs through the call stage.
     * A leading return cannot be bound as an input. Repeated OUT or INOUT markers are rejected.
     * SQL without markers requires an empty list. Indexed bindings always address JDBC
     * positions. The list is defensively copied. Each supplier invocation creates a new
     * statement with independent bindings. Other preparation rules match {@link #prepare(String)}.
     *
     * @param sql The SQL text defines one trusted statement.
     * @param inputNames The names identify logical parameters in list order.
     * @return The supplier creates new statements with independent bindings.
     * @throws NullPointerException The SQL, list, or a list entry is null.
     * @throws IllegalArgumentException The SQL or input contract is invalid.
     * @throws UnsupportedOperationException The provider does not support reusable definitions.
     */
    default Supplier<Statement> prepare(String sql, List<String> inputNames) {
        Objects.requireNonNull(sql, "The SQL statement must not be null.");
        Objects.requireNonNull(inputNames, "The input names must not be null.");
        inputNames.forEach(name -> Objects.requireNonNull(name, "The input name must not be null."));
        throw new UnsupportedOperationException("This JDBC client does not support reusable statement definitions.");
    }

    /**
     * Identifies a direct callable result event.
     */
    @Api.Incubating
    enum CallResultKind {
        /**
         * The current event contains direct rows.
         */
        ROWS,
        /**
         * The current event contains an update count, including zero.
         */
        UPDATE_COUNT,
        /**
         * No direct events remain and registered outputs are accessible.
         */
        END
    }

    /**
     * Describes one prepared JDBC operation.
     * <p>
     * Bind positions use JDBC indexes, which start at one. The stage accepts
     * exactly one terminal operation and is not safe for concurrent use.
     */
    @Api.Incubating
    interface Statement {

        /**
         * Selects callable execution without acquiring JDBC resources.
         *
         * @return A single use callable stage.
         * @throws UnsupportedOperationException This provider does not support calls.
         * @throws IllegalStateException A terminal operation has started.
         */
        default Call call() {
            throw new UnsupportedOperationException("Callable execution is unsupported.");
        }

        /**
         * Binds a supported scalar value that is not {@code null}.
         *
         * @param index JDBC position, starting at one
         * @param value supported scalar value
         * @return this statement
         * @throws NullPointerException if the value is {@code null}
         * @throws IllegalArgumentException if the position or value is invalid
         * @throws IllegalStateException if a terminal operation has started
         */
        Statement bind(int index, Object value);

        /**
         * Binds a supported nonnull scalar to every physical position of a logical input.
         * All destination slots are validated before any assignment.
         *
         * @param name The name identifies a logical input without a colon.
         * @param value The supported scalar value.
         * @return This statement.
         * @throws NullPointerException The name or value is null.
         * @throws IllegalArgumentException The name, value, or assignment is invalid.
         * @throws IllegalStateException The stage is consumed or does not support named bindings.
         * @throws UnsupportedOperationException The provider does not support named bindings.
         */
        default Statement bind(String name, Object value) {
            Objects.requireNonNull(name, "The bind name must not be null.");
            Objects.requireNonNull(value, "The bind value must not be null.");
            throw new UnsupportedOperationException("This JDBC statement does not support named bindings.");
        }

        /**
         * Executes an update and returns its update count as a {@code long} value.
         * <p>
         * Helidon's JDBC provider uses the large update count when the driver
         * supports it. Otherwise, the provider returns the legacy integer
         * update count as a {@code long} value.
         * <p>
         * A connection owned by the operation uses auto-commit. A failure
         * reported after the update executes, including while processing the
         * result or releasing JDBC resources, does not establish that the
         * database made no changes. Applications must not retry the update
         * automatically.
         *
         * @return update count
         * @throws io.helidon.data.DataException if JDBC execution fails
         * @throws IllegalStateException if a bind is missing or a terminal operation has started
         */
        long execute();

        /**
         * Selects an explicit mapper for query rows.
         *
         * @param mapper row mapper
         * @param <T> mapped type
         * @return terminal stage for mapped rows
         * @throws NullPointerException if the mapper is {@code null}
         * @throws IllegalStateException if a terminal operation has started
         */
        <T> Rows<T> map(RowMapper<T> mapper);

        /**
         * Selects mapping from the first column for a supported scalar type.
         * <p>
         * {@link Rows#one()} and {@link Rows#list()} require a column value
         * that is not {@code null}.
         * {@link Rows#optional()} returns {@link Optional#empty()} both
         * when no row is returned and when exactly one row contains SQL
         * {@code NULL} in column one. Applications that need to distinguish
         * those states can use {@link #map(RowMapper)} with a mapper that
         * returns an {@link Optional}; the outer optional returned by
         * {@link Rows#optional()} then represents row presence.
         *
         * @param scalarType scalar type
         * @param <T> mapped scalar type
         * @return terminal stage for mapped rows
         * @throws NullPointerException if the type is {@code null}
         * @throws IllegalArgumentException if the scalar type is not supported
         * @throws IllegalStateException if a terminal operation has started
         */
        <T> Rows<T> map(Class<T> scalarType);

        /**
         * Selects generated key execution.
         * <p>
         * Adding no columns requests the driver's default generated keys.
         * Configuration does not acquire JDBC resources.
         *
         * @return generated key configuration stage
         * @throws IllegalStateException if a terminal operation has started
         */
        GeneratedKeys generatedKeys();
    }

    /**
     * Configures outputs and executes one callable statement. Registration performs no JDBC work.
     * Both escape forms may invoke procedures or functions as supported by the driver.
     * The application supplies compatible SQL and driver connection settings.
     * A leading return must be registered as an output even when its value is discarded.
     * Transaction controlling routines are unsupported within a local JDBC transaction.
     */
    @Api.Incubating
    interface Call {

        /**
         * Registers one ordinary output by its marker name or positional layout name.
         * The definition must retain a unique position for this name. Use returns for a leading return.
         *
         * @param name The marker or layout name also identifies the output value.
         * @param type The JDBC registration type.
         * @return This callable stage.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The registration or parameter position is invalid.
         * @throws IllegalStateException The statement is consumed or lacks logical parameter metadata.
         * @throws UnsupportedOperationException The provider does not support this registration.
         */
        default Call out(String name, JDBCType type) {
            Objects.requireNonNull(name, "The output name must not be null.");
            Objects.requireNonNull(type, "The output type must not be null.");
            throw new UnsupportedOperationException("Logical callable registration is unsupported.");
        }

        /**
         * Registers one ordinary output by its marker name or positional layout name.
         * The definition must retain a unique position for this name. Use returns for a leading return.
         *
         * @param name The marker or layout name also identifies the output value.
         * @param type The JDBC registration type.
         * @param typeName The nonblank database type name.
         * @return This callable stage.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The registration or parameter position is invalid.
         * @throws IllegalStateException The statement is consumed or lacks logical parameter metadata.
         * @throws UnsupportedOperationException The provider does not support this registration.
         */
        default Call out(String name, JDBCType type, String typeName) {
            Objects.requireNonNull(name, "The output name must not be null.");
            Objects.requireNonNull(type, "The output type must not be null.");
            Objects.requireNonNull(typeName, "The database type name must not be null.");
            throw new UnsupportedOperationException("Logical callable registration is unsupported.");
        }

        /**
         * Registers one ordinary output by its marker name or positional layout name.
         * The definition must retain a unique position for this name. Use returns for a leading return.
         *
         * @param name The marker or layout name also identifies the output value.
         * @param type The JDBC registration type.
         * @param scale The nonnegative scale requires NUMERIC or DECIMAL.
         * @return This callable stage.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The registration or parameter position is invalid.
         * @throws IllegalStateException The statement is consumed or lacks logical parameter metadata.
         * @throws UnsupportedOperationException The provider does not support this registration.
         */
        default Call out(String name, JDBCType type, int scale) {
            Objects.requireNonNull(name, "The output name must not be null.");
            Objects.requireNonNull(type, "The output type must not be null.");
            throw new UnsupportedOperationException("Logical callable registration is unsupported.");
        }

        /**
         * Registers the leading callable return at JDBC position one with the logical name return.
         * The SQL must contain a leading {@code ? = call} return slot.
         *
         * @param type The JDBC registration type.
         * @return This callable stage.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The registration or parameter position is invalid.
         * @throws IllegalStateException The statement is consumed or lacks logical parameter metadata.
         * @throws UnsupportedOperationException The provider does not support this registration.
         */
        default Call returns(JDBCType type) {
            Objects.requireNonNull(type, "The output type must not be null.");
            throw new UnsupportedOperationException("Logical callable registration is unsupported.");
        }

        /**
         * Registers the leading callable return at JDBC position one with the logical name return.
         * The SQL must contain a leading {@code ? = call} return slot.
         *
         * @param type The JDBC registration type.
         * @param typeName The nonblank database type name.
         * @return This callable stage.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The registration or parameter position is invalid.
         * @throws IllegalStateException The statement is consumed or lacks logical parameter metadata.
         * @throws UnsupportedOperationException The provider does not support this registration.
         */
        default Call returns(JDBCType type, String typeName) {
            Objects.requireNonNull(type, "The output type must not be null.");
            Objects.requireNonNull(typeName, "The database type name must not be null.");
            throw new UnsupportedOperationException("Logical callable registration is unsupported.");
        }

        /**
         * Registers the leading callable return at JDBC position one with the logical name return.
         * The SQL must contain a leading {@code ? = call} return slot.
         *
         * @param type The JDBC registration type.
         * @param scale The nonnegative scale requires NUMERIC or DECIMAL.
         * @return This callable stage.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The registration or parameter position is invalid.
         * @throws IllegalStateException The statement is consumed or lacks logical parameter metadata.
         * @throws UnsupportedOperationException The provider does not support this registration.
         */
        default Call returns(JDBCType type, int scale) {
            Objects.requireNonNull(type, "The output type must not be null.");
            throw new UnsupportedOperationException("Logical callable registration is unsupported.");
        }


        /**
         * Registers an output or an input and output parameter.
         *
         * @param name The unique logical output name.
         * @param index The unique JDBC position, starting at one.
         * @param type The explicit JDBC registration type.
         * @return This call.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The registration is invalid or duplicated.
         * @throws IllegalStateException The statement has been consumed.
         */
        Call out(String name, int index, JDBCType type);

        /**
         * Registers an output with a database type name.
         *
         * @param name The unique logical output name.
         * @param index The JDBC position, starting at one.
         * @param type The JDBC registration type.
         * @param typeName The nonblank database type name.
         * @return This call.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The registration is invalid or duplicated.
         * @throws IllegalStateException The statement has been consumed.
         */
        Call out(String name, int index, JDBCType type, String typeName);

        /**
         * Registers a NUMERIC or DECIMAL output with a scale.
         *
         * @param name The unique logical output name.
         * @param index The JDBC position, starting at one.
         * @param type The JDBC registration type.
         * @param scale The nonnegative fractional scale.
         * @return This call.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The registration is invalid or duplicated.
         * @throws IllegalStateException The statement has been consumed.
         */
        Call out(String name, int index, JDBCType type, int scale);

        /**
         * Executes once and discards all results and outputs.
         *
         * @throws io.helidon.data.DataException Execution or cleanup failed.
         * @throws IllegalStateException A binding is missing or the statement has been consumed.
         */
        void execute();

        /**
         * Captures canonical detached scalar outputs after discarding direct results.
         * Binary values are copied at capture and on every read. Named types, cursors,
         * locators, and offset temporal types are outside this snapshot profile.
         * Driver support is required for the registration and its canonical getter.
         * <table>
         * <caption>Canonical detached outputs</caption>
         * <tr><th>JDBC registrations</th><th>Java class</th></tr>
         * <tr><td>BOOLEAN, BIT</td><td>{@link Boolean}</td></tr>
         * <tr><td>TINYINT</td><td>{@link Byte}</td></tr>
         * <tr><td>SMALLINT</td><td>{@link Short}</td></tr>
         * <tr><td>INTEGER</td><td>{@link Integer}</td></tr>
         * <tr><td>BIGINT</td><td>{@link Long}</td></tr>
         * <tr><td>REAL</td><td>{@link Float}</td></tr>
         * <tr><td>FLOAT, DOUBLE</td><td>{@link Double}</td></tr>
         * <tr><td>NUMERIC, DECIMAL</td><td>{@link java.math.BigDecimal}</td></tr>
         * <tr><td>CHAR, VARCHAR, LONGVARCHAR, NCHAR, NVARCHAR, LONGNVARCHAR</td><td>{@link String}</td></tr>
         * <tr><td>BINARY, VARBINARY, LONGVARBINARY</td><td>{@code byte[]}</td></tr>
         * <tr><td>DATE</td><td>{@link java.time.LocalDate}</td></tr>
         * <tr><td>TIME</td><td>{@link java.time.LocalTime}</td></tr>
         * <tr><td>TIMESTAMP</td><td>{@link java.time.LocalDateTime}</td></tr>
         * </table>
         *
         * @return An immutable snapshot, which may be empty.
         * @throws IllegalStateException A registration is outside the snapshot profile or the stage is unusable.
         * @throws io.helidon.data.DataException Execution, output retrieval, or cleanup failed.
         */
        OutParameters outputs();

        /**
         * Consumes scoped results synchronously on the invocation thread.
         * Caught provider processing failures are reported again after cleanup.
         *
         * @param action The result callback.
         * @throws NullPointerException The callback is null.
         * @throws io.helidon.data.DataException JDBC processing or cleanup failed.
         * @throws IllegalStateException A binding is missing or the statement has been consumed.
         */
        void consume(Consumer<CallResult> action);

        /**
         * Maps scoped results to a detached value synchronously on the invocation thread.
         * The callback must return a nonnull value. Caught provider processing failures
         * are reported again after cleanup. A participating connection remains reserved
         * until cleanup completes, including after a row terminal completes.
         *
         * @param mapper The result callback.
         * @param <T> The detached result type.
         * @return The detached value.
         * @throws NullPointerException The mapper is null.
         * @throws io.helidon.data.DataException JDBC processing or cleanup failed, or the callback returned null.
         * @throws IllegalStateException A binding is missing or the statement has been consumed.
         */
        <T> T map(CallMapper<T> mapper);
    }

    /**
     * Holds immutable canonical scalar outputs independently of JDBC resources.
     */
    @Api.Incubating
    interface OutParameters {
        /**
         * Reads a required canonical value without conversion.
         *
         * @param name The registered logical output name.
         * @param type The canonical class, allowing primitive and wrapper equivalents.
         * @param <T> The value type.
         * @return The detached value.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The name or class is invalid, or the value is SQL NULL.
         */
        <T> T get(String name, Class<T> type);

        /**
         * Reads an optional canonical value without conversion, validating its class even for SQL NULL.
         *
         * @param name The registered logical output name.
         * @param type The canonical class, allowing primitive and wrapper equivalents.
         * @param <T> The value type.
         * @return The optional detached value.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The name or class is invalid.
         */
        <T> Optional<T> optional(String name, Class<T> type);
    }

    /**
     * Maps a scoped call to an application value.
     *
     * @param <R> The detached result type.
     */
    @Api.Incubating
    @FunctionalInterface
    interface CallMapper<R> {
        /**
         * Maps results before the provider closes its JDBC resources.
         *
         * @param result The scoped result.
         * @return A nonnull detached value.
         * @throws NullPointerException The scoped result is null.
         */
        R map(CallResult result);
    }

    /**
     * Exposes callable events and outputs on the invocation thread during the callback.
     * Direct row stages also expire when their event advances. Outputs require END.
     * Finish a selected cursor before accessing another output and read scalars first.
     */
    @Api.Incubating
    interface CallResult {
        /**
         * Returns the current direct event.
         *
         * @return The current event kind.
         * @throws IllegalStateException The scope is expired, failed, or accessed from another thread.
         */
        CallResultKind resultKind();

        /**
         * Discards the current event and advances with CLOSE_CURRENT_RESULT. END is idempotent.
         *
         * @throws IllegalStateException The scope is unusable or a row terminal is active.
         * @throws io.helidon.data.DataException JDBC advancement failed.
         */
        void next();

        /**
         * Discards direct events until END without mapping their rows.
         *
         * @throws IllegalStateException The scope is unusable or a row terminal is active.
         * @throws io.helidon.data.DataException JDBC advancement failed.
         */
        void skipRemainingResults();

        /**
         * Selects one scoped row stage for the current ROWS event.
         *
         * @param mapper The row mapper.
         * @param <T> The mapped type.
         * @return The scoped row stage.
         * @throws NullPointerException The mapper is null.
         * @throws IllegalStateException The event is incompatible or has already been selected, or the scope is unusable.
         */
        <T> Rows<T> rows(RowMapper<T> mapper);

        /**
         * Reads the current update count, preserving zero.
         *
         * @return The count.
         * @throws IllegalStateException The event is incompatible or the scope is unusable.
         */
        long updateCount();

        /**
         * Reads a required scalar using JDBC conversion and detaches supported mutable values.
         *
         * @param name The registered logical name.
         * @param type The supported target scalar class.
         * @param <T> The scalar type.
         * @return The detached scalar.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The name or type is invalid, or the value is SQL NULL.
         * @throws IllegalStateException Outputs are unavailable or the scope is unusable.
         * @throws io.helidon.data.DataException JDBC conversion failed.
         */
        <T> T get(String name, Class<T> type);

        /**
         * Reads an optional scalar using JDBC conversion.
         *
         * @param name The registered logical name.
         * @param type The supported target scalar class.
         * @param <T> The scalar type.
         * @return The optional detached scalar.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The name or type is invalid.
         * @throws IllegalStateException Outputs are unavailable or the scope is unusable.
         * @throws io.helidon.data.DataException JDBC conversion failed.
         */
        <T> Optional<T> optional(String name, Class<T> type);

        /**
         * Selects a required REF_CURSOR, retrieving it lazily at its row terminal.
         * That terminal throws {@link IllegalArgumentException} if the output contains SQL NULL.
         *
         * @param name The registered logical name.
         * @param mapper The row mapper.
         * @param <T> The mapped type.
         * @return The scoped row stage.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The name is invalid or the output is not a cursor.
         * @throws IllegalStateException Outputs are unavailable, already selected, or the scope is unusable.
         */
        <T> Rows<T> cursor(String name, RowMapper<T> mapper);

        /**
         * Retrieves an optional REF_CURSOR immediately. SQL NULL produces an empty optional without invoking the mapper.
         * A present empty cursor remains present.
         *
         * @param name The registered logical name.
         * @param mapper The row mapper.
         * @param <T> The mapped type.
         * @return An optional scoped row stage.
         * @throws NullPointerException A reference argument is null.
         * @throws IllegalArgumentException The name is invalid or the output is not a cursor.
         * @throws IllegalStateException Outputs are unavailable, already selected, or the scope is unusable.
         * @throws io.helidon.data.DataException JDBC retrieval failed.
         */
        <T> Optional<Rows<T>> optionalCursor(String name, RowMapper<T> mapper);
    }

    /**
     * Configures generated key columns and mapping before execution.
     * <p>
     * The stage preserves column order. It is single use, is not safe for
     * concurrent use, and performs no JDBC work.
     * <p>
     * When the operation owns the connection, the update may be committed
     * before a terminal operation finishes reading or mapping the generated
     * keys, checking the expected number of keys, or releasing JDBC resources.
     * A terminal failure therefore does not establish that the update was
     * rolled back and must not be retried automatically. When generated key
     * materialization and the update must be atomic, use a client managed by
     * the registry inside a local transaction and let the failure escape the
     * transaction boundary. A directly created client does not join such a
     * transaction.
     */
    @Api.Incubating
    interface GeneratedKeys {

        /**
         * Adds one generated column requested from JDBC.
         *
         * @param columnName generated column name that is not blank
         * @return this generated key stage
         * @throws NullPointerException if the column name is {@code null}
         * @throws IllegalArgumentException if the column name is blank or exactly duplicates an existing name
         * @throws IllegalStateException if mapping has already been selected
         */
        GeneratedKeys addColumn(String columnName);

        /**
         * Selects the mapper for generated key rows and finalizes this stage.
         *
         * @param mapper generated key row mapper
         * @param <T> mapped key type
         * @return terminal stage for materialized keys
         * @throws NullPointerException if the mapper is {@code null}
         * @throws IllegalStateException if mapping has already been selected
         */
        <T> Rows<T> map(RowMapper<T> mapper);
    }

    /**
     * Result terminals for mapped query rows, callable rows, or generated keys.
     * <p>
     * The stage accepts exactly one terminal invocation and is not safe for
     * concurrent use. An exception from an application mapper is propagated
     * unchanged.
     *
     * @param <T> mapped type
     */
    @Api.Incubating
    interface Rows<T> {

        /**
         * Returns exactly one row.
         *
         * @return mapped row
         * @throws io.helidon.data.NoResultException if no row is returned
         * @throws io.helidon.data.NonUniqueResultException if more than one row is returned
         * @throws io.helidon.data.DataException if JDBC execution or provider mapping fails
         * @throws IllegalStateException if a terminal operation has started
         */
        T one();

        /**
         * Returns zero or one row.
         * <p>
         * For a scalar stage created by {@link Statement#map(Class)}, an empty
         * result represents either no row or exactly one row whose first
         * column is SQL {@code NULL}. For a stage created by
         * {@link Statement#map(RowMapper)}, an empty result represents no row;
         * the mapper may return an {@link Optional} value to represent column
         * nullability separately.
         *
         * @return empty if no row is returned or, for scalar mapping selected
         *         by {@link Statement#map(Class)}, if one row contains SQL
         *         {@code NULL} in column one; otherwise the mapped row
         * @throws io.helidon.data.NonUniqueResultException if more than one row is returned
         * @throws io.helidon.data.DataException if JDBC execution or provider mapping fails
         * @throws IllegalStateException if a terminal operation has started
         */
        Optional<T> optional();

        /**
         * Returns all rows in JDBC encounter order.
         *
         * @return materialized rows
         * @throws io.helidon.data.DataException if JDBC execution or provider mapping fails
         * @throws IllegalStateException if a terminal operation has started
         */
        List<T> list();

    }

    /**
     * Maps one JDBC row to an application value during a callback.
     * <p>
     * Imperative code passes a mapper to {@link Statement#map(RowMapper)} or
     * {@link GeneratedKeys#map(RowMapper)}. Applications may also register
     * implementations as services. A generated repository resolves a mapper selected by
     * {@code @Jdbc.RowMapper(SomeMapper.class)} by its concrete service type.
     * The marker form {@code @Jdbc.RowMapper} resolves this contract with the
     * exact {@code T} type.
     * <p>
     * A mapper held by a singleton repository may be invoked concurrently. It
     * must be stateless or safe for concurrent use. Each invocation receives
     * its own row. The row may be read only by the thread executing
     * {@link RowMapper#map(Row)}. The provider retains ownership of the JDBC
     * resources and exposes only the scoped row view.
     *
     * @param <T> mapped type
     */
    @Api.Incubating
    @Service.Contract
    @FunctionalInterface
    interface RowMapper<T> {

        /**
         * Maps the current row synchronously. Row reads call the JDBC driver
         * and may block until the driver returns or reports a failure. The row
         * may be read only by the current thread and is no longer valid after
         * this method returns.
         *
         * @param row current row
         * @return mapped value, never {@code null}
         * @throws NullPointerException The current row is null.
         */
        T map(Row row);
    }

    /**
     * Restricted view of the current result row.
     * <p>
     * The row may be read only by the thread executing the current
     * {@link RowMapper#map(Row)} callback. It must not be passed to another
     * thread and is no longer valid after the callback returns.
     */
    @Api.Incubating
    interface Row {

        /**
         * Reads a nullable value by a column index that starts at one.
         *
         * @param index column index, starting at one
         * @param type requested scalar type
         * @param <T> scalar type
         * @return optional value
         * @throws io.helidon.data.DataException if the column cannot be read
         * @throws NullPointerException if the type is {@code null}
         * @throws IllegalArgumentException if the index or type is invalid
         * @throws IllegalStateException if the row is no longer active or the
         *                                  caller is not the callback thread
         */
        <T> Optional<T> optional(int index, Class<T> type);

        /**
         * Reads a nullable value by column label.
         *
         * @param label column label
         * @param type requested scalar type
         * @param <T> scalar type
         * @return optional value
         * @throws io.helidon.data.DataException if the label is absent or ambiguous, or the column cannot be read
         * @throws NullPointerException if the label or type is {@code null}
         * @throws IllegalArgumentException if the label or type is invalid
         * @throws IllegalStateException if the row is no longer active or the
         *                                  caller is not the callback thread
         */
        <T> Optional<T> optional(String label, Class<T> type);

        /**
         * Reads a value that must not be {@code null} by the column index,
         * starting at one.
         *
         * @param index column index, starting at one
         * @param type requested scalar type
         * @param <T> scalar type
         * @return value that is not {@code null}
         * @throws io.helidon.data.DataException if the column contains SQL {@code NULL} or cannot be read
         * @throws NullPointerException if the type is {@code null}
         * @throws IllegalArgumentException if the index or type is invalid
         * @throws IllegalStateException if the row is no longer active or the
         *                                  caller is not the callback thread
         */
        <T> T get(int index, Class<T> type);

        /**
         * Reads a value that must not be {@code null} by column label.
         *
         * @param label column label
         * @param type requested scalar type
         * @param <T> scalar type
         * @return value that is not {@code null}
         * @throws io.helidon.data.DataException if the label is absent or ambiguous,
         *                                          the column contains SQL {@code NULL},
         *                                          or the column cannot be read
         * @throws NullPointerException if the label or type is {@code null}
         * @throws IllegalArgumentException if the label or type is invalid
         * @throws IllegalStateException if the row is no longer active or the
         *                                  caller is not the callback thread
         */
        <T> T get(String label, Class<T> type);
    }
}
