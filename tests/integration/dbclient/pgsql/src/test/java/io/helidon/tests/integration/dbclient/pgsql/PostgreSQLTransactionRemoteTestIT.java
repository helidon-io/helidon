/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.pgsql;

import io.helidon.tests.integration.dbclient.common.TransactionTest;

import org.junit.jupiter.api.Test;

/**
 * Remote transaction test.
 */
final class PostgreSQLTransactionRemoteTestIT extends PostgreSQLRemoteTest implements TransactionTest {

    PostgreSQLTransactionRemoteTestIT() {
        super("/test/transaction");
    }

    @Test
    @Override
    public void testCreateNamedDeleteStrStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDeleteStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDeleteStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateDeleteNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateDeleteOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testNamedDeleteOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testDeleteOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedQueryNonExistentStmt() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedQueryNamedAndOrderArgsWithoutArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedQueryNamedAndOrderArgsWithArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedQueryNamedArgsSetOrderArg() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedQueryOrderArgsSetNamedArg() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedGetStrStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedGetStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedGetStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateGetNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateGetOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testNamedGetStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testGetStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedInsertStrStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedInsertStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedInsertStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateInsertNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateInsertOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testNamedInsertOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testInsertOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testInsertNamedArgsReturnedKeys() {
        remoteTest();
    }

    @Test
    @Override
    public void testInsertNamedArgsReturnedColumns() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedQueryStrStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedQueryStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedQueryStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateQueryNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateQueryOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testNamedQueryOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testQueryOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedUpdateStrStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedUpdateStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedUpdateStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateUpdateNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateUpdateOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testNamedUpdateNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testUpdateOrderArgs() {
        remoteTest();
    }
}
