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
package io.helidon.tests.integration.data.jdbc.declarative.repository;

import java.sql.JDBCType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;

@Data.Repository
@Data.Provider("jdbc")
public interface CallableRoutineRepository extends InferredCallableParent<Integer> {
    @Jdbc.Statement("{? = call sp_add(:jdbcClient)}")
    @Jdbc.OutParameter(type = JDBCType.INTEGER)
    int scalar(Integer jdbcClient);

    @Jdbc.Statement("{? = call sp_add(:value)}")
    @Jdbc.OutParameter(type = JDBCType.INTEGER)
    Optional<Integer> nullable(Integer value);

    @Jdbc.Statement("{call sp_values(:input, :value, :other)}")
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    @Jdbc.OutParameter(value = "other", type = JDBCType.VARCHAR)
    Values aggregate(Integer input, Integer value, String other);

    @Jdbc.Statement("{call sp_values(:input, :value, :other)}")
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    @Jdbc.OutParameter(value = "other", type = JDBCType.VARCHAR)
    JdbcClient.OutParameters snapshot(Integer input, Integer value, String other);

    @Jdbc.Statement("{call sp_values(:input, :value, :other)}")
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    @Jdbc.OutParameter(value = "other", type = JDBCType.VARCHAR)
    void consume(Integer input, Integer value, String other, Consumer<JdbcClient.CallResult> result);

    @Jdbc.Statement("{? = call sp_cursor(:input)}")
    @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
    List<Integer> cursor(int input);

    @Jdbc.Statement("{? = call sp_cursor(:input)}")
    @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
    Optional<List<Integer>> optionalCursor(int input);

    @Jdbc.Statement("{? = call sp_cursor(:input)}")
    @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
    void consumeCursor(int input, Consumer<JdbcClient.CallResult> rows);

