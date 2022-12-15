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
package io.helidon.integrations.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Objects;

/**
 * A <a href="https://download.oracle.com/otn-pub/jcp/jdbc-4_3-mrel3-spec/jdbc4.3-fr-spec.pdf" target="_parent">JDBC
 * 4.3</a>-compliant {@link Statement} that delegates to another JDBC 4.3-compliant {@link Statement}.
 *
 * @param <S> the type of the {@link Statement} subclass
 */
public class DelegatingStatement<S extends Statement> implements Statement {

    private final Connection connection;

    private final S delegate;

    private final SQLRunnable closedChecker;

    private volatile boolean closeable;

    /**
     * Creates a new {@link DelegatingStatement}.
     *
     * @param connection the {@link Connection} that created this {@link DelegatingStatement}; must not be {@code null}
     *
     * @param delegate the {@link Statement} instance to which all operations will be delegated; must not be {@code
     * null}
     *
     * @param closeable the initial value for this {@link DelegatingStatement}'s {@linkplain #isCloseable() closeable}
     * status
     *
     * @param strictClosedChecking if {@code true}, then <em>this</em> {@link DelegatingStatement}'s {@link #isClosed()}
     * method will be invoked before every operation that cannot take place on a closed statement, and, if it returns
     * {@code true}, the operation in question will fail with a {@link SQLException}
     *
     * @exception NullPointerException if either {@code connection} or {@code delegate} is {@code null}
     *
     * @see #getConnection()
     */
    public DelegatingStatement(Connection connection,
                               S delegate,
                               boolean closeable,
                               boolean strictClosedChecking) {
        super();
        this.connection = Objects.requireNonNull(connection, "connection");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.closeable = closeable;
        this.closedChecker = strictClosedChecking ? this::failWhenClosed : DelegatingStatement::doNothing;
    }

    /**
     * Returns the {@link Statement} to which all operations will be delegated.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return the {@link Statement} to which all operations will be delegated; never {@code null}
     */
    protected final S delegate() {
        return this.delegate;
    }

    /**
     * Returns {@code true} if a call to {@link #close()} will actually close this {@link DelegatingStatement}.
     *
     * <p>This method returns {@code true} when {@link #setCloseable(boolean)} has been called with a value of {@code
     * true} and the {@link #isClosed()} method returns {@code false}.</p>
     *
     * @return {@code true} if a call to {@link #close()} will actually close this {@link DelegatingStatement}; {@code
     * false} in all other cases
     *
     * @exception SQLException if {@link #isClosed()} throws a {@link SQLException}
     *
     * @see #setCloseable(boolean)
     *
     * @see #close()
     *
     * @see #isClosed()
     */
    public boolean isCloseable() throws SQLException {
        return this.closeable && !this.isClosed();
    }

    /**
     * Sets the closeable status of this {@link DelegatingStatement}.
     *
     * <p>Note that calling this method with a value of {@code true} does not necessarily mean that the {@link
     * #isCloseable()} method will subsequently return {@code true}, since the {@link #isClosed()} method may return
     * {@code true}.</p>
     *
     * @param closeable whether or not a call to {@link #close()} will actually close this {@link DelegatingStatement}
     *
     * @see #isCloseable()
     *
     * @see #close()
     *
     * @see Statement#close()
     *
     * @see #isClosed()
     */
    public void setCloseable(boolean closeable) {
        // this.checkOpen(); // Deliberately omitted.
        this.closeable = closeable;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.delegate().isClosed();
    }

    /**
     * Overrides the {@link Statement#close()} method so that when it is invoked this {@link DelegatingStatement} is
     * {@linkplain Statement#close() closed} only if it {@linkplain #isCloseable() is closeable}.
     *
     * <p>Overrides should normally call {@code super.close()} as part of their implementation.</p>
     *
     * @exception SQLException if an error occurs
     *
     * @see #isCloseable()
     */
    @Override
    public void close() throws SQLException {
        // NOTE
        if (this.isCloseable()) {
            this.delegate().close();
        }
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkOpen();
        return
          new DelegatingResultSet(this, // NOTE
                                  this.delegate().executeQuery(sql),
                                  true,
                                  true);
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        checkOpen();
        return this.delegate().executeUpdate(sql);
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        checkOpen();
        return this.delegate().getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        checkOpen();
        this.delegate().setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        checkOpen();
        return this.delegate().getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        checkOpen();
        this.delegate().setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        checkOpen();
        this.delegate().setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        checkOpen();
        return this.delegate().getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        checkOpen();
        this.delegate().setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        checkOpen();
        this.delegate().cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkOpen();
        return this.delegate().getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkOpen();
        this.delegate().clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        checkOpen();
        this.delegate().setCursorName(name);
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        checkOpen();
        return this.delegate().execute(sql);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        checkOpen();
        return
          new DelegatingResultSet(this, // NOTE
                                  this.delegate().getResultSet(),
                                  true,
                                  true);
    }

