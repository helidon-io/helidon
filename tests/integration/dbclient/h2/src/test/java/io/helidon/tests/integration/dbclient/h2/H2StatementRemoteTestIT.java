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
package io.helidon.tests.integration.dbclient.h2;

import io.helidon.tests.integration.dbclient.common.StatementTest;

import org.junit.jupiter.api.Test;

/**
 * Remote statement test.
 */
final class H2StatementRemoteTestIT extends H2RemoteTest implements StatementTest {

    H2StatementRemoteTestIT() {
        super("/test/statement");
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
    public void testGetArrayParams() {
        remoteTest();
    }

    @Test
    @Override
    public void testGetListParams() {
        remoteTest();
    }

    @Test
    @Override
    public void testGetMapParams() {
        remoteTest();
    }

    @Test
    @Override
    public void testGetOrderParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testGetNamedParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testGetMappedNamedParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testGetMappedOrderParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testQueryArrayParams() {
        remoteTest();
    }

    @Test
    @Override
    public void testQueryListParams() {
        remoteTest();
    }

    @Test
    @Override
    public void testQueryMapParams() {
        remoteTest();
    }

    @Override
    public void testQueryMapMissingParams() {
        remoteTest();
    }

    @Test
    @Override
    public void testQueryOrderParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testQueryNamedParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testQueryMappedNamedParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testQueryMappedOrderParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlArrayParams() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlListParams() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlMapParams() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlOrderParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlNamedParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlMappedNamedParam() {
        remoteTest();
    }

    @Test
    @Override
    public void testDmlMappedOrderParam() {
        remoteTest();
    }
}
