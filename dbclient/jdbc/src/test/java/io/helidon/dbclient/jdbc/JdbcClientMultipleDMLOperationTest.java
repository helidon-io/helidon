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

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyString;

import io.helidon.dbclient.DbClientException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class JdbcClientMultipleDMLOperationTest {

    private enum DmlOperation { insert, update, delete}
    private String failedMessage = "";
    private AtomicInteger totalDMLOperation = new AtomicInteger(0);
    private static final PreparedStatement PREP_STATEMENT = mock(PreparedStatement.class);

    @BeforeAll
    static void beforeAll() throws SQLException {
        doAnswer(invocationOnMock -> {
            // Put a delay to simulate a statement processing
            delay(10);
            return 1L;
        }).when(PREP_STATEMENT).executeLargeUpdate();
    }

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
        Timer timer = new Timer(10);
        while (this.totalDMLOperation.get() < maxIteration) {
            if (!this.failedMessage.isEmpty())
                fail(String.format("Failed to %s data with error: %s", dmlOperation, this.failedMessage));
            if (timer.expired()) {
                fail(String.format("Only %d requests completed after %d sec.",
                        this.totalDMLOperation.get(), timer.getTimeout()));
            }
            delay(50);
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
                // If usedConnections reach maxPoolCount, wait for a few seconds until it recedes.
                // Otherwise throw an exception.
                Timer timer = new Timer(2);
                while (this.usedConnections.size() >= maxPoolCount) {
                    if (timer.expired()) {
                        throw new DbClientException(
                                String.format("Unable to acquire a connection after %d sec", timer.getTimeout()));
                    }
                    delay(50);
                }
                conn = mock(Connection.class);
                try {
                    when(conn.prepareStatement(anyString())).thenReturn(PREP_STATEMENT);
                    doAnswer(invocationOnMock -> {
                        if (this.usedConnections.remove(conn)) {
                            connectionPool.add(conn);
                        }
                        return null;
                    }).when(conn).close();
                } catch (Exception ignored) {
                }
            }
            this.usedConnections.add(conn);
            // Put delay to simulate an instantiation of a connection
            delay(10);
            return conn;
        }
    }

    private static class Timer {
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

    private static void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {
        }
    }
}
