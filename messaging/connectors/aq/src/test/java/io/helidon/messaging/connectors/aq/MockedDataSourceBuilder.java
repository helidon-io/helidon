/*
 * Copyright (c)  2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.aq;

import java.sql.CallableStatement;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import oracle.jdbc.OracleStruct;
import oracle.jdbc.OracleTypeMetaData;
import oracle.jdbc.aq.AQMessage;
import oracle.jdbc.aq.AQMessageProperties;
import oracle.jdbc.internal.OracleClob;
import oracle.jdbc.internal.OracleConnection;
import oracle.sql.NUMBER;
import org.mockito.Mockito;

public class MockedDataSourceBuilder {

    static String SYS_GUID_SQL = "select SYS_GUID() from dual";
    static String ALL_QUEUES_SQL = "select /*+ FIRST_ROWS */ " +
            "t1.owner, " +
            "t1.name, " +
            "t1.queue_table, " +
            "t1.queue_type, " +
            "t1.max_retries, " +
            "t1.retry_delay, " +
            "t1.retention, " +
            "t1.user_comment, " +
            "t2.type, " +
            "t2.object_type " +
            "from all_queues t1, all_queue_tables t2 " +
            "where t1.owner=? and t1.name=? and t2.owner=? and t1.queue_table=t2.queue_table";

    static String ALL_QUEUE_TABLES_SQL = "select " +
            "queue_table, " +
            "type, " +
            "object_type, " +
            "sort_order, " +
            "recipients, " +
            "message_grouping, " +
            "user_comment, " +
            "compatible, " +
            "primary_instance, " +
            "secondary_instance, " +
            "owner " +
            "from all_queue_tables " +
            "where owner = ? and queue_table = ?";

    static String TEST_MESSAGE = "test payload!!!";

    public static DataSource create() throws SQLException {
        OracleConnection connection = Mockito.mock(oracle.jdbc.internal.OracleConnection.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        Statement sysGuidStatement = Mockito.mock(Statement.class);
        CallableStatement allQueuesStatement = Mockito.mock(CallableStatement.class);
        CallableStatement allQueueTablesStatement = Mockito.mock(CallableStatement.class);
        ResultSet sysGuidResultSet = Mockito.mock(ResultSet.class);
        ResultSet allQueuesResultSet = Mockito.mock(ResultSet.class);
        ResultSet allQueueTablesResultSet = Mockito.mock(ResultSet.class);
        DatabaseMetaData databaseMetaData = Mockito.mock(DatabaseMetaData.class);
        AQMessage aqMessage = Mockito.mock(AQMessage.class);
        OracleStruct oracleStruct = Mockito.mock(OracleStruct.class);
        OracleStruct oracleStructHeader = Mockito.mock(OracleStruct.class);
        OracleTypeMetaData oracleTypeMetaData = Mockito.mock(OracleTypeMetaData.class);
        OracleClob oracleClob = Mockito.mock(OracleClob.class);
        AQMessageProperties aqMessageProperties = Mockito.mock(AQMessageProperties.class);

        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.getURL()).thenReturn("jdbc:oracle:thin:@exampledb_high");
        Mockito.when(connection.createStatement()).thenReturn(sysGuidStatement);
        Mockito.when(connection.getDbCsId()).thenReturn((short) 2002);
        Mockito.when(connection.getMetaData()).thenReturn(databaseMetaData);
        Mockito.when(connection.getTypeMap()).thenReturn(new HashMapTable<>());
        Mockito.when(connection.prepareCall(ALL_QUEUES_SQL)).thenReturn(allQueuesStatement);
        Mockito.when(connection.prepareCall(ALL_QUEUE_TABLES_SQL)).thenReturn(allQueueTablesStatement);
        Mockito.when(allQueuesStatement.executeQuery()).thenReturn(allQueuesResultSet);
        Mockito.when(allQueueTablesStatement.executeQuery()).thenReturn(allQueueTablesResultSet);

        Mockito.when(aqMessage.isSTRUCTPayload()).thenReturn(true);
        Mockito.when(aqMessage.getStructPayload()).thenReturn(oracleStruct);
        Mockito.when(aqMessage.getMessageProperties()).thenReturn(aqMessageProperties);
        Mockito.when(aqMessage.getMessageId()).thenReturn("only-message".getBytes());
        Mockito.when(oracleStruct.getOracleMetaData()).thenReturn(oracleTypeMetaData);

        Object[] attributes = {
                oracleStructHeader,
                new NUMBER(TEST_MESSAGE.length()),
                TEST_MESSAGE,
                oracleClob
        };

        Mockito.when(oracleStruct.getAttributes()).thenReturn(attributes);
        Mockito.when(aqMessageProperties.getEnqueueTime()).thenReturn(Timestamp.from(new Date().toInstant()));
        Mockito.when(aqMessageProperties.getState()).thenReturn(AQMessageProperties.MessageState.READY);
        Mockito.when(aqMessageProperties.getCorrelation()).thenReturn("1111-2222-3333");
        Mockito.when(aqMessageProperties.getDelay()).thenReturn(0);

        Mockito.when(connection.dequeue(Mockito.anyString(), Mockito.any(), Mockito.anyString()))
                .thenReturn(aqMessage);

        // queues
        AtomicLong allQueuesCounter = new AtomicLong(2);
        Mockito.when(allQueuesResultSet.next()).thenAnswer(im -> allQueuesCounter.decrementAndGet() > 0);
        Mockito.when(allQueuesResultSet.getString(1)).thenAnswer(im -> "FRANK");// owner
        Mockito.when(allQueuesResultSet.getString(2)).thenAnswer(im -> "test-queue-" + allQueuesCounter.get());// name
        Mockito.when(allQueuesResultSet.getString(3)).thenAnswer(im -> "test-queue-table-" + allQueuesCounter.get());// queue table
        Mockito.when(allQueuesResultSet.getString(4)).thenAnswer(im -> "NORMAL_QUEUE");// queue type
        Mockito.when(allQueuesResultSet.getString(7)).thenAnswer(im -> "FOREVER");// retention
        Mockito.when(allQueuesResultSet.getString(8)).thenAnswer(im -> "no comment");// comment
        Mockito.when(allQueuesResultSet.getString(9)).thenAnswer(im -> "OBJECT"); // type
        Mockito.when(allQueuesResultSet.getString(10)).thenAnswer(im -> "SYS.AQ$_JMS_TEXT_MESSAGE");// object type

        // queue tables
        AtomicLong allQueueTablesCounter = new AtomicLong(2);
        Mockito.when(allQueueTablesResultSet.next()).thenAnswer(im -> allQueueTablesCounter.decrementAndGet() > 0);
        Mockito.when(allQueueTablesResultSet.getString(1)).thenAnswer(im -> "test-queue-table-1");// queue table
        Mockito.when(allQueueTablesResultSet.getString(2)).thenAnswer(im -> "OBJECT");// type
        Mockito.when(allQueueTablesResultSet.getString(3)).thenAnswer(im -> "SYS.AQ$_JMS_TEXT_MESSAGE");// object type
        Mockito.when(allQueueTablesResultSet.getString(4)).thenAnswer(im -> "ENQUEUE_TIME");// sort order
        Mockito.when(allQueueTablesResultSet.getString(5)).thenAnswer(im -> "SINGLE");// recipients
        Mockito.when(allQueueTablesResultSet.getString(6)).thenAnswer(im -> "NONE");// message_grouping
        Mockito.when(allQueueTablesResultSet.getString(7)).thenAnswer(im -> "no comment");// comment
        Mockito.when(allQueueTablesResultSet.getString(8)).thenAnswer(im -> "10.0.0");// compatible
        Mockito.when(allQueueTablesResultSet.getString(9)).thenAnswer(im -> "0");// primary_instance
        Mockito.when(allQueueTablesResultSet.getString(10)).thenAnswer(im -> "0");// secondary_instance
        Mockito.when(allQueueTablesResultSet.getString(11)).thenAnswer(im -> "FRANK");// owner

        Mockito.when(databaseMetaData.getUserName()).thenReturn("frank");
        Mockito.when(sysGuidStatement.executeQuery(SYS_GUID_SQL)).thenReturn(sysGuidResultSet);
        Mockito.when(sysGuidResultSet.getBytes(1)).thenReturn("TEST GUID".getBytes());

        return dataSource;
    }

    static class HashMapTable<K, V> extends Hashtable<K, V> implements Map<K, V> {
    }
}
