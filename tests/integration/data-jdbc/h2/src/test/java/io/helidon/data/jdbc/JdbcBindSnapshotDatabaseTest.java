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

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcBindSnapshotDatabaseTest {

    /**
     * Proves mutable binary input is snapshotted before execution and binary
     * output remains detached from JDBC resources after materialization.
     */
    @Test
    void binaryResultsRemainDetachedAfterMaterialization() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:jdbc_bind_snapshot;DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS BINARY_VALUE");
            statement.execute("CREATE TABLE BINARY_VALUE (ID INTEGER PRIMARY KEY, DATA_VALUE VARBINARY(20))");
        }
        JdbcClient client = JdbcTestClients.create(dataSource);
        byte[] source = {7, 8, 9};
        JdbcClient.Statement insert = client.create("INSERT INTO BINARY_VALUE(ID, DATA_VALUE) VALUES (1, ?)")
                .bind(1, source);
        source[0] = 1;
        insert.execute();

        byte[] first = client.create("SELECT DATA_VALUE FROM BINARY_VALUE WHERE ID = 1")
                .map(byte[].class)
                .one();
        first[0] = 2;
        byte[] second = client.create("SELECT DATA_VALUE FROM BINARY_VALUE WHERE ID = 1")
                .map(byte[].class)
                .one();

        assertThat(second, is(new byte[] {7, 8, 9}));
        assertThat(second, not(sameInstance(first)));
    }
}
