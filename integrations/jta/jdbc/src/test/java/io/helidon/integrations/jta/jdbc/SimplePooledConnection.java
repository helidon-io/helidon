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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.Wrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.StatementEventListener;
import javax.sql.PooledConnection;

// It's unclear what should and should not be locked in here,
// since JDBC 4.x makes no mention of threads at all.
//
// I don't try to lock on the physical connection, since I don't know
// where it came from or how it behaves.  I *do* lock the connection
// listeners since operations that result in them being notified can
// happen on any thread and they can be added and removed on any
// thread.  I *do* use volatile semantics on the logical connection
// since creation/acquisition of a logical connection can happen on
// any thread, and since closing of this SimplePooledConnection (which
// closes everything) can happen on any thread.

/**
 * A straightforward implementation of the {@link PooledConnection} interface.
 *
 * <p>This implementation does not support pooling of {@link
 * PreparedStatement}s.</p>
 */
public class SimplePooledConnection implements PooledConnection {

    private static final Class<?>[] SINGLE_ELEMENT_CONNECTION_CLASS_ARRAY = new Class<?>[] { Connection.class };

    private static final Class<?>[] SINGLE_ELEMENT_CALLABLESTATEMENT_CLASS_ARRAY = new Class<?>[] { CallableStatement.class };

    private static final Class<?>[] SINGLE_ELEMENT_DATABASEMETADATA_CLASS_ARRAY = new Class<?>[] { DatabaseMetaData.class };

    private static final Class<?>[] SINGLE_ELEMENT_PREPAREDSTATEMENT_CLASS_ARRAY = new Class<?>[] { PreparedStatement.class };

    private static final Class<?>[] SINGLE_ELEMENT_RESULTSET_CLASS_ARRAY = new Class<?>[] { ResultSet.class };

    private static final Class<?>[] SINGLE_ELEMENT_STATEMENT_CLASS_ARRAY = new Class<?>[] { Statement.class };

    private static final VarHandle LOGICAL_CONNECTION;

    static {
        try {
            LOGICAL_CONNECTION =
                MethodHandles.lookup().findVarHandle(SimplePooledConnection.class, "logicalConnection", Connection.class);
        } catch (NoSuchFieldException | IllegalAccessException reflectiveOperationException) {
            throw new ExceptionInInitializerError(reflectiveOperationException);
        }
    }

    private final Lock connectionListenersLock;

    private final List<ConnectionEventListener> connectionListeners;

    private final Connection physicalConnection;

    private final InvocationHandler logicalConnectionInvocationHandler;

    private volatile Connection logicalConnection;

    /**
     * Creates a new {@link SimplePooledConnection}.
     *
     * @param physicalConnection the physical {@link Connection} this
     * {@link SimplePooledConnection} manages; must not be {@code
     * null}; must not {@linkplain Connection#isClosed() be closed}
     *
     * @exception NullPointerException if {@code physicalConnection}
     * is {@code null}
     *
     * @exception SQLException if {@code physicalConnection}
     * {@linkplain Connection#isClosed() is closed}
     */
    public SimplePooledConnection(Connection physicalConnection) throws SQLException {
        super();
        if (physicalConnection.isClosed()) {
            throw new IllegalArgumentException("physicalConnection.isClosed()");
        }
        this.physicalConnection = physicalConnection;
        this.connectionListenersLock = new ReentrantLock();
        this.connectionListeners = new ArrayList<>(3);
        this.logicalConnectionInvocationHandler =
          new ConnectionHandler(physicalConnection,
                                List.of(new UncloseableHandler<>(physicalConnection,
                                                                 this::physicalConnectionIsClosed,
                                                                 this::fireConnectionClosed,
                                                                 this::fireConnectionError)),
                                this::fireConnectionError);
    }

    @Override // PooledConnection
    public void addConnectionEventListener(ConnectionEventListener l) {
        if (l != null) {
            this.connectionListenersLock.lock();
            try {
                this.connectionListeners.add(l);
            } finally {
                this.connectionListenersLock.unlock();
            }
        }
    }

    @Override // PooledConnection
    public void removeConnectionEventListener(ConnectionEventListener l) {
        if (l != null) {
            this.connectionListenersLock.lock();
            try {
                this.connectionListeners.remove(l);
            } finally {
                this.connectionListenersLock.unlock();
            }
        }
    }

    @Override // PooledConnection
    public final void addStatementEventListener(StatementEventListener l) {
        // This PooledConnection implementation does not pool
        // PreparedStatements.
    }

    @Override // PooledConnection
    public final void removeStatementEventListener(StatementEventListener l) {
        // This PooledConnection implementation does not pool
        // PreparedStatements.
    }

    @Override // PooledConnection
    public Connection getConnection() throws SQLException {
        Connection logicalConnection = newLogicalConnection();
        Connection oldLogicalConnection =
            (Connection) LOGICAL_CONNECTION.getAndSet(this, logicalConnection); // volatile read and write
        if (oldLogicalConnection != null) {
            oldLogicalConnection.close();
        }
        return logicalConnection;
    }

    @Override // PooledConnection
    public void close() throws SQLException {
        this.connectionListenersLock.lock();
        try {
            this.connectionListeners.clear();
        } finally {
            this.connectionListenersLock.unlock();
        }

        try {
            // (If we were pooling statements, we'd close them here
            // first.)
            Connection logicalConnection = this.logicalConnection; // volatile read
            if (logicalConnection != null) {
                logicalConnection.close();
            }
        } finally {
            this.physicalConnection.close();
        }
    }

    private void fireConnectionClosed(Connection logicalConnection) {
        this.connectionListenersLock.lock();
        try {
            if (!this.connectionListeners.isEmpty()) {
                ConnectionEvent event = new ConnectionEvent(this);
                for (int i = this.connectionListeners.size() - 1; i >= 0; i--) {
                    // A common thing a listener wants to do is remove
                    // itself, so we iterate backwards to allow this to
                    // happen without having to copy the listener list.
                    this.connectionListeners.get(i).connectionClosed(event);
                }
            }
        } finally {
            this.connectionListenersLock.unlock();
        }
    }

    private void fireConnectionError(Wrapper logicalConstruct, Throwable throwable) {
        if (throwable instanceof SQLException sqlException) {
            this.connectionListenersLock.lock();
            try {
                if (!this.connectionListeners.isEmpty()) {
                    ConnectionEvent event = new ConnectionEvent(this, sqlException);
                    for (int i = this.connectionListeners.size() - 1; i >= 0; i--) {
                        // A common thing a listener wants to do is remove
                        // itself, so we iterate backwards to allow this to
                        // happen without having to copy the listener list.
                        this.connectionListeners.get(i).connectionErrorOccurred(event);
                    }
                }
            } finally {
                this.connectionListenersLock.unlock();
            }
        }
    }

    private Connection newLogicalConnection() throws SQLException {
        return
            (Connection) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                                                SINGLE_ELEMENT_CONNECTION_CLASS_ARRAY,
                                                this.logicalConnectionInvocationHandler);
    }

    private boolean physicalConnectionIsClosed(Connection physicalConnection) {
        try {
            return physicalConnection.isClosed();
        } catch (SQLException e) {
            this.fireConnectionError(physicalConnection, e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

}
