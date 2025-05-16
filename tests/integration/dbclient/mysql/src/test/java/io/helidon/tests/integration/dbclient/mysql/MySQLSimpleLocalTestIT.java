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
package io.helidon.tests.integration.dbclient.mysql;

import io.helidon.tests.integration.dbclient.common.LocalTextContext;
import io.helidon.tests.integration.dbclient.common.SimpleTest;
import io.helidon.tests.integration.dbclient.common.SimpleTestImpl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Local simple test.
 */
final class MySQLSimpleLocalTestIT extends MySQLLocalTest implements SimpleTest {

    static LocalTextContext<SimpleTestImpl> ctx;

    @BeforeAll
    static void setUp() {
        ctx = context(SimpleTestImpl::new);
    }

    @Test
    @Override
    public void testCreateNamedDeleteStrStrOrderArgs() {
        ctx.delegate().testCreateNamedDeleteStrStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedDeleteStrNamedArgs() {
        ctx.delegate().testCreateNamedDeleteStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedDeleteStrOrderArgs() {
        ctx.delegate().testCreateNamedDeleteStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateDeleteNamedArgs() {
        ctx.delegate().testCreateDeleteNamedArgs();
    }

    @Test
    @Override
    public void testCreateDeleteOrderArgs() {
        ctx.delegate().testCreateDeleteOrderArgs();
    }

    @Test
    @Override
    public void testNamedDeleteOrderArgs() {
        ctx.delegate().testNamedDeleteOrderArgs();
    }

    @Test
    @Override
    public void testDeleteOrderArgs() {
        ctx.delegate().testDeleteOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithInsertStrStrNamedArgs() {
        ctx.delegate().testCreateNamedDmlWithInsertStrStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithInsertStrNamedArgs() {
        ctx.delegate().testCreateNamedDmlWithInsertStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithInsertStrOrderArgs() {
        ctx.delegate().testCreateNamedDmlWithInsertStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateDmlWithInsertNamedArgs() {
        ctx.delegate().testCreateDmlWithInsertNamedArgs();
    }

    @Test
    @Override
    public void testCreateDmlWithInsertOrderArgs() {
        ctx.delegate().testCreateDmlWithInsertOrderArgs();
    }

    @Test
    @Override
    public void testNamedDmlWithInsertOrderArgs() {
        ctx.delegate().testNamedDmlWithInsertOrderArgs();
    }

    @Test
    @Override
    public void testDmlWithInsertOrderArgs() {
        ctx.delegate().testDmlWithInsertOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        ctx.delegate().testCreateNamedDmlWithUpdateStrStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        ctx.delegate().testCreateNamedDmlWithUpdateStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        ctx.delegate().testCreateNamedDmlWithUpdateStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateDmlWithUpdateNamedArgs() {
        ctx.delegate().testCreateDmlWithUpdateNamedArgs();
    }

    @Test
    @Override
    public void testCreateDmlWithUpdateOrderArgs() {
        ctx.delegate().testCreateDmlWithUpdateOrderArgs();
    }

    @Test
    @Override
    public void testNamedDmlWithUpdateOrderArgs() {
        ctx.delegate().testNamedDmlWithUpdateOrderArgs();
    }

    @Test
    @Override
    public void testDmlWithUpdateOrderArgs() {
        ctx.delegate().testDmlWithUpdateOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithDeleteStrStrOrderArgs() {
        ctx.delegate().testCreateNamedDmlWithDeleteStrStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithDeleteStrNamedArgs() {
        ctx.delegate().testCreateNamedDmlWithDeleteStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedDmlWithDeleteStrOrderArgs() {
        ctx.delegate().testCreateNamedDmlWithDeleteStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateDmlWithDeleteNamedArgs() {
        ctx.delegate().testCreateDmlWithDeleteNamedArgs();
    }

    @Test
    @Override
    public void testCreateDmlWithDeleteOrderArgs() {
        ctx.delegate().testCreateDmlWithDeleteOrderArgs();
    }

    @Test
    @Override
    public void testNamedDmlWithDeleteOrderArgs() {
        ctx.delegate().testNamedDmlWithDeleteOrderArgs();
    }

    @Test
    @Override
    public void testDmlWithDeleteOrderArgs() {
        ctx.delegate().testDmlWithDeleteOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedGetStrStrNamedArgs() {
        ctx.delegate().testCreateNamedGetStrStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedGetStrNamedArgs() {
        ctx.delegate().testCreateNamedGetStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedGetStrOrderArgs() {
        ctx.delegate().testCreateNamedGetStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateGetNamedArgs() {
        ctx.delegate().testCreateGetNamedArgs();
    }

    @Test
    @Override
    public void testCreateGetOrderArgs() {
        ctx.delegate().testCreateGetOrderArgs();
    }

    @Test
    @Override
    public void testNamedGetStrOrderArgs() {
        ctx.delegate().testNamedGetStrOrderArgs();
    }

    @Test
    @Override
    public void testGetStrOrderArgs() {
        ctx.delegate().testGetStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedInsertStrStrNamedArgs() {
        ctx.delegate().testCreateNamedInsertStrStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedInsertStrNamedArgs() {
        ctx.delegate().testCreateNamedInsertStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedInsertStrOrderArgs() {
        ctx.delegate().testCreateNamedInsertStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateInsertNamedArgs() {
        ctx.delegate().testCreateInsertNamedArgs();
    }

    @Test
    @Override
    public void testCreateInsertOrderArgs() {
        ctx.delegate().testCreateInsertOrderArgs();
    }

    @Test
    @Override
    public void testNamedInsertOrderArgs() {
        ctx.delegate().testNamedInsertOrderArgs();
    }

    @Test
    @Override
    public void testInsertOrderArgs() {
        ctx.delegate().testInsertOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedQueryStrStrOrderArgs() {
        ctx.delegate().testCreateNamedQueryStrStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedQueryStrNamedArgs() {
        ctx.delegate().testCreateNamedQueryStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedQueryStrOrderArgs() {
        ctx.delegate().testCreateNamedQueryStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateQueryNamedArgs() {
        ctx.delegate().testCreateQueryNamedArgs();
    }

    @Test
    @Override
    public void testCreateQueryOrderArgs() {
        ctx.delegate().testCreateQueryOrderArgs();
    }

    @Test
    @Override
    public void testNamedQueryOrderArgs() {
        ctx.delegate().testNamedQueryOrderArgs();
    }

    @Test
    @Override
    public void testQueryOrderArgs() {
        ctx.delegate().testQueryOrderArgs();
    }

    @Test
    @Override
    public void testCreateNamedUpdateStrStrNamedArgs() {
        ctx.delegate().testCreateNamedUpdateStrStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedUpdateStrNamedArgs() {
        ctx.delegate().testCreateNamedUpdateStrNamedArgs();
    }

    @Test
    @Override
    public void testCreateNamedUpdateStrOrderArgs() {
        ctx.delegate().testCreateNamedUpdateStrOrderArgs();
    }

    @Test
    @Override
    public void testCreateUpdateNamedArgs() {
        ctx.delegate().testCreateUpdateNamedArgs();
    }

    @Test
    @Override
    public void testCreateUpdateOrderArgs() {
        ctx.delegate().testCreateUpdateOrderArgs();
    }

    @Test
    @Override
    public void testNamedUpdateNamedArgs() {
        ctx.delegate().testNamedUpdateNamedArgs();
    }

    @Test
    @Override
    public void testUpdateOrderArgs() {
        ctx.delegate().testUpdateOrderArgs();
    }
}
