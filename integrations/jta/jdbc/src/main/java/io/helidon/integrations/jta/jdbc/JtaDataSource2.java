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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;

import io.helidon.integrations.jdbc.AbstractDataSource;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * An {@link AbstractDataSource} that wraps another {@link DataSource} that is known to not behave correctly in the
 * presence of JTA transaction management, such as one supplied by any of several freely and commercially available
 * connection pools, and that makes such a non-JTA-aware {@link DataSource} behave as sensibly as possible in the
 * presence of a JTA-managed transaction.
 */
public final class JtaDataSource2 extends AbstractDataSource {

    private final DataSource ds;

    private final TransactionManager tm;

    private final TransactionSynchronizationRegistry tsr;

    private final SQLExceptionConverter sqlExceptionConverter;

    /**
     * Creates a new {@link JtaDataSource2}.
     *
     * @param tm a {@link TransactionManager}; must not be {@code null}
     *
     * @param tsr a {@link TransactionSynchronizationRegistry}; must not be {@code null}
     *
     * @param ds a {@link DataSource} that is not XA-compliant; must not be {@code null}
     *
     * @param sqlExceptionConverter a {@link SQLExceptionConverter}; may be {@code null} in which case a default
     * implementation will be used instead
     */
    // Undefined behavior if ds ends up supplying the return value of an invocation of XAConnection#getConnection().
    public JtaDataSource2(TransactionManager tm,
                          TransactionSynchronizationRegistry tsr,
                          SQLExceptionConverter sqlExceptionConverter,
                          DataSource ds) {
        super();
        this.ds = Objects.requireNonNull(ds, "ds");
        this.tm = Objects.requireNonNull(tm, "tm");
        this.tsr = Objects.requireNonNull(tsr, "tsr");
        this.sqlExceptionConverter = sqlExceptionConverter;
    }

    @Override // DataSource
    public Connection getConnection(String username, String password) throws SQLException {
        return JtaConnection.connection(this.tm::getTransaction,
                                        this.tsr,
                                        this.sqlExceptionConverter,
                                        this.ds.getConnection(username, password));
    }

    @Override // DataSource
    public Connection getConnection() throws SQLException {
        return JtaConnection.connection(this.tm::getTransaction,
                                        this.tsr,
                                        this.sqlExceptionConverter,
                                        this.ds.getConnection());
    }

}
