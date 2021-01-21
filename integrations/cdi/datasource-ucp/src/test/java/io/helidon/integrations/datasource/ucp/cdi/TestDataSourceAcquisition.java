/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.datasource.ucp.cdi;

import java.sql.Connection;
import java.sql.SQLException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ApplicationScoped
class TestDataSourceAcquisition {

    @Inject
    @Named("test")
    private DataSource test;
    
    private static SeContainer container;
    
    TestDataSourceAcquisition() {
        super();
    }

    @BeforeAll
    static void startCdi() {
        // we want to use the default configuration, no need to explicitly configure
        container = SeContainerInitializer.newInstance()
                .initialize();
    }

    @AfterAll
    static void stopCdi() {
        if (container != null) {
            container.close();
        }
    }

    private void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event) throws SQLException {
        assertNotNull(this.test);
        assertNotNull(this.test.toString());

        try (Connection connection = this.test.getConnection()) {
            assertNotNull(connection);
        }
    }

    @Test
    void testDataSourceAcquisition() {

    }
  
}
