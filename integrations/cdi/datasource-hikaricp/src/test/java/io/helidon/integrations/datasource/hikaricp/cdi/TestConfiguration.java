/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.datasource.hikaricp.cdi;

import java.sql.Connection;
import java.sql.SQLException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import io.helidon.microprofile.server.Server;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ApplicationScoped
class TestConfiguration {

    @Inject
    @Named("test")
    private DataSource test;

    @Inject
    @Named("test")
    private HikariDataSource hikariTest;

    private Server server;

    TestConfiguration() {
        super();
    }

    @BeforeEach
    void startServer() {
        this.stopServer();
        final Server.Builder builder = Server.builder();
        assertNotNull(builder);
        // The Helidon MicroProfile server implementation uses
        // ConfigProviderResolver#getConfig(ClassLoader) directly
        // instead of ConfigProvider#getConfig() so we follow suit
        // here for fidelity.
        builder.config(ConfigProviderResolver.instance().getConfig(Thread.currentThread().getContextClassLoader()));
        this.server = builder.build();
        assertNotNull(this.server);
        this.server.start();
    }

    @AfterEach
    void stopServer() {
        if (this.server != null) {
            this.server.stop();
            this.server = null;
        }
    }

    private void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event) throws SQLException {
        assertNotNull(this.test);
        assertNotNull(this.hikariTest);
        assertNotNull(this.test.toString());
        assertNotNull(this.hikariTest.toString());
        assertTrue(this.hikariTest.getMetricsTrackerFactory() instanceof MicroProfileMetricsTrackerFactory);
        Connection connection = null;
        try {
            connection = this.test.getConnection();
        } finally {
            connection.close();
        }
    }

    @Test
    void testConfiguration() {

    }

}
