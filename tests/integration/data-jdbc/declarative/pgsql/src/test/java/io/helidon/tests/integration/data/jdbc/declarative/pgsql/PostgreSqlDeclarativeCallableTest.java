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
package io.helidon.tests.integration.data.jdbc.declarative.pgsql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.JDBCType;
import java.sql.ResultSet;

import io.helidon.config.Config;
import io.helidon.tests.integration.data.jdbc.contract.AbstractJdbcCallableContract;

import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.PgResultSet;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlDeclarativeCallableTest extends AbstractJdbcCallableContract {
    @Container
    static final GenericContainer<?> DATABASE = PostgreSqlDeclarativeTestSupport.POSTGRESQL;

    @Override
    protected Config configuration() {
        return PostgreSqlDeclarativeTestSupport.config();
    }

    @Override
    protected String vendor() {
        return "pgsql";
    }

    @Override
    protected boolean generated() {
        return true;
    }

    /**
     * Callable refcursors are buffered independently of the ordinary query fetch mode.
     */
    @Test
    void verifiesCallableRefcursorBuffering() throws Exception {
        Config config = configuration().get("data.clients.jdbc.0.connection");
        try (Connection connection = DriverManager.getConnection(config.get("url").asString().get()
                + "?escapeSyntaxCallMode=callIfNoReturn", config.get("username").asString().get(),
                config.get("password").asString().get())) {
            connection.setAutoCommit(false);
            try {
                try (var setup = connection.createStatement()) {
                    setup.execute("CREATE OR REPLACE FUNCTION sp_cursor_buffered() RETURNS refcursor LANGUAGE plpgsql "
                            + "AS $$ DECLARE c refcursor; BEGIN OPEN c FOR SELECT generate_series(1, 64); RETURN c; END $$");
                }
                for (int fetchSize : new int[] {1, 16}) {
                    try (var statement = connection.prepareCall("{? = call sp_cursor_buffered()}")) {
                        statement.setFetchSize(fetchSize);
                        statement.registerOutParameter(1, JDBCType.REF_CURSOR.getVendorTypeNumber());
                        statement.execute();
                        try (ResultSet rows = (ResultSet) statement.getObject(1)) {
                            PgResultSet driverRows = rows.unwrap(PgResultSet.class);
                            assertThat(rows.getFetchSize(), is(0));
                            // The driver has fetched all 64 rows before application traversal begins.
                            assertThat(driverRows.getLastUsedFetchSize(), is(64));
                            int count = 0;
                            while (rows.next()) {
                                assertThat(rows.getInt(1), is(++count));
                            }
                            assertThat(count, is(64));
                            assertThat(driverRows.getLastUsedFetchSize(), is(64));
                            System.out.println("Callable refcursor buffering: requested fetch size " + fetchSize
                                    + ", result fetch size " + rows.getFetchSize() + ", fetched rows "
                                    + driverRows.getLastUsedFetchSize() + ", consumed rows " + count + ".");
                        }
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }

}
