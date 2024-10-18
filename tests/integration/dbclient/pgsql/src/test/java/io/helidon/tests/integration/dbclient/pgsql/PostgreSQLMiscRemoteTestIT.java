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

import io.helidon.tests.integration.dbclient.common.MiscTest;

import org.junit.jupiter.api.Test;

/**
 * Remote misc test.
 */
final class PostgreSQLMiscRemoteTestIT extends PostgreSQLRemoteTest implements MiscTest {

    PostgreSQLMiscRemoteTestIT() {
        super("/test/misc");
    }

    @Test
    @Override
    public void testFlowControl() {
        remoteTest();
    }

    @Test
    @Override
    public void testStatementInterceptor() {
        remoteTest();
    }

    @Test
    @Override
    public void testInsertWithOrderMapping() {
        remoteTest();
    }

    @Test
    @Override
    public void testInsertWithNamedMapping() {
        remoteTest();
    }

    @Test
    @Override
    public void testUpdateWithOrderMapping() {
        remoteTest();
    }

    @Test
    @Override
    public void testUpdateWithNamedMapping() {
        remoteTest();
    }

    @Test
    @Override
    public void testDeleteWithOrderMapping() {
        remoteTest();
    }

    @Test
    @Override
    public void testDeleteWithNamedMapping() {
        remoteTest();
    }

    @Test
    @Override
    public void testQueryWithMapping() {
        remoteTest();
    }

    @Test
    @Override
    public void testGetWithMapping() {
        remoteTest();
    }
}