    @Override
    public int getUpdateCount() throws SQLException {
        checkOpen();
        return this.delegate().getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        checkOpen();
        return this.delegate().getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        checkOpen();
        this.delegate().setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        checkOpen();
        return this.delegate().getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        checkOpen();
        this.delegate().setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkOpen();
        return this.delegate().getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        checkOpen();
        return this.delegate().getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        checkOpen();
        return this.delegate().getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        checkOpen();
        this.delegate().addBatch(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        checkOpen();
        this.delegate().clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        checkOpen();
        return this.delegate().executeBatch();
    }

    /**
     * Returns the {@link Connection} {@linkplain #DelegatingStatement(Connection, Statement, boolean, boolean) supplied
     * at construction time}.
     *
     * @return the {@link Connection} {@linkplain #DelegatingStatement(Connection, Statement, boolean, boolean) supplied
     * at construction time}; never {@code null}
     *
     * @exception SQLException not thrown by the default implementation of this method
     *
     * @see #DelegatingStatement(Connection, Statement, boolean, boolean)
     */
    @Override
    public Connection getConnection() throws SQLException {
        checkOpen();
        // NOTE
        return this.connection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        checkOpen();
        return this.delegate().getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        checkOpen();
        return
          new DelegatingResultSet(this, // NOTE
                                  this.delegate().getGeneratedKeys(),
                                  true,
                                  true);
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        checkOpen();
        return this.delegate().executeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        checkOpen();
        return this.delegate().executeUpdate(sql, columnIndexes);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        checkOpen();
        return this.delegate().executeUpdate(sql, columnNames);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        checkOpen();
        return this.delegate().execute(sql, autoGeneratedKeys);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        checkOpen();
        return this.delegate().execute(sql, columnIndexes);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        checkOpen();
        return this.delegate().execute(sql, columnNames);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        checkOpen();
        return this.delegate().getResultSetHoldability();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        checkOpen();
        this.delegate().setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        checkOpen();
        return this.delegate().isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        checkOpen();
        this.delegate().closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        checkOpen();
        return this.delegate().isCloseOnCompletion();
    }

    @Override
    public long getLargeUpdateCount() throws SQLException {
        checkOpen();
        return this.delegate().getLargeUpdateCount();
    }

    @Override
    public void setLargeMaxRows(long max) throws SQLException {
        checkOpen();
        this.delegate().setLargeMaxRows(max);
    }

    @Override
    public long getLargeMaxRows() throws SQLException {
        checkOpen();
        return this.delegate().getLargeMaxRows();
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        checkOpen();
        return this.delegate().executeLargeBatch();
    }

    @Override
    public long executeLargeUpdate(String sql) throws SQLException {
        checkOpen();
        return this.delegate().executeLargeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        checkOpen();
        return this.delegate().executeLargeUpdate(sql, autoGeneratedKeys);
    }

    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        checkOpen();
        return this.delegate().executeLargeUpdate(sql, columnIndexes);
    }

    @Override
    public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        checkOpen();
        return this.delegate().executeLargeUpdate(sql, columnNames);
    }

    @Override
    public String enquoteLiteral(String val) throws SQLException {
        checkOpen();
        return this.delegate().enquoteLiteral(val);
    }

    @Override
    public String enquoteIdentifier(String identifier, boolean alwaysQuote) throws SQLException {
        checkOpen();
        return this.delegate().enquoteIdentifier(identifier, alwaysQuote);
    }

    @Override
    public boolean isSimpleIdentifier(String identifier) throws SQLException {
        checkOpen();
        return this.delegate().isSimpleIdentifier(identifier);
    }

    @Override
    public String enquoteNCharLiteral(String val) throws SQLException {
        checkOpen();
        return this.delegate().enquoteNCharLiteral(val);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // checkOpen(); // Deliberately omitted
        return iface.isInstance(this) ? iface.cast(this) : this.delegate().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // checkOpen(); // Deliberately omitted
        return iface.isInstance(this) || this.delegate().isWrapperFor(iface);
    }

    /**
     * Ensures this {@link DelegatingStatement} is {@linkplain #isClosed() not closed}, if {@linkplain
     * #DelegatingStatement(Connection, Statement, boolean, boolean) strict closed checking was enabled at construction
     * time}.
     *
     * <p>If a subclass overrides the {@link #isClosed()} method, the override must not call this method or undefined
     * behavior, such as an infinite loop, may result.</p>
     *
     * <p>This method is intended for advanced use cases only and almost all users of this class will have no reason to
     * call it.</p>
     *
     * @exception SQLException if this {@link DelegatingStatement} was {@linkplain #DelegatingStatement(Connection,
     * Statement, boolean, boolean) created with strict closed checking enabled} and an invocation of the {@link
     * #isClosed()} method returns {@code true}, or if some other database access error occurs
     */
    protected final void checkOpen() throws SQLException {
        this.closedChecker.run();
    }

    // (Invoked by method reference only.)
    private void failWhenClosed() throws SQLException {
        if (this.isClosed()) {
            throw new SQLNonTransientConnectionException("Statement is closed", "08000");
        }
    }


    /*
     * Static methods.
     */


    // (Invoked by method reference only.)
    private static void doNothing() {

    }

}
