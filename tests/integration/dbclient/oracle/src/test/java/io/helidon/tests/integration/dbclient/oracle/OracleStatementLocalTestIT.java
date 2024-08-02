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
import io.helidon.tests.integration.dbclient.common.StatementTest;
import io.helidon.tests.integration.dbclient.common.StatementTestImpl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Local statement test.
 */
final class OracleStatementLocalTestIT extends OracleLocalTest implements StatementTest {

    static LocalTextContext<StatementTestImpl> ctx;

    @BeforeAll
    static void setUp() {
        ctx = context(StatementTestImpl::new);
    }

    @Test
    @Override
    public void testCreateNamedQueryNonExistentStmt() {
        ctx.delegate().testCreateNamedQueryNonExistentStmt();
    }

    @Test
    @Override
    public void testCreateNamedQueryNamedAndOrderArgsWithoutArgs() {
        ctx.delegate().testCreateNamedQueryNamedAndOrderArgsWithoutArgs();
    }

    @Test
    @Override
    public void testCreateNamedQueryNamedAndOrderArgsWithArgs() {
        ctx.delegate().testCreateNamedQueryNamedAndOrderArgsWithArgs();
    }

    @Test
    @Override
    public void testCreateNamedQueryNamedArgsSetOrderArg() {
        ctx.delegate().testCreateNamedQueryNamedArgsSetOrderArg();
    }

    @Test
    @Override
    public void testCreateNamedQueryOrderArgsSetNamedArg() {
        ctx.delegate().testCreateNamedQueryOrderArgsSetNamedArg();
    }

    @Test
    @Override
    public void testGetArrayParams() {
        ctx.delegate().testGetArrayParams();
    }

    @Test
    @Override
    public void testGetListParams() {
        ctx.delegate().testGetListParams();
    }

    @Test
    @Override
    public void testGetMapParams() {
        ctx.delegate().testGetMapParams();
    }

    @Test
    @Override
    public void testGetOrderParam() {
        ctx.delegate().testGetOrderParam();
    }

    @Test
    @Override
    public void testGetNamedParam() {
        ctx.delegate().testGetNamedParam();
    }

    @Test
    @Override
    public void testGetMappedNamedParam() {
        ctx.delegate().testGetMappedNamedParam();
    }

    @Test
    @Override
    public void testGetMappedOrderParam() {
        ctx.delegate().testGetMappedOrderParam();
    }

    @Test
    @Override
    public void testQueryArrayParams() {
        ctx.delegate().testQueryArrayParams();
    }

    @Test
    @Override
    public void testQueryListParams() {
        ctx.delegate().testQueryListParams();
    }

    @Test
    @Override
    public void testQueryMapParams() {
        ctx.delegate().testQueryMapParams();
    }

    @Test
    @Override
    public void testQueryMapMissingParams() {
        ctx.delegate().testQueryMapMissingParams();
    }

    @Test
    @Override
    public void testQueryOrderParam() {
        ctx.delegate().testQueryOrderParam();
    }

    @Test
    @Override
    public void testQueryNamedParam() {
        ctx.delegate().testQueryNamedParam();
    }

    @Test
    @Override
    public void testQueryMappedNamedParam() {
        ctx.delegate().testQueryMappedNamedParam();
    }

    @Test
    @Override
    public void testQueryMappedOrderParam() {
        ctx.delegate().testQueryMappedOrderParam();
    }

    @Test
    @Override
    public void testDmlArrayParams() {
        ctx.delegate().testDmlArrayParams();
    }

    @Test
    @Override
    public void testDmlListParams() {
        ctx.delegate().testDmlListParams();
    }

    @Test
    @Override
    public void testDmlMapParams() {
        ctx.delegate().testDmlMapParams();
    }

    @Test
    @Override
    public void testDmlOrderParam() {
        ctx.delegate().testDmlOrderParam();
    }

    @Test
    @Override
    public void testDmlNamedParam() {
        ctx.delegate().testDmlNamedParam();
    }

    @Test
    @Override
    public void testDmlMappedNamedParam() {
        ctx.delegate().testDmlMappedNamedParam();
    }

    @Test
    @Override
    public void testDmlMappedOrderParam() {
        ctx.delegate().testDmlMappedOrderParam();
    }
}
