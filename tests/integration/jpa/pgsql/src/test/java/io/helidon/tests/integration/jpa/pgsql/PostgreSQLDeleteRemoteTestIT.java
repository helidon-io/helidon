/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.pgsql;

import io.helidon.tests.integration.jpa.common.DeleteTest;

import org.junit.jupiter.api.Test;

/**
 * Remote delete test.
 */
class PostgreSQLDeleteRemoteTestIT extends PostgreSQLRemoteTest implements DeleteTest {

    PostgreSQLDeleteRemoteTestIT() {
        super("/test/delete");
    }

    @Test
    @Override
    public void testDeleteEntity() {
        remoteTest();
    }

    @Test
    @Override
    public void testDeleteJPQL() {
        remoteTest();
    }

    @Test
    @Override
    public void testDeleteCriteria() {
        remoteTest();
    }

    @Test
    @Override
    public void testDeleteCity() {
        remoteTest();
    }
}
