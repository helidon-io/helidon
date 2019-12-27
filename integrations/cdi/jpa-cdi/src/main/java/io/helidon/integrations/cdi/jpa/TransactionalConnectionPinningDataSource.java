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

import javax.enterprise.event.Observes;

class TransactionalConnectionPinningDataSource extends ConnectionPinningDataSource {

    private boolean oldAutoCommit;
    
    TransactionalConnectionPinningDataSource(final ConnectionSupplier connectionSource) throws SQLException {
        super(connectionSource);
    }

    @Override
    protected void configureConnection(final Connection connection) throws SQLException {
        super.configureConnection(connection);
        this.oldAutoCommit = connection.getAutoCommit();
        if (this.oldAutoCommit) {
            connection.setAutoCommit(false);
        }
    }

    @Override
    protected void resetConnection(final Connection connection) throws SQLException {
        super.resetConnection(connection);
        if (this.oldAutoCommit) {
            connection.setAutoCommit(true);
        }
    }
    
}
