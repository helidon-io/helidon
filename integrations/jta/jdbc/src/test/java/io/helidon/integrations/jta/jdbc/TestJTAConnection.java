/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.jta.jdbc;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.logging.Logger;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

final class TestJTAConnection {

    private static final Logger LOGGER = Logger.getLogger(TestJTAConnection.class.getName());

    private JdbcDataSource h2ds;

    private TransactionManager tm;

    private TestJTAConnection() throws SQLException {
        super();
    }

    @BeforeEach
    final void initializeH2DataSource() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;INIT=SET TRACE_LEVEL_FILE=4");
        ds.setUser("sa");
        ds.setPassword("sa");
        this.h2ds = ds;
    }

    @BeforeEach
    final void initializeTransactionManager() throws SystemException {
        this.tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
        this.tm.setTransactionTimeout(20 * 60); // 20 minutes for debugging
    }

    @AfterEach
    final void rollback() throws SQLException, SystemException {
        switch (this.tm.getStatus()) {
        case STATUS_NO_TRANSACTION:
            break;
        default:
            this.tm.rollback();
            break;
        }
        this.tm.setTransactionTimeout(0);
    }

    @DisplayName("Spike")
    @Test
    final void testSpike()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SQLException,
               SystemException {
        LOGGER.info("Starting testSpike()");
        tm.begin();

        try (Connection physicalConnection = h2ds.getConnection();
             Connection logicalConnection = JTAConnection.munge(tm, physicalConnection)) {


          // JTAConnection makes proxy connections.
          assertThat(logicalConnection, instanceOf(Proxy.class));

          // Trigger an Object method; make sure nothing blows up
          logicalConnection.toString();

          // Trigger harmless Connection method; make sure nothing blows up
          logicalConnection.getHoldability();

          try (Statement s = logicalConnection.createStatement()) {
            assertThat(s, not(nullValue()));
            assertThat(s, instanceOf(Proxy.class));
            assertThat(s.getConnection(), sameInstance(logicalConnection));
            try (ResultSet rs = s.executeQuery("SHOW TABLES")) {
              assertThat(rs, instanceOf(Proxy.class));
              assertThat(rs.getStatement(), sameInstance(s));
            }
          }

          tm.commit();
        }

        LOGGER.info("Ending testSpike()");
    }

}
