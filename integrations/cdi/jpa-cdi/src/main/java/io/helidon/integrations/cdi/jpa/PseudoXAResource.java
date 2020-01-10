/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

abstract class PseudoXAResource implements XAResource {

    private final Connection connection;

    private volatile Xid xid; // @GuardedBy("this")

    private volatile boolean originalAutoCommit; // @GuardedBy("this")

    protected PseudoXAResource(final Connection connection) {
        super();
        this.connection = Objects.requireNonNull(connection);
    }

    /**
     * Commits the transaction and restores the original auto commit setting.
     *
     * @param xid
     *            the id of the transaction branch for this connection
     * @param flag
     *            ignored
     * @throws XAException
     *             if connection.commit() throws a SQLException
     */
    @Override
    public void commit(final Xid xid, final boolean flag) throws XAException {
        Objects.requireNonNull(xid);

        final Xid myXid = this.xid;
        if (myXid == null) {
            throw new XAException("There is no current transaction");
        } else if (!myXid.equals(xid)) {
            throw new XAException("Invalid Xid: expected " + myXid + ", but was " + xid);
        }

        try {
            // make sure the connection isn't already closed
            if (connection.isClosed()) {
                throw new XAException("Connection is closed");
            }

            // A read only connection should not be committed
            if (!connection.isReadOnly()) {
                connection.commit();
            }
        } catch (final SQLException e) {
            throw (XAException) new XAException().initCause(e);
        } finally {
            try {
                connection.setAutoCommit(this.originalAutoCommit);
            } catch (final SQLException e) {
                // ignore
            }
            this.xid = null;
        }
    }

    /**
     * This method does nothing.
     *
     * @param xid
     *            the id of the transaction branch for this connection
     * @param flag
     *            ignored
     * @throws XAException
     *             if the connection is already enlisted in another transaction
     */
    @Override
    public void end(final Xid xid, final int flag) throws XAException {
        Objects.requireNonNull(xid);
        final Object myXid = this.xid;
        if (!myXid.equals(xid)) {
            throw new XAException("Invalid Xid: expected " + myXid + ", but was " + xid);
        }
        // This notification tells us that the application server is done using this
        // connection for the time being. The connection is still associated with an
        // open transaction, so we must still wait for the commit or rollback method
    }

    /**
     * Clears the currently associated transaction if it is the specified xid.
     *
     * @param xid
     *            the id of the transaction to forget
     */
    @Override
    public void forget(final Xid xid) {
        if (xid != null) {
            synchronized (this) {
                if (xid.equals(this.xid)) {
                    this.xid = null;
                }
            }
        }
    }

    /**
     * Always returns 0 since we have no way to set a transaction timeout on a JDBC connection.
     *
     * @return always 0
     */
    @Override
    public int getTransactionTimeout() {
        return 0;
    }

    /**
     * Gets the current xid of the transaction branch associated with this XAResource.
     *
     * @return the current xid of the transaction branch associated with this XAResource.
     */
    public Xid getXid() {
        return this.xid;
    }

    /**
     * Returns true if the specified XAResource == this XAResource.
     *
     * @param xaResource
     *            the XAResource to test
     * @return true if the specified XAResource == this XAResource; false otherwise
     */
    @Override
    public boolean isSameRM(final XAResource xaResource) {
        return this == xaResource;
    }

    /**
     * This method does nothing since the LocalXAConnection does not support two-phase-commit. This method will
     * return XAResource.XA_RDONLY if the connection isReadOnly(). This assumes that the physical connection is
     * wrapped with a proxy that prevents an application from changing the read-only flag while enrolled in a
     * transaction.
     *
     * @param xid
     *            the id of the transaction branch for this connection
     * @return XAResource.XA_RDONLY if the connection.isReadOnly(); XAResource.XA_OK otherwise
     */
    @Override
    public synchronized int prepare(final Xid xid) {
        // if the connection is read-only, then the resource is read-only
        // NOTE: this assumes that the outer proxy throws an exception when application code
        // attempts to set this in a transaction
        try {
            if (this.connection.isReadOnly()) {
                // update the auto commit flag
                this.connection.setAutoCommit(this.originalAutoCommit);

                // tell the transaction manager we are read only
                return XAResource.XA_RDONLY;
            }
        } catch (final SQLException ignored) {
            // no big deal
        }
        // this is a local (one phase) only connection, so we can't prepare
        return XAResource.XA_OK;
    }

    /**
     * Always returns a zero length Xid array. The LocalXAConnectionFactory can not support recovery, so no xids
     * will ever be found.
     *
     * @param flag
     *            ignored since recovery is not supported
     * @return always a zero length Xid array.
     */
    @Override
    public Xid[] recover(final int flag) {
        return new Xid[0];
    }

    /**
     * Rolls back the transaction and restores the original auto commit setting.
     *
     * @param xid
     *            the id of the transaction branch for this connection
     * @throws XAException
     *             if connection.rollback() throws a SQLException
     */
    @Override
    public void rollback(final Xid xid) throws XAException {
        Objects.requireNonNull(xid);
        final Object myXid = this.xid;
        if (!myXid.equals(xid)) {
            throw new XAException("Invalid Xid: expected " + myXid + ", but was " + xid);
        }

        try {
            this.connection.rollback();
        } catch (final SQLException e) {
            throw (XAException) new XAException().initCause(e);
        } finally {
            try {
                this.connection.setAutoCommit(this.originalAutoCommit);
            } catch (final SQLException e) {
                // Ignore.
            }
            this.xid = null;
        }
    }

    /**
     * Always returns false since we have no way to set a transaction timeout on a JDBC connection.
     *
     * @param transactionTimeout
     *            ignored since we have no way to set a transaction timeout on a JDBC connection
     * @return always false
     */
    @Override
    public boolean setTransactionTimeout(final int transactionTimeout) {
        return false;
    }

    /**
     * Signals that a the connection has been enrolled in a transaction. This method saves off the current auto
     * commit flag, and then disables auto commit. The original auto commit setting is restored when the transaction
     * completes.
     *
     * @param xid
     *            the id of the transaction branch for this connection
     * @param flag
     *            either XAResource.TMNOFLAGS or XAResource.TMRESUME
     * @throws XAException
     *             if the connection is already enlisted in another transaction, or if auto-commit could not be
     *             disabled
     */
    @Override
    public void start(final Xid xid, final int flag) throws XAException {
        Objects.requireNonNull(xid);
        final Object myXid = this.xid;
        if (flag == XAResource.TMNOFLAGS) {
            // first time in this transaction

            // make sure we aren't already in another tx
            if (myXid != null) {
                throw new XAException("Already enlisted in another transaction with xid " + myXid);
            }

            // save off the current auto commit flag so it can be restored after the transaction completes
            try {
                this.originalAutoCommit = this.connection.getAutoCommit();
            } catch (final SQLException ignored) {
                this.originalAutoCommit = true;
            }

            // update the auto commit flag
            try {
                this.connection.setAutoCommit(false);
            } catch (final SQLException e) {
                throw (XAException) new XAException("Count not turn off auto commit for a XA transaction").initCause(e);
            }

            this.xid = xid;
        } else if (flag == XAResource.TMRESUME) {
            if (!xid.equals(myXid)) {
                throw new XAException("Attempting to resume in different transaction: expected " + myXid
                                      + ", but was " + xid);
            }
        } else {
            throw new XAException("Unknown start flag " + flag);
        }
    }

}
