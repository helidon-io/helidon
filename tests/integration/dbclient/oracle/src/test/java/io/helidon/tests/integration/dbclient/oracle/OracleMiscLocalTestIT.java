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
package io.helidon.tests.integration.dbclient.oracle;

import io.helidon.tests.integration.dbclient.common.LocalTextContext;
import io.helidon.tests.integration.dbclient.common.MiscTest;
import io.helidon.tests.integration.dbclient.common.MiscTestImpl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Local misc test.
 */
final class OracleMiscLocalTestIT extends OracleLocalTest implements MiscTest {

    static LocalTextContext<MiscTestImpl> ctx;

    @BeforeAll
    static void setUp() {
        ctx = context(MiscTestImpl::new);
    }

    @Test
    @Override
    public void testFlowControl() {
        ctx.delegate().testFlowControl();
    }

    @Test
    @Override
    public void testStatementInterceptor() {
        ctx.delegate().testStatementInterceptor();
    }

    @Test
    @Override
    public void testInsertWithOrderMapping() {
        ctx.delegate().testInsertWithOrderMapping();
    }

    @Test
    @Override
    public void testInsertWithNamedMapping() {
        ctx.delegate().testInsertWithNamedMapping();
    }

    @Test
    @Override
    public void testUpdateWithOrderMapping() {
        ctx.delegate().testUpdateWithOrderMapping();
    }

    @Test
    @Override
    public void testUpdateWithNamedMapping() {
        ctx.delegate().testUpdateWithNamedMapping();
    }

    @Test
    @Override
    public void testDeleteWithOrderMapping() {
        ctx.delegate().testDeleteWithOrderMapping();
    }

    @Test
    @Override
    public void testDeleteWithNamedMapping() {
        ctx.delegate().testDeleteWithNamedMapping();
    }

    @Test
    @Override
    public void testQueryWithMapping() {
        ctx.delegate().testQueryWithMapping();
    }

    @Test
    @Override
    public void testGetWithMapping() {
        ctx.delegate().testGetWithMapping();
    }
}
