/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import javax.sql.DataSource;

import io.helidon.microprofile.server.Server;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

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
        assertThat(builder, notNullValue());
        // The Helidon MicroProfile server implementation uses
        // ConfigProviderResolver#getConfig(ClassLoader) directly
        // instead of ConfigProvider#getConfig() so we follow suit
        // here for fidelity.
        builder.config(ConfigProviderResolver.instance().getConfig(Thread.currentThread().getContextClassLoader()));
        this.server = builder.build();
        assertThat(this.server, notNullValue());
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
        assertThat(this.test, notNullValue());
        assertThat(this.hikariTest, notNullValue());
        assertThat(this.test.toString(), notNullValue());
        assertThat(this.hikariTest.toString(), notNullValue());
        assertThat(this.hikariTest.getMetricsTrackerFactory(), instanceOf(MicroProfileMetricsTrackerFactory.class));
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
