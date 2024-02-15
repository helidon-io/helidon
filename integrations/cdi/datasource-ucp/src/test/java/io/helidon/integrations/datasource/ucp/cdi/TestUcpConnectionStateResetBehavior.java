/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.datasource.ucp.cdi;

import java.sql.Connection;
import java.sql.SQLException;

import oracle.ucp.jdbc.ConnectionInitializationCallback;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A test class that shows that the Universal Connection Pool does not reset connection state when a borrowed connection
 * is returned.
 */
class TestUcpConnectionStateResetBehavior {

    private PoolDataSource pds;

    private TestUcpConnectionStateResetBehavior() {
        super();
    }

    @BeforeEach
    void initializeDataSource() throws SQLException {
        this.pds = PoolDataSourceFactory.getPoolDataSource();

        // We register this callback, knowing that UCP will ignore it, despite documentation that does not say so. It
        // ignores it because the connection factory (DataSource) we use is H2, not Oracle. In case UCP fixes this
        // shortcoming we want to know about it.
        this.pds.registerConnectionInitializationCallback(new FailingCallback());
        assertThat(this.pds.getConnectionInitializationCallback(), is(instanceOf(FailingCallback.class)));

        this.pds.setConnectionFactoryClassName("org.h2.jdbcx.JdbcDataSource");
        this.pds.setURL("jdbc:h2:mem:" + this.getClass().getSimpleName());
        this.pds.setUser("sa");
        this.pds.setPassword("");

        this.pds.setInitialPoolSize(2);
        this.pds.setMinPoolSize(2);
        this.pds.setMaxPoolSize(8); // actually the default but just being explicit
    }

    @AfterEach
    void destroyDataSource() throws SQLException {
        this.pds.unregisterConnectionInitializationCallback();
    }

    @Test
    void testUCPErroneouslyDoesNotResetConnectionStateOnReturn() throws SQLException {

        // The connection pool has two connections in it.

        // Borrow connection zero.
        Connection c0 = this.pds.getConnection();
        assertThat(c0.getAutoCommit(), is(true)); // we never set it anywhere; default value is true

        // Borrow connection one.
        Connection c1 = this.pds.getConnection();
        assertThat(c1.getAutoCommit(), is(true)); // we never set it anywhere; default value is true

        // There are now no more connections available in the pool.
        assertThat(this.pds.getAvailableConnectionsCount(), is(0));

        // Change the state of connection one.
        c1.setAutoCommit(false);

        // Return it to the pool.
        c1.close();

        // There is now exactly one connection available in the pool (the one we just returned).
        assertThat(this.pds.getAvailableConnectionsCount(), is(1));

        // Borrow connection two. (It will be connection one.)
        Connection c2 = this.pds.getConnection();

        // There are now no more connections available in the pool.
        assertThat(this.pds.getAvailableConnectionsCount(), is(0));

        // Note that the state of connection two includes the state of connection one. Oops. The pool really should
        // reset at least this portion of the connection state, but it does not.
        assertThat(c2.getAutoCommit(), is(false));

        // Return it.
        c2.close();

        // Return connection zero.
        c0.close();

        // No more borrowed connections are extant.
        assertThat(this.pds.getAvailableConnectionsCount(), is(2));
    }

    private static class FailingCallback implements ConnectionInitializationCallback {

        private FailingCallback() {
            super();
        }

        @Override
        public void initialize(Connection connection) throws SQLException {
            // ConnectionInitializationCallbacks are ignored by the UCP unless you are using an Oracle JDBC driver.
            fail();
        }

    }

}
