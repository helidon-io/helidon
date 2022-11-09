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

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;

/**
 * A {@link SimplePooledConnection} and a very special-purpose {@link
 * XAConnection} implementation that adapts a non-XA-compliant {@link
 * Connection} to the {@link XAConnection} (and hence {@link
 * javax.sql.PooledConnection}) interface.
 *
 * @see LocalXAResource
 *
 * @see XAConnection
 *
 * @see XAResource
 */
public final class LocalXAConnection extends SimplePooledConnection implements XAConnection {


    /*
     * Instance fields.
     */


    private final LocalXAResource xaResource;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link LocalXAConnection}.
     *
     * @param physicalConnection a {@link Connection}; must not be
     * {@code null} and must not be {@linkplain Connection#isClosed()
     * closed}
     *
     * @exception NullPointerException if {@code physicalConnection}
     * is {@code null}
     *
     * @exception SQLException if {@code physicalConnection} is
     * {@linkplain Connection#isClosed() closed}
     */
    public LocalXAConnection(Connection physicalConnection) throws SQLException {
        super(physicalConnection);
        this.xaResource = new LocalXAResource(xid -> physicalConnection);
    }


    /*
     * Instance methods.
     */


    /**
     * Returns the {@link XAResource} affiliated with this {@link
     * LocalXAConnection}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return the {@link XAResource} affiliated with this {@link
     * LocalXAConnection}; never {@code null}
     *
     * @see LocalXAResource
     */
    @Override // XAResource
    public XAResource getXAResource() {
        return this.xaResource;
    }

}