    @Jdbc.Statement("{call sp_direct()}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    void consumeDirect(Consumer<JdbcClient.CallResult> result);

    @Jdbc.Statement("{call sp_pair(:first, :second, :value)}")
    @Jdbc.OutParameter(value = "first", type = JDBCType.REF_CURSOR)
    @Jdbc.OutParameter(value = "second", type = JDBCType.REF_CURSOR)
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    void consumePair(Consumer<JdbcClient.CallResult> statement);

    @Jdbc.Statement("{call sp_inout(:input, :value)}")
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    int inout(int input, int value);

    @Jdbc.Statement("{call sp_direct()}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    int direct();

    @Jdbc.Statement("{call sp_noop()}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    int noDirect();

    @Jdbc.Statement("{call sp_bundle(:input, :value, :rows)}")
    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    Bundle aggregateCursor(int input);

    @Jdbc.Statement("{call sp_bundle(:input, :value, :rows)}")
    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    OptionalBundle aggregateOptionalCursor(int input);

    @Jdbc.Statement("{? = call sp_cursor(:input)}")
    @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
    int cursorOne(int input);

    @Jdbc.Statement("{? = call sp_cursor(:input)}")
    @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
    Optional<Integer> cursorOptional(int input);

    @Jdbc.Statement("{? = call sp_add(:input)}")
    @Jdbc.OutParameter(type = JDBCType.INTEGER)
    String mapped(Integer input, JdbcClient.CallMapper<String> mapper);

    @Jdbc.Statement("{call sp_bundle(:MAPPER_RECORD_BUNDLE_OUTPUT_0_2, :value, :rows)}")
    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    RecordBundle recordBundle(int MAPPER_RECORD_BUNDLE_OUTPUT_0_2);

    @Jdbc.Statement("SELECT DATA_VALUE AS label FROM TX_MATRIX WHERE DATA_VALUE = :input")
    QueryProjection recordBundle_OUTPUT_0(String input);

    @Jdbc.Statement("{? = call sp_add(:input)}")
    @Jdbc.OutParameter
    Optional<Integer> inferredNullable(Integer input);

    @Jdbc.Statement("{call sp_inout(:input, :value)}")
    @Jdbc.OutParameter("value")
    Optional<Integer> inferredInout(int input, Integer value);

    @Jdbc.Statement("{? = call sp_decimal(:input)}")
    @Jdbc.OutParameter(scale = 2)
    BigDecimal inferredDecimal(Integer input);

    @Jdbc.Statement("{call sp_values(:input, :value, :other)}")
    @Jdbc.OutParameter(value = "other")
    @Jdbc.OutParameter(value = "value")
    InferredValues inferredAggregate(Integer input, Integer value, String other);

    @Jdbc.Statement("{? = call sp_cursor(:input)}")
    @Jdbc.OutParameter
    List<Projection> inferredCursor(int input);

    @Jdbc.Statement("{? = call sp_cursor(:input)}")
    @Jdbc.OutParameter
    Optional<List<Projection>> inferredOptionalCursor(int input);

    @Jdbc.Statement("{? = call sp_cursor(:input)}")
    @Jdbc.OutParameter
    RejectedProjection inferredMappingFailure(int input);

    @Jdbc.Statement("{? = call sp_add(:input)}")
    @Jdbc.OutParameter
    List<Projection> inferredUnsupportedCursor(int input);

    @Jdbc.Statement("{call sp_bundle(:input, :value, :rows)}")
    @Jdbc.OutParameter(value = "rows")
    @Jdbc.OutParameter(value = "value")
    InferredBundle inferredBundle(int input);

    @Jdbc.Statement("{call sp_bundle(:input, :value, :rows)}")
    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    Bundle mappedBundle(int input, JdbcClient.CallMapper<Bundle> mapper);

    @Jdbc.Statement("{call sp_pair(:first, :second, :value)}")
    @Jdbc.OutParameter("value")
    @Jdbc.OutParameter("first")
    @Jdbc.OutParameter("second")
    Pair pair();

    @Jdbc.Statement("{? = call sp_sum(:input, :input)}")
    @Jdbc.OutParameter
    Optional<Integer> aliasedRepeated(@Data.Param("input") Integer renamed);

    @Jdbc.Statement("{call sp_inout(:input, :value)}")
    @Jdbc.OutParameter("value")
    Optional<Integer> aliasedInout(@Data.Param("input") int renamed, @Data.Param("value") Integer seed);

    @Jdbc.Statement("${application.calls.command:{call sp_noop()}}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    void configuredCommand();

    @Jdbc.Statement("${application.calls.input:{call sp_write(:input)}}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    void configuredInput(String input);

    @Jdbc.Statement("${application.calls.output:{call sp_out(:value)}}")
    @Jdbc.OutParameter("value")
    int configuredOutput();

    @Jdbc.Statement("${application.calls.values:{call sp_values(:input, :value, :other)}}")
    @Jdbc.OutParameter("other")
    @Jdbc.OutParameter("value")
    ConfiguredValues configuredValues(String other, Integer value, Integer input);

    @Jdbc.Statement("${application.calls.inout:{call sp_inout(:input, :value)}}")
    @Jdbc.OutParameter("value")
    Optional<Integer> configuredInout(Integer value, int input);

    @Jdbc.Statement("${application.calls.aliased-inout:{call sp_inout(:input, :value)}}")
    @Jdbc.OutParameter("value")
    Optional<Integer> configuredAliasedInout(@Data.Param("value") Integer seed, @Data.Param("input") int renamed);

    @Jdbc.Statement("${application.calls.total:{? = call sp_add(?)}}")
    @Jdbc.OutParameter
    Optional<Integer> configuredTotal(Integer input);

    @Jdbc.Statement("${application.calls.ordinary-return:{call sp_inout(:input, :return)}}")
    @Jdbc.OutParameter("return")
    Optional<Integer> configuredOrdinaryReturn(@Data.Param("input") int input, @Data.Param("return") Integer seed);

    @Jdbc.Statement("{call sp_form_procedure(?)}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    void formProcedure(int input);

    @Jdbc.Statement("{? = call sp_form_procedure(?)}")
    @Jdbc.OutParameter
    int formProcedureReturn(int input);

    @Jdbc.Statement("{call sp_add(?)}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    int formFunction(int input);

    @Jdbc.Statement("{? = call sp_add(?)}")
    @Jdbc.OutParameter
    int formFunctionReturn(int input);

    @Jdbc.Statement("{? = call sp_add(?)}")
    @Jdbc.OutParameter(type = JDBCType.INTEGER)
    void discardFormReturn(int input);

    @Jdbc.Statement("${application.calls.form-procedure:{call sp_form_procedure(?)}}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    void configuredFormProcedure(int input);

    @Jdbc.Statement("${application.calls.form-procedure-return:{? = call sp_form_procedure(?)}}")
    @Jdbc.OutParameter
    int configuredFormProcedureReturn(int input);

    @Jdbc.Statement("${application.calls.form-function:{call sp_add(?)}}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    int configuredFormFunction(int input);

    @Jdbc.Statement("${application.calls.form-function-return:{? = call sp_add(?)}}")
    @Jdbc.OutParameter
    int configuredFormFunctionReturn(int input);

    @Jdbc.Statement("${application.calls.form-discard:{? = call sp_add(?)}}")
    @Jdbc.OutParameter(type = JDBCType.INTEGER)
    void configuredDiscardFormReturn(int input);

    @Jdbc.Statement("{? = call sp_stamp()}")
    @Jdbc.OutParameter(type = JDBCType.TIMESTAMP)
    JdbcClient.OutParameters stamp();

    @Jdbc.Statement("${application.calls.stamp:{? = call sp_stamp()}}")
    @Jdbc.OutParameter(type = JDBCType.TIMESTAMP)
    JdbcClient.OutParameters configuredStamp();

    @Jdbc.Statement("${application.calls.repeated:{? = call sp_sum(:input, :input)}}")
    @Jdbc.OutParameter
    Optional<Integer> configuredRepeated(@Data.Param("input") Integer renamed);

    @Jdbc.Statement("${application.calls.cursor:{? = call sp_cursor(?)}}")
    @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
    Optional<List<Integer>> configuredCursor(int input);

    @Jdbc.Statement("${application.calls.cursor:{? = call sp_cursor(?)}}")
    @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
    void configuredCursorCallback(int input, Consumer<JdbcClient.CallResult> action);

    @Jdbc.Statement("${application.calls.direct:{call sp_direct()}}")
    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
    void configuredDirect(Consumer<JdbcClient.CallResult> action);

    @Jdbc.Statement("${application.calls.bundle:{call sp_bundle(:input, :value, :rows)}}")
    @Jdbc.OutParameter("value")
    @Jdbc.OutParameter("rows")
    InferredBundle configuredBundle(int input);

    @Jdbc.Statement("${application.calls.bundle:{call sp_bundle(:input, :value, :rows)}}")
    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
    Bundle configuredMappedBundle(int input, JdbcClient.CallMapper<Bundle> mapper);

    @Jdbc.Statement("${application.calls.leading-outputs:{call sp_values_reordered(:other, :value, :input)}}")
    @Jdbc.OutParameter("value")
    @Jdbc.OutParameter("other")
    ConfiguredValues configuredLeadingOutputs(Integer input, String other, Integer value);

    @Jdbc.Statement("{? = call sp_snap_boolean(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.BOOLEAN)
    JdbcClient.OutParameters snapshotBoolean(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_bit(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.BIT)
    JdbcClient.OutParameters snapshotBit(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_tinyint(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.TINYINT)
    JdbcClient.OutParameters snapshotTinyint(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_smallint(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.SMALLINT)
    JdbcClient.OutParameters snapshotSmallint(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_integer(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.INTEGER)
    JdbcClient.OutParameters snapshotInteger(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_bigint(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.BIGINT)
    JdbcClient.OutParameters snapshotBigint(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_real(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.REAL)
    JdbcClient.OutParameters snapshotReal(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_float(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.FLOAT)
    JdbcClient.OutParameters snapshotFloat(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_double(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.DOUBLE)
    JdbcClient.OutParameters snapshotDouble(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_numeric(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.NUMERIC)
    JdbcClient.OutParameters snapshotNumeric(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_decimal(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.DECIMAL)
    JdbcClient.OutParameters snapshotDecimal(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_char(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.CHAR)
    JdbcClient.OutParameters snapshotChar(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_varchar(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.VARCHAR)
    JdbcClient.OutParameters snapshotVarchar(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_longvarchar(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.LONGVARCHAR)
    JdbcClient.OutParameters snapshotLongvarchar(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_nchar(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.NCHAR)
    JdbcClient.OutParameters snapshotNchar(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_nvarchar(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.NVARCHAR)
    JdbcClient.OutParameters snapshotNvarchar(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_longnvarchar(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.LONGNVARCHAR)
    JdbcClient.OutParameters snapshotLongnvarchar(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_binary(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.BINARY)
    JdbcClient.OutParameters snapshotBinary(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_varbinary(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.VARBINARY)
    JdbcClient.OutParameters snapshotVarbinary(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_longvarbinary(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.LONGVARBINARY)
    JdbcClient.OutParameters snapshotLongvarbinary(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_date(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.DATE)
    JdbcClient.OutParameters snapshotDate(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_time(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.TIME)
    JdbcClient.OutParameters snapshotTime(int nullMode);

    @Jdbc.Statement("{? = call sp_snap_timestamp(:nullMode)}")
    @Jdbc.OutParameter(type = JDBCType.TIMESTAMP)
    JdbcClient.OutParameters snapshotTimestamp(int nullMode);

    record ConfiguredValues(String other, int value) {
    }

    record Pair(List<Projection> second, int value, List<Projection> first) {
    }

    record InferredValues(int value, String other) {
    }

    record InferredBundle(Optional<List<Projection>> rows, int value) {
    }

    record RejectedProjection(int id) {
        public RejectedProjection {
            throw new IllegalArgumentException("The test rejects the mapped cursor row.");
        }
    }

    record RecordBundle(List<Projection> rows, int value) {
    }

    record Projection(int id) {
    }

    record QueryProjection(String label) {
    }

    record OptionalBundle(Optional<List<Integer>> rows, int value) {
    }

    record Bundle(List<Integer> rows, int value) {
    }

    @Service.Singleton
    class IntegerMapper implements JdbcClient.RowMapper<Integer> {
        @Override
        public Integer map(JdbcClient.Row row) {
            Objects.requireNonNull(row, "The current row must not be null.");
            return row.get(1, Integer.class);
        }
    }

    record Values(int value, String other) {
    }
}

interface InferredCallableParent<T> {
    @Jdbc.Statement("{? = call sp_add(:input)}")
    @Jdbc.OutParameter
    T inferredScalar(T input);

    @Jdbc.Statement("${application.calls.inherited-total:{? = call sp_add(:input)}}")
    @Jdbc.OutParameter
    T inheritedConfiguredTotal(@Data.Param("input") T value);
}
