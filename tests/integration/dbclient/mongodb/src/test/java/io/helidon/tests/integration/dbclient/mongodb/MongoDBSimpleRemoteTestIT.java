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
package io.helidon.tests.integration.dbclient.mongodb;

import io.helidon.tests.integration.dbclient.common.SimpleTest;

import org.junit.jupiter.api.Test;

/**
 * Remote simple test.
 */
final class MongoDBSimpleRemoteTestIT extends MongoDBRemoteTest implements SimpleTest {

    MongoDBSimpleRemoteTestIT() {
        super("/test/simple");
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
    public void testCreateNamedDmlWithInsertStrStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithInsertStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithInsertStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateDmlWithInsertNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateDmlWithInsertOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testNamedDmlWithInsertOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlWithInsertOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateDmlWithUpdateNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateDmlWithUpdateOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testNamedDmlWithUpdateOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlWithUpdateOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithDeleteStrStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithDeleteStrNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithDeleteStrOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateDmlWithDeleteNamedArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testCreateDmlWithDeleteOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testNamedDmlWithDeleteOrderArgs() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlWithDeleteOrderArgs() {
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
