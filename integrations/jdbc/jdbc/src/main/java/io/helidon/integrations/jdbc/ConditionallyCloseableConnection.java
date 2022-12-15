/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.jdbc;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * A <a href="https://download.oracle.com/otn-pub/jcp/jdbc-4_3-mrel3-spec/jdbc4.3-fr-spec.pdf" target="_parent">JDBC
 * 4.3</a>-compliant {@link DelegatingConnection} whose {@link #close()} method may or may not close it depending on
 * other partial state.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are not necessarily safe for concurrent use by multiple threads because their {@link
 * Connection} delegates may not be. JDBC 4.3 does not require thread safety from any JDBC construct.</p>
 *
 * @see #isClosed()
 *
 * @see #isCloseable()
 *
 * @see #setCloseable(boolean)
 *
 * @see #close()
 *
 * @see #isClosePending()
 *
 * @see #ConditionallyCloseableConnection(Connection, boolean, boolean)
 */
public class ConditionallyCloseableConnection extends DelegatingConnection {


    /*
     * Instance fields.
     */


    /**
     * A {@link SQLRunnable} representing the logic run by the {@link #checkOpen()} method.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is set based on the value of the {@code strictClosedChecking} argument supplied to the {@link
     * #ConditionallyCloseableConnection(Connection, boolean, boolean)} constructor. It may end up deliberately doing
     * nothing.</p>
     *
     * @see #ConditionallyCloseableConnection(Connection, boolean, boolean)
     */
    private final SQLRunnable closedChecker;

    /**
     * A {@link SQLBooleanSupplier} that is the effective body of the {@link #isClosed()} method.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is set based on the value of the {@code strictClosedChecking} argument supplied to the {@link
     * #ConditionallyCloseableConnection(Connection, boolean, boolean)} constructor.
     *
     * @see #isClosed()
     *
     * @see #ConditionallyCloseableConnection(Connection, boolean, boolean)
     */
    private SQLBooleanSupplier isClosedFunction;

    /**
     * Whether or not the {@link #close()} method will actually close this {@link DelegatingConnection}.
     *
     * <p>This field is set based on the value of the {@code strictClosedChecking} argument supplied to the {@link
     * #ConditionallyCloseableConnection(Connection, boolean, boolean)} constructor. It may end up deliberately doing
     * nothing.</p>
     *
     * @see #isCloseable()
     *
     * @see #setCloseable(boolean)
     *
     * @see #ConditionallyCloseableConnection(Connection, boolean, boolean)
     */
    private volatile boolean closeable;

    /**
     * Whether or not a {@link #close()} request has been issued from another thread.
     *
     * @see #isClosePending()
     *
     * @see #isClosed()
     */
    private volatile boolean closePending;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ConditionallyCloseableConnection} and {@linkplain #setCloseable(boolean) sets its closeable
     * status to <code>true</code>}.
     *
     * @param delegate the {@link Connection} to wrap; must not be {@code null}
     *
     * @exception NullPointerException if {@code delegate} is {@code null}
     *
     * @see #ConditionallyCloseableConnection(Connection, boolean, boolean)
     *
     * @deprecated This constructor continues to exist for backwards compatibility only <strong>and its use is strongly
     * discouraged</strong>. Please use the {@link #ConditionallyCloseableConnection(Connection, boolean, boolean)}
     * constructor instead <strong>and consider supplying {@code true} for its {@code strictClosedChecking}
     * parameter</strong>. In the future, this constructor may change, without prior notice, to cause new {@link
     * ConditionallyCloseableConnection} instances created by it to behave as if they were created by invocations of the
     * {@link #ConditionallyCloseableConnection(Connection, boolean, boolean)} constructor instead, with {@code true}
     * supplied for its {@code strictClosedChecking} parameter.
     */
    @Deprecated(since = "3.0.3")
    public ConditionallyCloseableConnection(Connection delegate) {
        this(delegate, true, false);
    }

    /**
     * Creates a new {@link ConditionallyCloseableConnection}.
     *
     * @param delegate the {@link Connection} to wrap; must not be {@code null}
     *
     * @param closeable the initial value for this {@link ConditionallyCloseableConnection}'s {@linkplain #isCloseable()
     * closeable} status
     *
     * @exception NullPointerException if {@code delegate} is {@code null}
     *
     * @see ConditionallyCloseableConnection(Connection, boolean, boolean)
     *
     * @deprecated This constructor continues to exist for backwards compatibility only <strong>and its use is strongly
     * discouraged</strong>. Please use the {@link #ConditionallyCloseableConnection(Connection, boolean, boolean)}
     * constructor instead <strong>and consider supplying {@code true} for its {@code strictClosedChecking}
     * parameter</strong>. In the future, this constructor may change, without prior notice, to cause new {@link
     * ConditionallyCloseableConnection} instances created by it to behave as if they were created by invocations of the
     * {@link #ConditionallyCloseableConnection(Connection, boolean, boolean)} constructor instead, with {@code true}
     * supplied for its {@code strictClosedChecking} parameter.
     */
    @Deprecated(since = "3.0.3")
    public ConditionallyCloseableConnection(Connection delegate, boolean closeable) {
        this(delegate, closeable, false);
    }

    /**
     * Creates a new {@link ConditionallyCloseableConnection}.
     *
     * @param delegate the {@link Connection} to wrap; must not be {@code null}
     *
     * @param closeable the initial value for this {@link ConditionallyCloseableConnection}'s {@linkplain #isCloseable()
     * closeable} status
     *
     * @param strictClosedChecking if {@code true}, then <em>this</em> {@link ConditionallyCloseableConnection}'s {@link
     * #isClosed()} method will be invoked before every operation that cannot take place on a closed connection, and, if
     * it returns {@code true}, the operation in question will fail with a {@link SQLException}; <strong>it is strongly
     * recommended to supply {@code true} as the argument for this parameter</strong> ({@code false} is permitted for
     * backwards compatibility reasons only)
     *
     * @exception NullPointerException if {@code delegate} is {@code null}
     *
     * @see #isCloseable()
     *
     * @see #setCloseable(boolean)
     *
     * @see #close()
     *
     * @see #isClosed()
     *
     * @see #isClosePending()
     *
     * @see DelegatingConnection#DelegatingConnection(Connection)
     */
    public ConditionallyCloseableConnection(Connection delegate,
                                            boolean closeable,
                                            boolean strictClosedChecking) {
        super(delegate);
        if (strictClosedChecking) {
            this.closedChecker = this::failWhenClosed;
            this.isClosedFunction = () -> this.isClosePending() || super.isClosed();
        } else {
            this.closedChecker = ConditionallyCloseableConnection::doNothing;
            this.isClosedFunction = super::isClosed;
        }
        this.closeable = closeable;
    }


    /*
     * Instance methods.
     */


    /**
     * Overrides the {@link DelegatingConnection#close()} method so that when it is invoked this {@link
     * ConditionallyCloseableConnection} is {@linkplain Connection#close() closed} only if it {@linkplain #isCloseable()
     * is closeable}.
     *
     * <p>Subclasses that override this method must not directly or indirectly call {@link #failWhenClosed()} or
     * undefined behavior may result.</p>
     *
     * <p>If {@code strictClosedChecking} was {@code true} {@linkplain #ConditionallyCloseableConnection(Connection,
     * boolean, boolean) at construction time} (strongly recommended), then the following pre- and post-conditions
     * apply:</p>
     *
     * <p>If {@link #isCloseable()} returns {@code true} at the point of an invocation of this method, then after this
     * method completes, successfully or not, {@link #isClosePending()} will return {@code false}.</p>
     *
     * <p>If {@link #isCloseable()} returns {@code false} at the point of an invocation of this method, then after this
     * method completes, successfully or not, {@link #isClosePending()} will return {@code true}.</p>
     *
     * <p>Overrides should normally call {@code super.close()} as part of their implementation.</p>
     *
     * @exception SQLException if an error occurs
     *
     * @see #isClosed()
     *
     * @see #isCloseable()
     *
     * @see #setCloseable(boolean)
     *
     * @see #isClosePending()
     */
    @Override // DelegatingConnection
    public void close() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        if (this.isCloseable()) {
            try {
                super.close();
            } finally {
                this.closePending = false;
                this.onClose();
            }
        } else {
            this.closePending = true;
        }
    }

    /**
     * Called by the {@link #close()} method to perform work after an actual close operation has completed.
     *
     * <p>During an invocation of this method by the {@link #close()} method:</p>
     *
     * <ul>
     *
     * <li>The {@link #isClosed()} method will return {@code true}.</li>
     *
     * <li>The {@link #isCloseable()} method will return {@code false}.</li>
     *
     * <li>The {@link #isClosePending()} method will return {@code false}.</li>
     *
     * </ul>
     *
     * <p>The default implementation of this method does nothing.</p>
     *
     * <p>Invoking this method directly may result in undefined behavior, depending on how it is overridden.</p>
     *
     * <p>Overrides of this method must not call {@link #close()} or undefined behavior, such as an infinite loop, may
     * result.</p>
     *
     * <p>Overrides of this method must be idempotent.</p>
     *
     * @exception SQLException if a database error occurs
     *
     * @see #isClosed()
     *
     * @see #isCloseable()
     *
     * @see #setCloseable(boolean)
     *
     * @see #isClosePending()
     *
     * @see #close()
     */
    protected void onClose() throws SQLException {

    }

    /**
     * Returns {@code true} if a call to {@link #close()} will actually close this {@link
     * ConditionallyCloseableConnection}.
     *
     * <p>This method returns {@code true} when {@link #setCloseable(boolean)} has been called with a value of {@code
     * true} and the {@link #isClosed()} method returns {@code false}.</p>
     *
     * <p>Subclasses that override this method must not directly or indirectly call {@link #failWhenClosed()} or
     * undefined behavior may result.</p>
     *
     * @return {@code true} if a call to {@link #close()} will actually close this {@link
     * ConditionallyCloseableConnection}; {@code false} in all other cases
     *
     * @exception SQLException if {@link #isClosed()} throws a {@link SQLException}
     *
     * @see #isClosed()
     *
     * @see #setCloseable(boolean)
     *
     * @see #close()
     *
     * @see #isClosePending()
     */
    public boolean isCloseable() throws SQLException {
        // this.checkOpen(); // Deliberately omitted.
        return this.closeable && !this.isClosed();
    }

    /**
     * Sets the closeable status of this {@link ConditionallyCloseableConnection} and, if the supplied {@code closeable}
     * agrument is {@code true}, sets the {@linkplain #isClosePending() close pending status} to {@code false}.
     *
     * <p>Subclasses that override this method must not directly or indirectly call {@link #failWhenClosed()} or
     * undefined behavior may result.</p>
     *
     * <p>Note that calling this method with a value of {@code true} does not necessarily mean that the {@link
     * #isCloseable()} method will subsequently return {@code true}, since the {@link #isClosed()} method may return
     * {@code true}.</p>
     *
     * <h4>Design Note</h4>
     *
     * <p>This method does not throw {@link SQLException} only because of an oversight in the design of the original
     * version of this class. Callers should consider catching {@link UncheckedSQLException} where appropriate
     * instead. The default implementation of this method does not throw any exceptions of any kind.</p>
     *
     * @param closeable whether or not a call to {@link #close()} will actually close this {@link
     * ConditionallyCloseableConnection}
     *
     * @see #isClosed()
     *
     * @see #isCloseable()
     *
     * @see #close()
     *
     * @see #isClosePending()
     *
     * @see Connection#close()
     */
    public void setCloseable(boolean closeable) {
        // this.checkOpen(); // Deliberately omitted.
        this.closeable = closeable;
        if (closeable) {
            this.closePending = false;
        }
    }

    /**
     * Returns {@code true} if and only if this {@link ConditionallyCloseableConnection} is behaving as if the {@link
     * #close()} method has been invoked while this {@link ConditionallyCloseableConnection} was {@linkplain
     * #isCloseable() not closeable}.
     *
     * <p>Subclasses that override this method must not directly or indirectly call {@link #failWhenClosed()} or
     * undefined behavior may result.</p>
     *
     * <p>Subclasses that override this method must not directly or indirectly mutate the state of this {@link
     * ConditionallyCloseableConnection} or undefined behavior may result.</p>
     *
     * @return {@code true} if and only if a close operation is pending
     *
     * @see #isClosed()
     *
     * @see #isCloseable()
     *
     * @see #setCloseable(boolean)
     *
     * @see #close()
     */
    public boolean isClosePending() {
        // this.checkOpen(); // Deliberately omitted.
        return this.closePending;
    }

    @Override // DelegatingConnection
    public Statement createStatement() throws SQLException {
        this.checkOpen();
        return super.createStatement();
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql);
    }

    @Override // DelegatingConnection
    public CallableStatement prepareCall(String sql) throws SQLException {
        this.checkOpen();
        return super.prepareCall(sql);
    }

    @Override // DelegatingConnection
    public String nativeSQL(String sql) throws SQLException {
        this.checkOpen();
        return super.nativeSQL(sql);
    }

    @Override // DelegatingConnection
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.checkOpen();
        super.setAutoCommit(autoCommit);
    }

    @Override // DelegatingConnection
    public boolean getAutoCommit() throws SQLException {
        this.checkOpen();
        return super.getAutoCommit();
    }

    @Override // DelegatingConnection
    public void commit() throws SQLException {
        this.checkOpen();
        super.commit();
    }

    @Override // DelegatingConnection
    public void rollback() throws SQLException {
        this.checkOpen();
        super.rollback();
    }

    /**
     * Returns {@code true} if and only if this {@link ConditionallyCloseableConnection} either is, or is to be
     * considered to be, closed, such that operations which must throw a {@link SQLException} when invoked on a closed
     * connection will do so.
     *
     * <p>If {@code true} was supplied for the {@code strictClosedChecking} parameter {@linkplain
     * #ConditionallyCloseableConnection(Connection, boolean, boolean) at construction time} (strongly recommended), the
     * default implementation of this method returns a value as if produced by the following implementation: {@code
     * this.}{@link #isClosePending() isClosePending() }{@code || super.isClosed()}.</p>
     *
     * <p>If {@code false} was supplied for the {@code strictClosedChecking} parameter {@linkplain
     * #ConditionallyCloseableConnection(Connection, boolean, boolean) at construction time} (not recommended), the
     * default implementation of this method returns a value as if produced by the following implementation: {@code
     * super.isClosed()}.</p>
     *
     * <p>Subclasses that override this method must not directly or indirectly call {@link #failWhenClosed()} or
     * undefined behavior may result.</p>
     *
     * @return {@code true} if and only if this {@link ConditionallyCloseableConnection} either is, or is to be
     * considered to be, closed
     *
     * @exception SQLException if a database access error occurs
     *
     * @see #isCloseable()
     *
     * @see #setCloseable(boolean)
     *
     * @see #close()
     *
     * @see #isClosePending()
     */
    @Override // DelegatingConnection
    public boolean isClosed() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec (and common sense).
        return this.isClosedFunction.getAsBoolean();
    }

    @Override // DelegatingConnection
    public DatabaseMetaData getMetaData() throws SQLException {
        this.checkOpen();
        return super.getMetaData();
    }

    @Override // DelegatingConnection
    public void setReadOnly(boolean readOnly) throws SQLException {
        this.checkOpen();
        super.setReadOnly(readOnly);
    }

    @Override // DelegatingConnection
    public boolean isReadOnly() throws SQLException {
        this.checkOpen();
        return super.isReadOnly();
    }

    @Override // DelegatingConnection
    public void setCatalog(String catalog) throws SQLException {
        this.checkOpen();
        super.setCatalog(catalog);
    }

    @Override // DelegatingConnection
    public String getCatalog() throws SQLException {
        this.checkOpen();
        return super.getCatalog();
    }

    @Override // DelegatingConnection
    public void setTransactionIsolation(int level) throws SQLException {
        this.checkOpen();
        super.setTransactionIsolation(level);
    }

    @Override // DelegatingConnection
    public int getTransactionIsolation() throws SQLException {
        this.checkOpen();
        return super.getTransactionIsolation();
    }

    @Override // DelegatingConnection
    public SQLWarning getWarnings() throws SQLException {
        this.checkOpen();
        return super.getWarnings();
    }

    @Override // DelegatingConnection
    public void clearWarnings() throws SQLException {
        this.checkOpen();
        super.clearWarnings();
    }

    @Override // DelegatingConnection
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        this.checkOpen();
        return super.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override // DelegatingConnection
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.checkOpen();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override // DelegatingConnection
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        this.checkOpen();
        return super.getTypeMap();
    }

    @Override // DelegatingConnection
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.checkOpen();
        super.setTypeMap(map);
    }

    @Override // DelegatingConnection
    public void setHoldability(int holdability) throws SQLException {
        this.checkOpen();
        super.setHoldability(holdability);
    }

    @Override // DelegatingConnection
    public int getHoldability() throws SQLException {
        this.checkOpen();
        return super.getHoldability();
    }

    @Override // DelegatingConnection
    public Savepoint setSavepoint() throws SQLException {
        this.checkOpen();
        return super.setSavepoint();
    }

    @Override // DelegatingConnection
    public Savepoint setSavepoint(String name) throws SQLException {
        this.checkOpen();
        return super.setSavepoint(name);
    }

    @Override // DelegatingConnection
    public void rollback(Savepoint savepoint) throws SQLException {
        this.checkOpen();
        super.rollback(savepoint);
    }

    @Override // DelegatingConnection
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.checkOpen();
        super.releaseSavepoint(savepoint);
    }

    @Override // DelegatingConnection
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        this.checkOpen();
        return super.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // DelegatingConnection
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
        this.checkOpen();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, columnIndexes);
    }

    @Override // DelegatingConnection
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        this.checkOpen();
        return super.prepareStatement(sql, columnNames);
    }

    @Override // DelegatingConnection
    public Clob createClob() throws SQLException {
        this.checkOpen();
        return super.createClob();
    }

    @Override // DelegatingConnection
    public Blob createBlob() throws SQLException {
        this.checkOpen();
        return super.createBlob();
    }

    @Override // DelegatingConnection
    public NClob createNClob() throws SQLException {
        this.checkOpen();
        return super.createNClob();
    }

    @Override // DelegatingConnection
    public SQLXML createSQLXML() throws SQLException {
        this.checkOpen();
        return super.createSQLXML();
    }

    @Override // DelegatingConnection
    public boolean isValid(int timeout) throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        return super.isValid(timeout);
    }

    @Override // DelegatingConnection
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        try {
            this.checkOpen();
        } catch (SQLClientInfoException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLClientInfoException(e.getMessage(), e.getSQLState(), e.getErrorCode(), Map.of(), e);
        }
        super.setClientInfo(name, value);
    }

    @Override // DelegatingConnection
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        try {
            this.checkOpen();
        } catch (SQLClientInfoException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLClientInfoException(e.getMessage(), e.getSQLState(), e.getErrorCode(), Map.of(), e);
        }
        super.setClientInfo(properties);
    }

    @Override // DelegatingConnection
    public String getClientInfo(String name) throws SQLException {
        this.checkOpen();
        return super.getClientInfo(name);
    }

    @Override // DelegatingConnection
    public Properties getClientInfo() throws SQLException {
        this.checkOpen();
        return super.getClientInfo();
    }

    @Override // DelegatingConnection
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        this.checkOpen();
        return super.createArrayOf(typeName, elements);
    }

    @Override // DelegatingConnection
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        this.checkOpen();
        return super.createStruct(typeName, attributes);
    }

    @Override // DelegatingConnection
    public void setSchema(String schema) throws SQLException {
        this.checkOpen();
        super.setSchema(schema);
    }

    @Override // DelegatingConnection
    public String getSchema() throws SQLException {
        this.checkOpen();
        return super.getSchema();
    }

    @Override // DelegatingConnection
    public void abort(Executor executor) throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        super.abort(executor);
    }

    @Override // DelegatingConnection
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        this.checkOpen();
        super.setNetworkTimeout(executor, milliseconds);
    }

    @Override // DelegatingConnection
    public int getNetworkTimeout() throws SQLException {
        this.checkOpen();
        return super.getNetworkTimeout();
    }

    @Override // DelegatingConnection
    public void beginRequest() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        super.beginRequest();
    }

    @Override // DelegatingConnection
    public void endRequest() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        super.endRequest();
    }

    @Override // DelegatingConnection
    public boolean setShardingKeyIfValid(ShardingKey shardingKey, ShardingKey superShardingKey, int timeout)
        throws SQLException {
        this.checkOpen();
        return super.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
    }

    @Override // DelegatingConnection
    public boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout) throws SQLException {
        this.checkOpen();
        return super.setShardingKeyIfValid(shardingKey, timeout);
    }

    @Override // DelegatingConnection
    public void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey) throws SQLException {
        this.checkOpen();
        super.setShardingKey(shardingKey, superShardingKey);
    }

    @Override // DelegatingConnection
    public void setShardingKey(ShardingKey shardingKey) throws SQLException {
        this.checkOpen();
        super.setShardingKey(shardingKey);
    }

    @Override // DelegatingConnection
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        return super.unwrap(iface);
    }

    @Override // DelegatingConnection
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        return super.isWrapperFor(iface);
    }

    /**
     * Ensures this {@link ConditionallyCloseableConnection} is {@linkplain #isClosed() not closed}, if {@linkplain
     * #ConditionallyCloseableConnection(Connection, boolean, boolean) strict closed checking was enabled at
     * construction time}, or simply returns if {@linkplain #ConditionallyCloseableConnection(Connection, boolean,
     * boolean) strict closed checking was <em>not</em> enabled at construction time}.
     *
     * <p>This method is called from almost every method in this class.</p>
     *
     * @exception SQLException if this {@link ConditionallyCloseableConnection} was {@linkplain
     * #ConditionallyCloseableConnection(Connection, boolean, boolean) created with strict closed checking enabled} and
     * an invocation of the {@link #isClosed()} method returns {@code true}, or if some other database access error
     * occurs
     *
     * @see #closedChecker
     */
    private void checkOpen() throws SQLException {
        this.closedChecker.run();
    }

    /**
     * Invokes the {@link #isClosed()} method, and, if it returns {@code true}, throws a new {@link SQLException}
     * indicating that because the connection is closed the operation cannot proceed.
     *
     * <p>If this {@link ConditionallyCloseableConnection} was {@linkplain #ConditionallyCloseableConnection(Connection,
     * boolean, boolean) created with strict closed checking enabled} (strongly recommended), then this method will be
     * called where appropriate.  Otherwise this method is not called internally by default implementations of the
     * methods in the {@link ConditionallyCloseableConnection} class.  Subclasses may, and often will, call this method
     * directly for any reason.</p>
     *
     * @exception SQLNonTransientConnectionException when an invocation of the {@link #isClosed()} method returns {@code
     * true}; its {@linkplain SQLException#getSQLState() SQLState} will begin with {@code 08}
     *
     * @exception SQLException if {@link #isClosed()} throws a {@link SQLException}
     *
     * @see #isClosed()
     *
     * @see #isCloseable()
     *
     * @see #setCloseable(boolean)
     *
     * @see #close()
     */
    protected final void failWhenClosed() throws SQLException {
        if (this.isClosed()) {
            throw new SQLNonTransientConnectionException("Connection is closed", "08000");
        }
    }


    /*
     * Static methods.
     */


    /**
     * Deliberately does nothing when invoked.
     *
     * <p>Used as a method reference only, and then only as a potential value for the {@link #closedChecker} field, and
     * then only when the {@code strictClosedChecking} argument supplied to the {@link
     * #ConditionallyCloseableConnection(Connection, boolean, boolean)} constructor was {@code false}.</p>
     *
     * @see #closedChecker
     *
     * @see #ConditionallyCloseableConnection(Connection, boolean, boolean)
     */
    // (Invoked by method reference only.)
    private static void doNothing() {

    }

}
