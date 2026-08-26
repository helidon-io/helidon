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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("helidon:api:internal")
class JdbcClientGeneratedBridgeTest {

    @Test
    void generatedCreationFallsBackToTheSupportedAlternateProviderContract() {
        AlternateClient client = new AlternateClient();

        JdbcClient.Statement result = JdbcClient.createGenerated(client, "select ?", 1);

        assertSame(client.statement, result);
        assertEquals("select ?", client.sql);
    }

    @Test
    void typedNullDoesNotExpandTheAlternateStatementContract() {
        AlternateStatement statement = new AlternateStatement();

        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
                                                              () -> JdbcClient.bindNull(statement,
                                                                                        1,
                                                                                        JDBCType.VARCHAR));

        assertEquals("Typed SQL null binding requires a statement created by Helidon's JDBC provider.",
                     failure.getMessage());
    }

    private static final class AlternateClient implements JdbcClient {
        private final AlternateStatement statement = new AlternateStatement();

        private String sql;

        @Override
        public Statement create(String sql) {
            this.sql = sql;
            return statement;
        }
    }

    private static final class AlternateStatement implements JdbcClient.Statement {

        @Override
        public JdbcClient.Statement bind(int index, Object value) {
            return this;
        }

        @Override
        public long execute() {
            return 0;
        }

        @Override
        public <T> JdbcClient.Rows<T> map(JdbcClient.RowMapper<T> mapper) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> JdbcClient.Rows<T> map(Class<T> scalarType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JdbcClient.GeneratedKeys generatedKeys() {
            throw new UnsupportedOperationException();
        }
    }
}
