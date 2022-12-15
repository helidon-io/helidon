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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLType;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;
import java.util.Objects;

/**
 * A <a href="https://download.oracle.com/otn-pub/jcp/jdbc-4_3-mrel3-spec/jdbc4.3-fr-spec.pdf" target="_parent">JDBC
 * 4.3</a>-compliant {@link ResultSet} that delegates to another JDBC 4.3-compliant {@link ResultSet}.
 */
public class DelegatingResultSet implements ResultSet {

    private final Statement statement;

    private final ResultSet delegate;

    private final SQLRunnable closedChecker;

    private volatile boolean closeable;

    /**
     * Creates a new {@link DelegatingResultSet}.
     *
     * @param statement the {@link Statement} that will be returned by the {@link #getStatement()} method; may be {@code
     * null}
     *
     * @param delegate the {@link ResultSet} to which all operations will be delegated; must not be {@code null}
     *
     * @param closeable the initial value for this {@link DelegatingResultSet}'s {@linkplain #isCloseable() closeable}
     * status
     *
     * @param strictClosedChecking if {@code true}, then <em>this</em> {@link DelegatingResultSet}'s {@link #isClosed()}
     * method will be invoked before every operation that cannot take place on a closed statement, and, if it returns
     * {@code true}, the operation in question will fail with a {@link SQLException}
     *
     * @exception NullPointerException if {@code delegate} is {@code null}
     */
    public DelegatingResultSet(Statement statement, ResultSet delegate, boolean closeable, boolean strictClosedChecking) {
        super();
        this.statement = statement;
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.closeable = closeable;
        this.closedChecker = strictClosedChecking ? this::failWhenClosed : DelegatingResultSet::doNothing;
    }

    /**
     * Returns the {@link ResultSet} to which all operations will be delegated.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return the {@link ResultSet} to which all operations will be delegated; never {@code null}
     */
    protected final ResultSet delegate() {
        return this.delegate;
    }

    /**
     * Ensures this {@link DelegatingResultSet} is {@linkplain #isClosed() not closed}, if {@linkplain
     * #DelegatingResultSet(Connection, ResultSet, boolean, boolean) strict closed checking was enabled at construction
     * time}.
     *
     * <p>If a subclass overrides the {@link #isClosed()} method, the override must not call this method or undefined
     * behavior, such as an infinite loop, may result.</p>
     *
     * <p>This method is intended for advanced use cases only and almost all users of this class will have no reason to
     * call it.</p>
     *
     * @exception SQLException if this {@link DelegatingResultSet} was {@linkplain #DelegatingResultSet(Connection,
     * ResultSet, boolean, boolean) created with strict closed checking enabled} and an invocation of the {@link
     * #isClosed()} method returns {@code true}, or if some other database access error occurs
     */
    protected final void checkOpen() throws SQLException {
        this.closedChecker.run();
    }

    // (Invoked by method reference only.)
    private void failWhenClosed() throws SQLException {
        if (this.isClosed()) {
            throw new SQLNonTransientConnectionException("ResultSet is closed", "08000");
        }
    }

