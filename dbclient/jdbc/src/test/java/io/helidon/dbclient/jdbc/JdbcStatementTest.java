/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.dbclient.jdbc;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.DbStatementContext;
import io.helidon.dbclient.jdbc.SqlPreparedStatementMock.ParInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * JDBC statement tests
 */
public class JdbcStatementTest {

    /**
     * Issue #2599: Named parameters should be usable more than once in a statement.
     * Verify parameters order returned by parser.
     * Verify how parameters were set in PreparedStatement.
     */
    @Test
    void testMultipleNamedParameterUsage() {
        // Data mockup
        Connection conn = new SqlConnectionMock();
        CompletableFuture<Connection> connFuture = new CompletableFuture<>();
        connFuture.complete(conn);
        Map<String, Object> params = new HashMap<>(3);
        params.put("name", "Name");
        // Let's try other tham Integer type
        params.put("count", (short) 5);
        params.put("id", 12);
        JdbcExecuteContext execCtx = JdbcExecuteContext.jdbcBuilder()
                .dbType("Test")
                .connection(connFuture)
                .build();
        DbStatementContext stmtCtx = DbStatementContext.builder()
                .statementName("test")
                .statementType(DbStatementType.UPDATE)
                .statementText("UPDATE TestTable SET name1=:name, name2=:name, count1=:count, count2=:count, count3=:count WHERE id=:id")
                .build();
        JdbcStatementDml dml = new JdbcStatementDml(execCtx, stmtCtx);
        DbClientServiceContext dbContext = DbClientServiceContext.create(dml.dbType());
        dbContext.statement(stmtCtx.statement(), params);
        // Contains statement params setting info.
        JdbcStatement.Parser parser = new JdbcStatement.Parser(stmtCtx.statement());
        String jdbcStatement = parser.convert();
        // Parsed order of params.
        List<String> namesOrder = parser.namesOrder();
        // Verify that parsed names order matches DML statement
        assertThat(namesOrder.get(0), equalTo("name"));
        assertThat(namesOrder.get(1), equalTo("name"));
        assertThat(namesOrder.get(2), equalTo("count"));
        assertThat(namesOrder.get(3), equalTo("count"));
        assertThat(namesOrder.get(4), equalTo("count"));
        assertThat(namesOrder.get(5), equalTo("id"));
        // Build statement mockup from statement context with multiple parameters
        // It shall not fail when Issue #2599 is fixed
        SqlPreparedStatementMock stmt = (SqlPreparedStatementMock)dml.build(conn, dbContext);
        // Verify parameters set in statement mockup
        Map<Integer, SqlPreparedStatementMock.ParInfo> stmtParams = stmt.params();
        // Parameters count shall be 6
        assertThat(stmtParams.size(), equalTo(6));
        // 1st assignment name1=:name
        final ParInfo info1 = stmtParams.get(1);
        assertThat(info1.value(), equalTo("Name"));
        assertThat(info1.cls(), equalTo(String.class));
        // 2nd assignment name2=:name
        final ParInfo info2 = stmtParams.get(2);
        assertThat(info2.value(), equalTo("Name"));
        assertThat(info2.cls(), equalTo(String.class));
        // 3rd assignment count1=:count
        final ParInfo info3 = stmtParams.get(3);
        assertThat(info3.value(), equalTo((short) 5));
        assertThat(info3.cls(), equalTo(Short.class));
        // 4th assignment count2=:count
        final ParInfo info4 = stmtParams.get(4);
        assertThat(info4.value(), equalTo((short) 5));
        assertThat(info4.cls(), equalTo(Short.class));
        // 5th assignment count3=:count
        final ParInfo info5 = stmtParams.get(5);
        assertThat(info5.value(), equalTo((short) 5));
        assertThat(info5.cls(), equalTo(Short.class));
        // 6th assignment id=:id
        final ParInfo info6 = stmtParams.get(6);
        assertThat(info6.value(), equalTo(12));
        assertThat(info6.cls(), equalTo(Integer.class));
    }

}
