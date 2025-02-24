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

import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tests.integration.jpa.common.DeleteTest;
import io.helidon.tests.integration.jpa.common.DeleteTestImpl;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Local delete test.
 */
@HelidonTest
class PostgreSQLDeleteLocalTestIT extends PostgreSQLLocalTest implements DeleteTest {

    @Inject
    private DeleteTestImpl delegate;

    @Test
    @Override
    public void testDeleteEntity() {
        delegate.testDeleteEntity();
    }

    @Test
    @Override
    public void testDeleteJPQL() {
        delegate.testDeleteJPQL();
    }

    @Test
    @Override
    public void testDeleteCriteria() {
        delegate.testDeleteCriteria();
    }

    @Test
    @Override
    public void testDeleteCity() {
        delegate.testDeleteCity();
    }
}
