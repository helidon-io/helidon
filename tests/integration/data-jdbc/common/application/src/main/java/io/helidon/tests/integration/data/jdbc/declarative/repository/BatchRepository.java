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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.tests.integration.data.jdbc.parameters.BatchAliasStatements;

/**
 * Declares batches using Java names, positional bindings, and inherited parameter aliases.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface BatchRepository extends BatchAliasStatements<String> {
    @Jdbc.Statement("INSERT INTO CONTACT (NAME, EMAIL) VALUES (:rows, :statement)")
    JdbcClient.BatchResult insert(List<String> rows, String statement);

    @Jdbc.Statement("INSERT INTO CONTACT (NAME, EMAIL) VALUES (:rows, :statement)")
    @Jdbc.Execution(Jdbc.ExecutionType.AUTO)
    JdbcClient.BatchResult insertAuto(List<String> rows, String statement);

    @Jdbc.Statement("INSERT INTO CONTACT (NAME, EMAIL) VALUES (:rows, :statement)")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult insertExplicit(List<String> rows, String statement);

    @Jdbc.Statement("UPDATE CONTACT SET NAME = :name, EMAIL = :email WHERE ID = :id OR ID = :id")
    JdbcClient.BatchResult records(Iterable<Change> rows);

    @Jdbc.Statement("UPDATE CONTACT SET NAME = ?, EMAIL = ? WHERE ID = ?")
    @Jdbc.Execution(Jdbc.ExecutionType.AUTO)
    JdbcClient.BatchResult aligned(Iterator<String> rows, String statement, long... iterator);

    @Override
    @Jdbc.Statement("INSERT INTO CONTACT (NAME, EMAIL) VALUES (:rows, :email)")
    @Jdbc.Execution(Jdbc.ExecutionType.AUTO)
    JdbcClient.BatchResult insertAliased(List<String> SQL_INSERT_ALIASED, String jdbcClient);

    @Jdbc.Statement("UPDATE CONTACT SET NAME = :name, EMAIL = :email WHERE ID = :id OR ID = :id")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult aliasedRecords(@Data.Param("email") String rows, Iterable<Rename> bindings);

    @Jdbc.Statement("INSERT INTO CONTACT (NAME) VALUES (:rows)")
    JdbcClient.BatchResult insertIterable(Iterable<String> rows);

    @Jdbc.Statement("INSERT INTO CONTACT (NAME) VALUES (:rows)")
    @Jdbc.Execution(Jdbc.ExecutionType.AUTO)
    JdbcClient.BatchResult insertSet(Set<String> rows);

    @Jdbc.Statement("UPDATE CONTACT SET NAME = :name, EMAIL = :email WHERE ID = :id")
    JdbcClient.BatchResult recordIterator(Iterator<Change> rows);

    @Jdbc.Statement("UPDATE CONTACT SET NAME = ?, EMAIL = ? WHERE ID = ? AND NAME <> ? AND ID = ?")
    @Jdbc.Execution(Jdbc.ExecutionType.AUTO)
    JdbcClient.BatchResult alignedRecords(Iterable<Change> rows, Iterator<Rename> filters);

    @Jdbc.Statement("UPDATE CONTACT SET EMAIL = :emails WHERE ID = :ids")
    JdbcClient.BatchResult emailArrays(String[] emails, long... ids);

    @Jdbc.Statement("UPDATE CONTACT SET EMAIL = :emails WHERE ID = :ids")
    @Jdbc.Execution(Jdbc.ExecutionType.AUTO)
    JdbcClient.BatchResult emailIterable(Iterable<String> emails, long... ids);

    @Jdbc.Statement("""
            UPDATE SCALAR_VALUE
            SET BYTES_VALUE = :bytes,
                DECIMAL_VALUE = :decimal,
                LOCAL_DATE_VALUE = :date,
                LOCAL_TIME_VALUE = :time,
                LOCAL_DATE_TIME_VALUE = :dateTime,
                DATE_VALUE = :sqlDate,
                TIME_VALUE = :sqlTime,
                TIMESTAMP_VALUE = :sqlTimestamp
            WHERE ID = :id
            """)
    @Jdbc.Execution(Jdbc.ExecutionType.AUTO)
    JdbcClient.BatchResult scalarRecords(Iterable<ScalarChange> rows);

    @Jdbc.Statement("UPDATE SCALAR_VALUE SET BYTES_VALUE = :values WHERE ID = :id")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult binaryValues(Iterable<byte[]> values, long id);

    @Jdbc.Statement("UPDATE SCALAR_VALUE SET BYTES_VALUE = :value WHERE ID = :ids")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult binaryConstant(long[] ids, byte[] value);

    @Jdbc.Statement("UPDATE SCALAR_VALUE SET BYTE_VALUE = :values WHERE ID = 1")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    JdbcClient.BatchResult byteValues(byte... values);

    @Jdbc.Statement("${application.batches.insert:INSERT INTO CONTACT (NAME, EMAIL) VALUES (:name, :email)}")
    JdbcClient.BatchResult configuredInsert(Iterable<String> name, String email);

    @Jdbc.Statement("${application.batches.insert:INSERT INTO CONTACT (NAME, EMAIL) VALUES (:name, :email)}")
    @Jdbc.Execution(Jdbc.ExecutionType.AUTO)
    JdbcClient.BatchResult configuredAuto(Iterable<String> name, String email);

    @Jdbc.Statement("${application.batches.insert:INSERT INTO CONTACT (NAME, EMAIL) VALUES (:name, :email)}")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    void configuredVoid(Iterable<String> name, String email);

    @Jdbc.Statement("${application.batches.names:INSERT INTO CONTACT (NAME) VALUES (:name)}")
    JdbcClient.BatchResult configuredSet(Set<String> name);

    @Jdbc.Statement("${application.batches.records:UPDATE CONTACT SET NAME = :name, EMAIL = :email WHERE ID = :id}")
    JdbcClient.BatchResult configuredRecords(Iterator<Change> values);

    @Jdbc.Statement("${application.batches.arrays:UPDATE CONTACT SET EMAIL = :emails WHERE ID = :ids}")
    JdbcClient.BatchResult configuredArrays(String[] emails, long... ids);

    @Jdbc.Statement("${application.batches.duplicates:UPDATE CONTACT SET NAME = ? WHERE ID = ? AND NAME = ?}")
    JdbcClient.BatchResult configuredDuplicates(Iterable<Replacement> replacements, Iterator<Expected> expected);

    @Jdbc.Statement("${application.batches.aligned:UPDATE CONTACT SET NAME = ?, EMAIL = ? WHERE ID = ? AND NAME <> ? AND ID = ?}")
    JdbcClient.BatchResult configuredAligned(Iterable<Change> rows, Iterator<Rename> filters);

    @Jdbc.Statement("${application.batches.aliases:UPDATE CONTACT SET NAME = :name, EMAIL = :email WHERE ID = :id OR ID = :id}")
    JdbcClient.BatchResult configuredAliases(@Data.Param("name") Iterator<String> jdbcClient,
                                           @Data.Param("email") String SQL_CONFIGURED_ALIASES,
                                           @Data.Param("id") long... iterator);

    @Jdbc.Statement("${application.batches.bytes:UPDATE SCALAR_VALUE SET BYTES_VALUE = :values WHERE ID = :id}")
    JdbcClient.BatchResult configuredBinary(Iterable<byte[]> values, long id);

    @Jdbc.Statement("${application.batches.constant:UPDATE SCALAR_VALUE SET BYTES_VALUE = :value WHERE ID = :ids}")
    JdbcClient.BatchResult configuredBinaryConstant(long[] ids, byte[] value);

    @Jdbc.Statement("${application.batches.varargs:UPDATE SCALAR_VALUE SET BYTE_VALUE = :values WHERE ID = 1}")
    JdbcClient.BatchResult configuredBytes(byte... values);

    @Jdbc.Statement("""
            ${application.batches.scalars:UPDATE SCALAR_VALUE
            SET BYTES_VALUE = :bytes, DECIMAL_VALUE = :decimal, LOCAL_DATE_VALUE = :date, LOCAL_TIME_VALUE = :time,
                LOCAL_DATE_TIME_VALUE = :dateTime, DATE_VALUE = :sqlDate, TIME_VALUE = :sqlTime,
                TIMESTAMP_VALUE = :sqlTimestamp WHERE ID = :id}
            """)
    JdbcClient.BatchResult configuredScalars(Iterable<ScalarChange> values);

    /**
     * Requires a deployment key independently of methods with SQL defaults.
     */
    @Data.Repository
    @Data.Provider("jdbc")
    interface Required {
        @Jdbc.Statement("${application.batches.required}")
        JdbcClient.BatchResult insert(Iterable<String> name);
    }

    record Replacement(String name) {
    }

    record Expected(long id, String name) {
    }

    record Rename(String name, long id) {
    }

    record Change(String name, String email, long id) {
    }

    record ScalarChange(long id, byte[] bytes, BigDecimal decimal, LocalDate date, LocalTime time,
                        LocalDateTime dateTime, Date sqlDate, Time sqlTime, Timestamp sqlTimestamp) {
    }
}
