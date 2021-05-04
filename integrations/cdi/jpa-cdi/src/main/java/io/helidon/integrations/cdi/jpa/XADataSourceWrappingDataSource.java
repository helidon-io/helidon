/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.jpa;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.enterprise.inject.Vetoed;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

@Vetoed
class XADataSourceWrappingDataSource extends AbstractDataSource {

    private final XADataSource xaDataSource;

    private final String dataSourceName;

    private final TransactionManager tm;

    XADataSourceWrappingDataSource(final XADataSource xaDataSource,
                                   final String dataSourceName,
                                   final TransactionManager transactionManager) {
        super();
        this.xaDataSource = Objects.requireNonNull(xaDataSource);
        this.dataSourceName = dataSourceName;
        this.tm = Objects.requireNonNull(transactionManager);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.getConnection(null, null, true);
    }

    @Override
    public Connection getConnection(final String username, final String password) throws SQLException {
        return this.getConnection(username, password, false);
    }

    private Connection getConnection(final String username,
                                     final String password,
                                     final boolean useZeroArgumentForm)
        throws SQLException {
        final XAConnection xaConnection;
        if (useZeroArgumentForm) {
            xaConnection = this.xaDataSource.getXAConnection();
        } else {
            xaConnection = this.xaDataSource.getXAConnection(username, password);
        }
        try {
            if (this.tm.getStatus() == Status.STATUS_ACTIVE) {
                final Transaction transaction = this.tm.getTransaction();
                assert transaction != null;
                transaction.enlistResource(xaConnection.getXAResource());
            }
        } catch (final RollbackException | SystemException exception) {
            throw new SQLException(exception.getMessage(), exception);
        }
        // I am not confident about this.  Note that the xaConnection
        // is not closed.  And yet, the end consumer knows nothing of
        // XAConnections so cannot close it herself.  So is
        // Connection#close() invoked on the return value of
        // XAConnection#getConnection() guaranteed to call through to
        // XAConnection#close()?  Using H2 as an arbitrary example, we
        // can see this is the case:
        // https://github.com/h2database/h2database/blob/12fcf4c219e26176d4027e72eb5f9f0c797f0152/h2/src/main/org/h2/jdbcx/JdbcXAConnection.java#L74-L89
        // Is that mandated anywhere?  I'm honestly not sure.
        return xaConnection.getConnection();
    }


}