    /**
     * Returns {@code true} if a call to {@link #close()} will actually close this {@link DelegatingResultSet}.
     *
     * <p>This method returns {@code true} when {@link #setCloseable(boolean)} has been called with a value of {@code
     * true} and the {@link #isClosed()} method returns {@code false}.</p>
     *
     * @return {@code true} if a call to {@link #close()} will actually close this {@link DelegatingResultSet}; {@code
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
        // this.checkOpen(); // Deliberately omitted.
        return this.closeable && !this.isClosed();
    }

    /**
     * Sets the closeable status of this {@link DelegatingResultSet}.
     *
     * <p>Note that calling this method with a value of {@code true} does not necessarily mean that the {@link
     * #isCloseable()} method will subsequently return {@code true}, since the {@link #isClosed()} method may return
     * {@code true}.</p>
     *
     * @param closeable whether or not a call to {@link #close()} will actually close this {@link DelegatingResultSet}
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
    public boolean next() throws SQLException {
        checkOpen();
        return this.delegate().next();
    }

    @Override
    public void close() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        this.delegate().close();
    }

    @Override
    public boolean wasNull() throws SQLException {
        checkOpen();
        return this.delegate().wasNull();
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getString(columnIndex);
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getBoolean(columnIndex);
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getByte(columnIndex);
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getShort(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getInt(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getLong(columnIndex);
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getFloat(columnIndex);
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getDouble(columnIndex);
    }

    @Deprecated
    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        checkOpen();
        return this.delegate().getBigDecimal(columnIndex, scale);
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getBytes(columnIndex);
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getDate(columnIndex);
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getTime(columnIndex);
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getTimestamp(columnIndex);
    }

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getAsciiStream(columnIndex);
    }

    @Deprecated
    @Override
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getUnicodeStream(columnIndex);
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getBinaryStream(columnIndex);
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getString(columnLabel);
    }

    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getBoolean(columnLabel);
    }

    @Override
    public byte getByte(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getByte(columnLabel);
    }

    @Override
    public short getShort(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getShort(columnLabel);
    }

    @Override
    public int getInt(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getInt(columnLabel);
    }

    @Override
    public long getLong(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getLong(columnLabel);
    }

    @Override
    public float getFloat(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getFloat(columnLabel);
    }

    @Override
    public double getDouble(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getDouble(columnLabel);
    }

    @Deprecated
    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        checkOpen();
        return this.delegate().getBigDecimal(columnLabel, scale);
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getBytes(columnLabel);
    }

    @Override
    public Date getDate(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getDate(columnLabel);
    }

    @Override
    public Time getTime(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getTime(columnLabel);
    }

    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getTimestamp(columnLabel);
    }

    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getAsciiStream(columnLabel);
    }

    @Deprecated
    @Override
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getUnicodeStream(columnLabel);
    }

    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getBinaryStream(columnLabel);
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
    public String getCursorName() throws SQLException {
        checkOpen();
        return this.delegate().getCursorName();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkOpen();
        return this.delegate().getMetaData();
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getObject(columnIndex);
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getObject(columnLabel);
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().findColumn(columnLabel);
    }

    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getCharacterStream(columnIndex);
    }

    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getCharacterStream(columnLabel);
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getBigDecimal(columnIndex);
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getBigDecimal(columnLabel);
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        checkOpen();
        return this.delegate().isBeforeFirst();
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        checkOpen();
        return this.delegate().isAfterLast();
    }

    @Override
    public boolean isFirst() throws SQLException {
        checkOpen();
        return this.delegate().isFirst();
    }

    @Override
    public boolean isLast() throws SQLException {
        checkOpen();
        return this.delegate().isLast();
    }

    @Override
    public void beforeFirst() throws SQLException {
        checkOpen();
        this.delegate().beforeFirst();
    }

    @Override
    public void afterLast() throws SQLException {
        checkOpen();
        this.delegate().afterLast();
    }

    @Override
    public boolean first() throws SQLException {
        checkOpen();
        return this.delegate().first();
    }

    @Override
    public boolean last() throws SQLException {
        checkOpen();
        return this.delegate().last();
    }

    @Override
    public int getRow() throws SQLException {
        checkOpen();
        return this.delegate().getRow();
    }

    @Override
    public boolean absolute(int row) throws SQLException {
        checkOpen();
        return this.delegate().absolute(row);
    }

    @Override
    public boolean relative(int rows) throws SQLException {
        checkOpen();
        return this.delegate().relative(rows);
    }

    @Override
    public boolean previous() throws SQLException {
        checkOpen();
        return this.delegate().previous();
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
    public int getType() throws SQLException {
        checkOpen();
        return this.delegate().getType();
    }

    @Override
    public int getConcurrency() throws SQLException {
        checkOpen();
        return this.delegate().getConcurrency();
    }

    @Override
    public boolean rowUpdated() throws SQLException {
        checkOpen();
        return this.delegate().rowUpdated();
    }

    @Override
    public boolean rowInserted() throws SQLException {
        checkOpen();
        return this.delegate().rowInserted();
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        checkOpen();
        return this.delegate().rowDeleted();
    }

    @Override
    public void updateNull(int columnIndex) throws SQLException {
        checkOpen();
        this.delegate().updateNull(columnIndex);
    }

    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        checkOpen();
        this.delegate().updateBoolean(columnIndex, x);
    }

    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {
        checkOpen();
        this.delegate().updateByte(columnIndex, x);
    }

    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {
        checkOpen();
        this.delegate().updateShort(columnIndex, x);
    }

    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {
        checkOpen();
        this.delegate().updateInt(columnIndex, x);
    }

    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {
        checkOpen();
        this.delegate().updateLong(columnIndex, x);
    }

    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {
        checkOpen();
        this.delegate().updateFloat(columnIndex, x);
    }

    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {
        checkOpen();
        this.delegate().updateDouble(columnIndex, x);
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        checkOpen();
        this.delegate().updateBigDecimal(columnIndex, x);
    }

    @Override
    public void updateString(int columnIndex, String x) throws SQLException {
        checkOpen();
        this.delegate().updateString(columnIndex, x);
    }

    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        checkOpen();
        this.delegate().updateBytes(columnIndex, x);
    }

    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException {
        checkOpen();
        this.delegate().updateDate(columnIndex, x);
    }

    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException {
        checkOpen();
        this.delegate().updateTime(columnIndex, x);
    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
        checkOpen();
        this.delegate().updateTimestamp(columnIndex, x);
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        checkOpen();
        this.delegate().updateAsciiStream(columnIndex, x, length);
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        checkOpen();
        this.delegate().updateBinaryStream(columnIndex, x, length);
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        checkOpen();
        this.delegate().updateCharacterStream(columnIndex, x, length);
    }

    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
        checkOpen();
        this.delegate().updateObject(columnIndex, x, scaleOrLength);
    }

    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {
        checkOpen();
        this.delegate().updateObject(columnIndex, x);
    }

    @Override
    public void updateNull(String columnLabel) throws SQLException {
        checkOpen();
        this.delegate().updateNull(columnLabel);
    }

    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException {
        checkOpen();
        this.delegate().updateBoolean(columnLabel, x);
    }

    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException {
        checkOpen();
        this.delegate().updateByte(columnLabel, x);
    }

    @Override
    public void updateShort(String columnLabel, short x) throws SQLException {
        checkOpen();
        this.delegate().updateShort(columnLabel, x);
    }

    @Override
    public void updateInt(String columnLabel, int x) throws SQLException {
        checkOpen();
        this.delegate().updateInt(columnLabel, x);
    }

    @Override
    public void updateLong(String columnLabel, long x) throws SQLException {
        checkOpen();
        this.delegate().updateLong(columnLabel, x);
    }

    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException {
        checkOpen();
        this.delegate().updateFloat(columnLabel, x);
    }

    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException {
        checkOpen();
        this.delegate().updateDouble(columnLabel, x);
    }

    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
        checkOpen();
        this.delegate().updateBigDecimal(columnLabel, x);
    }

    @Override
    public void updateString(String columnLabel, String x) throws SQLException {
        checkOpen();
        this.delegate().updateString(columnLabel, x);
    }

    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException {
        checkOpen();
        this.delegate().updateBytes(columnLabel, x);
    }

    @Override
    public void updateDate(String columnLabel, Date x) throws SQLException {
        checkOpen();
        this.delegate().updateDate(columnLabel, x);
    }

    @Override
    public void updateTime(String columnLabel, Time x) throws SQLException {
        checkOpen();
        this.delegate().updateTime(columnLabel, x);
    }

    @Override
    public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
        checkOpen();
        this.delegate().updateTimestamp(columnLabel, x);
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
        checkOpen();
        this.delegate().updateAsciiStream(columnLabel, x, length);
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
        checkOpen();
        this.delegate().updateBinaryStream(columnLabel, x, length);
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
        checkOpen();
        this.delegate().updateCharacterStream(columnLabel, reader, length);
    }

    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
        checkOpen();
        this.delegate().updateObject(columnLabel, x, scaleOrLength);
    }

    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException {
        checkOpen();
        this.delegate().updateObject(columnLabel, x);
    }

    @Override
    public void insertRow() throws SQLException {
        checkOpen();
        this.delegate().insertRow();
    }

    @Override
    public void updateRow() throws SQLException {
        checkOpen();
        this.delegate().updateRow();
    }

    @Override
    public void deleteRow() throws SQLException {
        checkOpen();
        this.delegate().deleteRow();
    }

    @Override
    public void refreshRow() throws SQLException {
        checkOpen();
        this.delegate().refreshRow();
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        checkOpen();
        this.delegate().cancelRowUpdates();
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        checkOpen();
        this.delegate().moveToInsertRow();
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        checkOpen();
        this.delegate().moveToCurrentRow();
    }

    @Override
    public Statement getStatement() throws SQLException {
        checkOpen();
        // NOTE
        return this.statement;
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        checkOpen();
        return this.delegate().getObject(columnIndex, map);
    }

    @Override
    public Ref getRef(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getRef(columnIndex);
    }

    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getBlob(columnIndex);
    }

    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getClob(columnIndex);
    }

    @Override
    public Array getArray(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getArray(columnIndex);
    }

    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        checkOpen();
        return this.delegate().getObject(columnLabel, map);
    }

    @Override
    public Ref getRef(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getRef(columnLabel);
    }

    @Override
    public Blob getBlob(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getBlob(columnLabel);
    }

    @Override
    public Clob getClob(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getClob(columnLabel);
    }

    @Override
    public Array getArray(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getArray(columnLabel);
    }

    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        checkOpen();
        return this.delegate().getDate(columnIndex, cal);
    }

    @Override
    public Date getDate(String columnLabel, Calendar cal) throws SQLException {
        checkOpen();
        return this.delegate().getDate(columnLabel, cal);
    }

    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        checkOpen();
        return this.delegate().getTime(columnIndex, cal);
    }

    @Override
    public Time getTime(String columnLabel, Calendar cal) throws SQLException {
        checkOpen();
        return this.delegate().getTime(columnLabel, cal);
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        checkOpen();
        return this.delegate().getTimestamp(columnIndex, cal);
    }

    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        checkOpen();
        return this.delegate().getTimestamp(columnLabel, cal);
    }

    @Override
    public URL getURL(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getURL(columnIndex);
    }

    @Override
    public URL getURL(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getURL(columnLabel);
    }

    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {
        checkOpen();
        this.delegate().updateRef(columnIndex, x);
    }

    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException {
        checkOpen();
        this.delegate().updateRef(columnLabel, x);
    }

    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        checkOpen();
        this.delegate().updateBlob(columnIndex, x);
    }

    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException {
        checkOpen();
        this.delegate().updateBlob(columnLabel, x);
    }

    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {
        checkOpen();
        this.delegate().updateClob(columnIndex, x);
    }

    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException {
        checkOpen();
        this.delegate().updateClob(columnLabel, x);
    }

    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {
        checkOpen();
        this.delegate().updateArray(columnIndex, x);
    }

    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException {
        checkOpen();
        this.delegate().updateArray(columnLabel, x);
    }

    @Override
    public RowId getRowId(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getRowId(columnIndex);
    }

    @Override
    public RowId getRowId(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getRowId(columnLabel);
    }

    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        checkOpen();
        this.delegate().updateRowId(columnIndex, x);
    }

    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException {
        checkOpen();
        this.delegate().updateRowId(columnLabel, x);
    }

    @Override
    public int getHoldability() throws SQLException {
        checkOpen();
        return this.delegate().getHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        // this.checkOpen(); // Deliberately omitted per spec.
        return this.delegate().isClosed();
    }

    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {
        checkOpen();
        this.delegate().updateNString(columnIndex, nString);
    }

    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException {
        checkOpen();
        this.delegate().updateNString(columnLabel, nString);
    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        checkOpen();
        this.delegate().updateNClob(columnIndex, nClob);
    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        checkOpen();
        this.delegate().updateNClob(columnLabel, nClob);
    }

    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getNClob(columnIndex);
    }

    @Override
    public NClob getNClob(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getNClob(columnLabel);
    }

    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getSQLXML(columnIndex);
    }

    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getSQLXML(columnLabel);
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        checkOpen();
        this.delegate().updateSQLXML(columnIndex, xmlObject);
    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        checkOpen();
        this.delegate().updateSQLXML(columnLabel, xmlObject);
    }

    @Override
    public String getNString(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getNString(columnIndex);
    }

    @Override
    public String getNString(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getNString(columnLabel);
    }

    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        checkOpen();
        return this.delegate().getNCharacterStream(columnIndex);
    }

    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException {
        checkOpen();
        return this.delegate().getNCharacterStream(columnLabel);
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        checkOpen();
        this.delegate().updateNCharacterStream(columnIndex, x, length);
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        checkOpen();
        this.delegate().updateNCharacterStream(columnLabel, reader, length);
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        checkOpen();
        this.delegate().updateAsciiStream(columnIndex, x, length);
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        checkOpen();
        this.delegate().updateBinaryStream(columnIndex, x, length);
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        checkOpen();
        this.delegate().updateCharacterStream(columnIndex, x, length);
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
        checkOpen();
        this.delegate().updateAsciiStream(columnLabel, x, length);
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
        checkOpen();
        this.delegate().updateBinaryStream(columnLabel, x, length);
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        checkOpen();
        this.delegate().updateCharacterStream(columnLabel, reader, length);
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        this.delegate().updateBlob(columnIndex, inputStream, length);
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        this.delegate().updateBlob(columnLabel, inputStream, length);
    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        checkOpen();
        this.delegate().updateClob(columnIndex, reader, length);
    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        checkOpen();
        this.delegate().updateClob(columnLabel, reader, length);
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        checkOpen();
        this.delegate().updateNClob(columnIndex, reader, length);
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        checkOpen();
        this.delegate().updateNClob(columnLabel, reader, length);
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        checkOpen();
        this.delegate().updateNCharacterStream(columnIndex, x);
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        checkOpen();
        this.delegate().updateNCharacterStream(columnLabel, reader);
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        checkOpen();
        this.delegate().updateAsciiStream(columnIndex, x);
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        checkOpen();
        this.delegate().updateBinaryStream(columnIndex, x);
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        checkOpen();
        this.delegate().updateCharacterStream(columnIndex, x);
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        checkOpen();
        this.delegate().updateAsciiStream(columnLabel, x);
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        checkOpen();
        this.delegate().updateBinaryStream(columnLabel, x);
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        checkOpen();
        this.delegate().updateCharacterStream(columnLabel, reader);
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        checkOpen();
        this.delegate().updateBlob(columnIndex, inputStream);
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        checkOpen();
        this.delegate().updateBlob(columnLabel, inputStream);
    }

    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        checkOpen();
        this.delegate().updateClob(columnIndex, reader);
    }

    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        checkOpen();
        this.delegate().updateClob(columnLabel, reader);
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        checkOpen();
        this.delegate().updateNClob(columnIndex, reader);
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        checkOpen();
        this.delegate().updateNClob(columnLabel, reader);
    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        checkOpen();
        return this.delegate().getObject(columnIndex, type);
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        checkOpen();
        return this.delegate().getObject(columnLabel, type);
    }

    @Override
    public void updateObject(int columnIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
        checkOpen();
        this.delegate().updateObject(columnIndex, x, targetSqlType, scaleOrLength);
    }

    @Override
    public void updateObject(String columnLabel, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
        checkOpen();
        this.delegate().updateObject(columnLabel, x, targetSqlType, scaleOrLength);
    }

    @Override
    public void updateObject(int columnIndex, Object x, SQLType targetSqlType) throws SQLException {
        checkOpen();
        this.delegate().updateObject(columnIndex, x, targetSqlType);
    }

    @Override
    public void updateObject(String columnLabel, Object x, SQLType targetSqlType) throws SQLException {
        checkOpen();
        this.delegate().updateObject(columnLabel, x, targetSqlType);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // checkOpen(); // Deliberately omitted per spec.
        return iface.isInstance(this) ? iface.cast(this) : this.delegate().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // checkOpen(); // Deliberately omitted per spec.
        return iface.isInstance(this) || this.delegate().isWrapperFor(iface);
    }


    /*
     * Static methods.
     */


    // (Invoked by method reference only.)
    private static void doNothing() {

    }

}
