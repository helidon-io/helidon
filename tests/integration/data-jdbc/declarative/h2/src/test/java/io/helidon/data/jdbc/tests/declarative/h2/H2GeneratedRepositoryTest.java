/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests.declarative.h2;

import java.util.Optional;

import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.contract.AbstractGeneratedRepositoryContract;
import io.helidon.data.jdbc.tests.database.H2Database;
import io.helidon.data.jdbc.tests.support.DatabaseFixture;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Executes generated repository behavior against H2.
 */
class H2GeneratedRepositoryTest extends AbstractGeneratedRepositoryContract {
    @Override
    protected void beforeStartApplication() {
        TestConfigFactory.config(H2Database.config());
    }

    /**
     * Proves multiple generated methods can execute when application parameters
     * collide with generated fields and lambda parameters.
     */
    @Test
    void executesMultipleGeneratedMethodsWithNameCollisions() {
        beforeStartApplication();
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            DatabaseFixture database = manager.registry().get(DatabaseFixture.class);
            database.reset();
            H2GeneratedNameCollisionRepository repository =
                    manager.registry().get(H2GeneratedNameCollisionRepository.class);

            assertThat(repository.find("sql-find"), is("sql-find"));
            assertThat(repository.lookup("sql-lookup"), is("sql-lookup"));
            assertThat(repository.client("client"), is("client"));
            assertThat(repository.secondaryClient("secondary-client"), is("secondary-client"));

            long firstId = repository.insert("lambda-row", "lambda-value@example.test").orElseThrow();
            assertThat(database.committedByName("lambda-row"),
                       is(Optional.of(new ContactView(firstId,
                                                      "lambda-row",
                                                      Optional.of("lambda-value@example.test")))));
            long secondId = repository.insertSecondary("secondary-lambda-row",
                                                       "secondary-lambda-value@example.test").orElseThrow();
            assertThat(database.committedByName("secondary-lambda-row"),
                       is(Optional.of(new ContactView(secondId,
                                                      "secondary-lambda-row",
                                                      Optional.of("secondary-lambda-value@example.test")))));
        } finally {
            manager.shutdown();
        }
    }
}
