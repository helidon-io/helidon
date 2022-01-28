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
 *
 */
package io.helidon.dbclient.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import io.helidon.dbclient.DbClientException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class JdbcClientMultipleDMLOperationTest {

    private enum DmlOperation { insert, update, delete}
    private static final Logger LOGGER = Logger.getLogger(JdbcClientMultipleDMLOperationTest.class.getName());

    String failedMessage = "";
    AtomicInteger totalDMLOperation = new AtomicInteger(0);

    @Test
    void testMultipleInsert() {
        multipleDMLOperationExecution(false, DmlOperation.insert);
    }

    @Test
    void testMultipleTxInsert() {
        multipleDMLOperationExecution(true, DmlOperation.insert);
    }

    @Test
    void testMultipleUpdate() {
        multipleDMLOperationExecution(false, DmlOperation.update);
    }

    @Test
    void testMultipleTxUpdate() {
        multipleDMLOperationExecution(true, DmlOperation.update);
    }

    @Test
    void testMultipleDelete() {
        multipleDMLOperationExecution(false, DmlOperation.delete);
    }

    @Test
    void testMultipleTxDelete() {
        multipleDMLOperationExecution(true, DmlOperation.delete);
    }

    void multipleDMLOperationExecution(boolean tx, DmlOperation dmlOperation) {
        int maxIteration = 100;

        failedMessage = "";
        JdbcDbClient dbClient = (JdbcDbClient) JdbcDbClientProviderBuilder.create()
                .connectionPool(new MockConnectionPool())
                .build();
        switch (dmlOperation) {
            case insert:
                for (int i = 0; i < maxIteration; i++) {
                    if (tx) {
                        dbClient.inTransaction(exec -> exec.createInsert("INSERT INTO pokemons (name, type) VALUES ('name', 'type')").execute())
                                .thenAccept(
                                        count ->this.totalDMLOperation.incrementAndGet()
                                )
                                .exceptionally(throwable -> {
                                    throwable.printStackTrace();
                                    failedMessage = throwable.getMessage();
                                    return null;
                                });
                    } else {
                        dbClient.execute(exec -> exec.createInsert("INSERT INTO pokemons (name, type) VALUES ('name', 'type')").execute())
                                .thenAccept(
                                        count ->this.totalDMLOperation.incrementAndGet()
                                )
                                .exceptionally(throwable -> {
                                    throwable.printStackTrace();
                                    failedMessage = throwable.getMessage();
                                    return null;
                                });
                    }
                }
                break;
            case update:
                for (int i = 0; i < maxIteration; i++) {
                    if (tx) {
                        dbClient.inTransaction(exec -> exec.createUpdate("UPDATE pokemons SET type = 'type' WHERE name = 'name'").execute())
                                .thenAccept(
                                        count ->this.totalDMLOperation.incrementAndGet()
                                )
                                .exceptionally(throwable -> {
                                    throwable.printStackTrace();
                                    failedMessage = throwable.getMessage();
                                    return null;
                                });
                    } else {
                        dbClient.execute(exec -> exec.createUpdate("UPDATE pokemons SET type = 'type' WHERE name = 'name'").execute())
                                .thenAccept(
                                        count ->this.totalDMLOperation.incrementAndGet()
                                )
                                .exceptionally(throwable -> {
                                    throwable.printStackTrace();
                                    failedMessage = throwable.getMessage();
                                    return null;
                                });
                    }
                }
                break;
            case delete:
                for (int i = 0; i < maxIteration; i++) {
                    if (tx) {
                        dbClient.inTransaction(exec -> exec.createDelete("DELETE FROM pokemons WHERE name = name").execute())
                                .thenAccept(
                                        count -> this.totalDMLOperation.incrementAndGet()
                                )
                                .exceptionally(throwable -> {
                                    throwable.printStackTrace();
                                    failedMessage = throwable.getMessage();
                                    return null;
                                });
                    } else {
                        dbClient.execute(exec -> exec.createDelete("DELETE FROM pokemons WHERE name = name").execute())
                                .thenAccept(
                                        count ->this.totalDMLOperation.incrementAndGet()
                                )
                                .exceptionally(throwable -> {
                                    throwable.printStackTrace();
                                    failedMessage = throwable.getMessage();
                                    return null;
                                });
                    }
                }
                break;
        }
        Timer timer = new Timer(20);
        while (this.totalDMLOperation.get() < maxIteration) {
            try {
                if (!this.failedMessage.isEmpty())
                    fail(String.format("Failed to %s data with error: %s", dmlOperation, this.failedMessage));
                if (timer.expired()) {
                    fail(String.format("Only %d requests completed after %d sec.",
                            this.totalDMLOperation.get(), timer.getTimeout()));
                }
                Thread.sleep(100);
            }  catch (InterruptedException e) {
                break;
            }
        }
    }

    static class Timer {
        private final long endTime;
        private final int timeOut;

        public Timer(int timeOut) {
            this.timeOut = timeOut;
            this.endTime = System.currentTimeMillis() + 1_000L * timeOut;
        }

        public boolean expired() {
            return System.currentTimeMillis() >= this.endTime;
        }

        int getTimeout() {
            return this.timeOut;
        }
    }

    static class MockConnectionPool implements io.helidon.dbclient.jdbc.ConnectionPool {
        int maxPoolCount = 10;
        List <Connection> connectionPool = Collections.synchronizedList(new ArrayList<>(maxPoolCount));
        List <Connection> usedConnections = Collections.synchronizedList(new ArrayList<>(maxPoolCount));

        int actualConnection = 0;

        @Override
        public Connection connection() {
            Connection conn;
            this.actualConnection++;
            // get connection from the pool if it is not empty,
            if (!connectionPool.isEmpty()) {
                conn = this.connectionPool.remove(0);
            } else {
                Timer timer = new Timer(10);
                // if usedConnections reach maxPoolCount, wait for 5 minutes until it recedes. Otherwise throw an exception
                while (this.usedConnections.size() >= maxPoolCount) {
                    try {
                        if (timer.expired()) {
                            throw new DbClientException(
                                    String.format("Unable to acquire connection after %d sec", timer.getTimeout()));
                        }
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new DbClientException(
                                String.format("Unable to acquire connection due to InterruptedException: %s", e.getMessage()));
                    }
                }
                // Create a mocked up Connection that returns a PreparedStatement
                conn = new Connection() {
                    @Override
                    public Statement createStatement() throws SQLException {
                        return null;
                    }

                    @Override
                    public PreparedStatement prepareStatement(String sql) throws SQLException {
                        // Create a mocked up Connection that returns 1 on executeLargeUpdate
                        return new PreparedStatement() {
                            @Override
                            public long executeLargeUpdate() throws SQLException {
                                return 1L;
                            }

                            @Override
                            public ResultSet executeQuery() throws SQLException {
                                return null;
                            }

                            @Override
                            public int executeUpdate() throws SQLException {
                                return 0;
                            }

                            @Override
                            public void setNull(int parameterIndex, int sqlType) throws SQLException {

                            }

                            @Override
                            public void setBoolean(int parameterIndex, boolean x) throws SQLException {

                            }

                            @Override
                            public void setByte(int parameterIndex, byte x) throws SQLException {

                            }

                            @Override
                            public void setShort(int parameterIndex, short x) throws SQLException {

                            }

                            @Override
                            public void setInt(int parameterIndex, int x) throws SQLException {

                            }

                            @Override
                            public void setLong(int parameterIndex, long x) throws SQLException {

                            }

                            @Override
                            public void setFloat(int parameterIndex, float x) throws SQLException {

                            }

                            @Override
                            public void setDouble(int parameterIndex, double x) throws SQLException {

                            }

                            @Override
                            public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {

                            }

                            @Override
                            public void setString(int parameterIndex, String x) throws SQLException {

                            }

                            @Override
                            public void setBytes(int parameterIndex, byte[] x) throws SQLException {

                            }

                            @Override
                            public void setDate(int parameterIndex, Date x) throws SQLException {

                            }

                            @Override
                            public void setTime(int parameterIndex, Time x) throws SQLException {

                            }

                            @Override
                            public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {

                            }

                            @Override
                            public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {

                            }

                            @Override
                            public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {

                            }

                            @Override
                            public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {

                            }

                            @Override
                            public void clearParameters() throws SQLException {

                            }

                            @Override
                            public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {

                            }

                            @Override
                            public void setObject(int parameterIndex, Object x) throws SQLException {

                            }

                            @Override
                            public boolean execute() throws SQLException {
                                return false;
                            }

                            @Override
                            public void addBatch() throws SQLException {

                            }

                            @Override
                            public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {

                            }

                            @Override
                            public void setRef(int parameterIndex, Ref x) throws SQLException {

                            }

                            @Override
                            public void setBlob(int parameterIndex, Blob x) throws SQLException {

                            }

                            @Override
                            public void setClob(int parameterIndex, Clob x) throws SQLException {

                            }

                            @Override
                            public void setArray(int parameterIndex, Array x) throws SQLException {

                            }

                            @Override
                            public ResultSetMetaData getMetaData() throws SQLException {
                                return null;
                            }

                            @Override
                            public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {

                            }

                            @Override
                            public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {

                            }

                            @Override
                            public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {

                            }

                            @Override
                            public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {

                            }

                            @Override
                            public void setURL(int parameterIndex, URL x) throws SQLException {

                            }

                            @Override
                            public ParameterMetaData getParameterMetaData() throws SQLException {
                                return null;
                            }

                            @Override
                            public void setRowId(int parameterIndex, RowId x) throws SQLException {

                            }

                            @Override
                            public void setNString(int parameterIndex, String value) throws SQLException {

                            }

                            @Override
                            public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {

                            }

                            @Override
                            public void setNClob(int parameterIndex, NClob value) throws SQLException {

                            }

                            @Override
                            public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {

                            }

                            @Override
                            public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {

                            }

                            @Override
                            public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {

                            }

                            @Override
                            public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {

                            }

                            @Override
                            public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {

                            }

                            @Override
                            public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {

                            }

                            @Override
                            public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {

                            }

                            @Override
                            public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {

                            }

                            @Override
                            public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {

                            }

                            @Override
                            public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {

                            }

                            @Override
                            public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {

                            }

                            @Override
                            public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {

                            }

                            @Override
                            public void setClob(int parameterIndex, Reader reader) throws SQLException {

                            }

                            @Override
                            public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {

                            }

                            @Override
                            public void setNClob(int parameterIndex, Reader reader) throws SQLException {

                            }

                            @Override
                            public ResultSet executeQuery(String sql) throws SQLException {
                                return null;
                            }

                            @Override
                            public int executeUpdate(String sql) throws SQLException {
                                return 0;
                            }

                            @Override
                            public void close() throws SQLException {

                            }

                            @Override
                            public int getMaxFieldSize() throws SQLException {
                                return 0;
                            }

                            @Override
                            public void setMaxFieldSize(int max) throws SQLException {

                            }

                            @Override
                            public int getMaxRows() throws SQLException {
                                return 0;
                            }

                            @Override
                            public void setMaxRows(int max) throws SQLException {

                            }

                            @Override
                            public void setEscapeProcessing(boolean enable) throws SQLException {

                            }

                            @Override
                            public int getQueryTimeout() throws SQLException {
                                return 0;
                            }

                            @Override
                            public void setQueryTimeout(int seconds) throws SQLException {

                            }

                            @Override
                            public void cancel() throws SQLException {

                            }

                            @Override
                            public SQLWarning getWarnings() throws SQLException {
                                return null;
                            }

                            @Override
                            public void clearWarnings() throws SQLException {

                            }

                            @Override
                            public void setCursorName(String name) throws SQLException {

                            }

                            @Override
                            public boolean execute(String sql) throws SQLException {
                                return false;
                            }

                            @Override
                            public ResultSet getResultSet() throws SQLException {
                                return null;
                            }

                            @Override
                            public int getUpdateCount() throws SQLException {
                                return 0;
                            }

                            @Override
                            public boolean getMoreResults() throws SQLException {
                                return false;
                            }

                            @Override
                            public void setFetchDirection(int direction) throws SQLException {

                            }

                            @Override
                            public int getFetchDirection() throws SQLException {
                                return 0;
                            }

                            @Override
                            public void setFetchSize(int rows) throws SQLException {

                            }

                            @Override
                            public int getFetchSize() throws SQLException {
                                return 0;
                            }

                            @Override
                            public int getResultSetConcurrency() throws SQLException {
                                return 0;
                            }

                            @Override
                            public int getResultSetType() throws SQLException {
                                return 0;
                            }

                            @Override
                            public void addBatch(String sql) throws SQLException {

                            }

                            @Override
                            public void clearBatch() throws SQLException {

                            }

                            @Override
                            public int[] executeBatch() throws SQLException {
                                return new int[0];
                            }

                            @Override
                            public Connection getConnection() throws SQLException {
                                return null;
                            }

                            @Override
                            public boolean getMoreResults(int current) throws SQLException {
                                return false;
                            }

                            @Override
                            public ResultSet getGeneratedKeys() throws SQLException {
                                return null;
                            }

                            @Override
                            public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
                                return 0;
                            }

                            @Override
                            public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
                                return 0;
                            }

                            @Override
                            public int executeUpdate(String sql, String[] columnNames) throws SQLException {
                                return 0;
                            }

                            @Override
                            public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
                                return false;
                            }

                            @Override
                            public boolean execute(String sql, int[] columnIndexes) throws SQLException {
                                return false;
                            }

                            @Override
                            public boolean execute(String sql, String[] columnNames) throws SQLException {
                                return false;
                            }

                            @Override
                            public int getResultSetHoldability() throws SQLException {
                                return 0;
                            }

                            @Override
                            public boolean isClosed() throws SQLException {
                                return false;
                            }

                            @Override
                            public void setPoolable(boolean poolable) throws SQLException {

                            }

                            @Override
                            public boolean isPoolable() throws SQLException {
                                return false;
                            }

                            @Override
                            public void closeOnCompletion() throws SQLException {

                            }

                            @Override
                            public boolean isCloseOnCompletion() throws SQLException {
                                return false;
                            }

                            @Override
                            public <T> T unwrap(Class<T> iface) throws SQLException {
                                return null;
                            }

                            @Override
                            public boolean isWrapperFor(Class<?> iface) throws SQLException {
                                return false;
                            }
                        };
                    }

                    @Override
                    public CallableStatement prepareCall(String sql) throws SQLException {
                        return null;
                    }

                    @Override
                    public String nativeSQL(String sql) throws SQLException {
                        return null;
                    }

                    @Override
                    public void setAutoCommit(boolean autoCommit) throws SQLException {

                    }

                    @Override
                    public boolean getAutoCommit() throws SQLException {
                        return false;
                    }

                    @Override
                    public void commit() throws SQLException {

                    }

                    @Override
                    public void rollback() throws SQLException {

                    }

                    @Override
                    public void close() throws SQLException {
                        if (usedConnections.remove(this)) {
                            connectionPool.add(this);
                        }
                        LOGGER.fine(() ->
                                String.format("After Connection.close() metrics: connectionPool=%d, usedConnections=%d",
                                connectionPool.size(), usedConnections.size()));
                    }

                    @Override
                    public boolean isClosed() throws SQLException {
                        return false;
                    }

                    @Override
                    public DatabaseMetaData getMetaData() throws SQLException {
                        return null;
                    }

                    @Override
                    public void setReadOnly(boolean readOnly) throws SQLException {

                    }

                    @Override
                    public boolean isReadOnly() throws SQLException {
                        return false;
                    }

                    @Override
                    public void setCatalog(String catalog) throws SQLException {

                    }

                    @Override
                    public String getCatalog() throws SQLException {
                        return null;
                    }

                    @Override
                    public void setTransactionIsolation(int level) throws SQLException {

                    }

                    @Override
                    public int getTransactionIsolation() throws SQLException {
                        return 0;
                    }

                    @Override
                    public SQLWarning getWarnings() throws SQLException {
                        return null;
                    }

                    @Override
                    public void clearWarnings() throws SQLException {

                    }

                    @Override
                    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
                        return null;
                    }

                    @Override
                    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
                        return null;
                    }

                    @Override
                    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
                        return null;
                    }

                    @Override
                    public Map<String, Class<?>> getTypeMap() throws SQLException {
                        return null;
                    }

                    @Override
                    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {

                    }

                    @Override
                    public void setHoldability(int holdability) throws SQLException {

                    }

                    @Override
                    public int getHoldability() throws SQLException {
                        return 0;
                    }

                    @Override
                    public Savepoint setSavepoint() throws SQLException {
                        return null;
                    }

                    @Override
                    public Savepoint setSavepoint(String name) throws SQLException {
                        return null;
                    }

                    @Override
                    public void rollback(Savepoint savepoint) throws SQLException {

                    }

                    @Override
                    public void releaseSavepoint(Savepoint savepoint) throws SQLException {

                    }

                    @Override
                    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
                        return null;
                    }

                    @Override
                    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
                        return null;
                    }

                    @Override
                    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
                        return null;
                    }

                    @Override
                    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
                        return null;
                    }

                    @Override
                    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
                        return null;
                    }

                    @Override
                    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
                        return null;
                    }

                    @Override
                    public Clob createClob() throws SQLException {
                        return null;
                    }

                    @Override
                    public Blob createBlob() throws SQLException {
                        return null;
                    }

                    @Override
                    public NClob createNClob() throws SQLException {
                        return null;
                    }

                    @Override
                    public SQLXML createSQLXML() throws SQLException {
                        return null;
                    }

                    @Override
                    public boolean isValid(int timeout) throws SQLException {
                        return false;
                    }

                    @Override
                    public void setClientInfo(String name, String value) throws SQLClientInfoException {

                    }

                    @Override
                    public void setClientInfo(Properties properties) throws SQLClientInfoException {

                    }

                    @Override
                    public String getClientInfo(String name) throws SQLException {
                        return null;
                    }

                    @Override
                    public Properties getClientInfo() throws SQLException {
                        return null;
                    }

                    @Override
                    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
                        return null;
                    }

                    @Override
                    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
                        return null;
                    }

                    @Override
                    public void setSchema(String schema) throws SQLException {

                    }

                    @Override
                    public String getSchema() throws SQLException {
                        return null;
                    }

                    @Override
                    public void abort(Executor executor) throws SQLException {

                    }

                    @Override
                    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {

                    }

                    @Override
                    public int getNetworkTimeout() throws SQLException {
                        return 0;
                    }

                    @Override
                    public <T> T unwrap(Class<T> iface) throws SQLException {
                        return null;
                    }

                    @Override
                    public boolean isWrapperFor(Class<?> iface) throws SQLException {
                        return false;
                    }
                };
            }
            this.usedConnections.add(conn);
            LOGGER.fine(() ->
                    String.format("After ConnectionPool.connection() metrics: connectionPool=%d, usedConnections=%d",
                    connectionPool.size(), usedConnections.size()));
            return conn;
        }
    }
}
