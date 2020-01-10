/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

class TransactionalConnectionPinningDataSource extends ConnectionPinningDataSource {

    private boolean oldAutoCommit;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link TransactionalConnectionPinningDataSource}.
     *
     * @param connectionSupplier a {@link ConnectionSupplier}; must
     * not be {@code null}
     *
     * @exception SQLException if an error occurs
     *
     * @exception NullPointerException if {@code connectionSupplier}
     * is {@code null}
     */
    TransactionalConnectionPinningDataSource(final ConnectionSupplier connectionSupplier) throws SQLException {
        super(connectionSupplier);
    }


    /*
     * Instance methods.
     */


    /**
     * @exception SQLException if an error occurs
     *
     * @exception NullPointerException if {@code connection} is {@code null}
     */
    @Override
    protected ConditionallyCloseableConnection configureConnection(ConditionallyCloseableConnection connection)
        throws SQLException {
        connection = super.configureConnection(connection);
        final boolean oldAutoCommit = connection.getAutoCommit();
        if (oldAutoCommit) {
            connection.setAutoCommit(false);
        }
        this.oldAutoCommit = oldAutoCommit;
        return connection;
    }

    /**
     * @exception SQLException if an error occurs
     *
     * @exception NullPointerException if {@code connection} is {@code null}
     */
    @Override
    protected void resetConnection(final Connection connection) throws SQLException {
        super.resetConnection(connection);
        connection.setAutoCommit(this.oldAutoCommit);
    }

    final void rollback() throws SQLException {
        final Connection connection = this.getPinnedConnection();
        if (connection != null) {
            connection.rollback();
        }
    }

    final void commit() throws SQLException {
        final Connection connection = this.getPinnedConnection();
        if (connection != null) {
            connection.commit();
        }
    }

}
