/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

import javax.sql.DataSource;

import io.helidon.microprofile.server.Server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceImpl;
import oracle.ucp.jdbc.PoolXADataSource;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.jboss.weld.proxy.WeldClientProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

@ApplicationScoped
class TestDataSourceAcquisition {

    @Inject
    @Named("test")
    private DataSource test;

    @Inject
    @Named("test")
    private UniversalConnectionPool testUcp;
  
    private Server server;

    TestDataSourceAcquisition() {
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
        assertThat(this.test.toString(), notNullValue());
        assertThat(this.testUcp, notNullValue());
        assertThat(this.testUcp.getName(), is("test"));
        final PoolDataSourceImpl contextualInstance =
            (PoolDataSourceImpl) ((WeldClientProxy) this.test).getMetadata().getContextualInstance();
        assertThat(contextualInstance.getDescription(), is("A test datasource"));
        assertThat(contextualInstance.getConnectionPoolName(), is("test"));
        Connection connection = null;
        try {
            connection = this.test.getConnection();
            assertThat(connection, notNullValue());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private void configure(@Observes @Named("test") final PoolDataSource pds) throws SQLException {
        assertThat(pds.getDescription(), nullValue());
        assertThat(pds.getClass().isSynthetic(), is(false));
        pds.setDescription("A test datasource");
    }

    private void configure(@Observes @Named("testxa") final PoolXADataSource pds) throws SQLException {
        assertThat(pds.getDescription(), nullValue());
        assertThat(pds.getClass().isSynthetic(), is(false));
        pds.setDescription("A test datasource");
    }

    @Test
    void testDataSourceAcquisition() {

    }

}
