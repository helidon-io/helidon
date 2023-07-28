/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientContext;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link JdbcClientBuilder}.
 */
class JdbcClientBuilderTest {

    private static final DbClientServiceContext TEST_SERVICE_CONTEXT = Mockito.mock(DbClientServiceContext.class);

    private static final JdbcConnectionPool TEST_POOL = () -> null;

    @Test
    void testDbClientBuildWithService() {
        DbClient dbClient = new JdbcClientBuilder()
                .addService(context -> TEST_SERVICE_CONTEXT)
                .connectionPool(TEST_POOL)
                .build();
        DbClientContext clientContext = dbClient.unwrap(JdbcClient.class).context();
        List<DbClientService> services = clientContext.clientServices();
        // Services list must contain 1 item (configured one)
        assertThat(services.size(), is(1));
        DbClientService service = services.get(0);
        // Retrieve context from mocked service
        DbClientServiceContext serviceContext = service.statement(null);
        assertThat(serviceContext.dbType(), is(TEST_SERVICE_CONTEXT.dbType()));
    }

    // Check default JDBC parameters setter configuration
    @Test
    void testDefaultParametersSetterConfig() {
        DbClient dbClient = new JdbcClientBuilder()
                .addService(context -> TEST_SERVICE_CONTEXT)
                .connectionPool(TEST_POOL)
                .build();
        JdbcClientContext clientContext = dbClient.unwrap(JdbcClient.class).context();
        assertThat(clientContext.parametersConfig().useNString(), is(false));
        assertThat(clientContext.parametersConfig().useStringBinding(), is(true));
        assertThat(clientContext.parametersConfig().stringBindingSize(), is(1024));
        assertThat(clientContext.parametersConfig().useByteArrayBinding(), is(true));
        assertThat(clientContext.parametersConfig().timestampForLocalTime(), is(true));
        assertThat(clientContext.parametersConfig().setObjectForJavaTime(), is(true));
    }

    // Check custom JDBC parameters setter configuration from builder
    @Test
    void testCustomParametersSetterConfig() {
        DbClient dbClient = new JdbcClientBuilder()
                .addService(context -> TEST_SERVICE_CONTEXT)
                .connectionPool(TEST_POOL)
                .parametersSetter(JdbcParametersConfig.builder()
                                          .useNString(true)
                                          .useStringBinding(false)
                                          .stringBindingSize(4096)
                                          .useByteArrayBinding(false)
                                          .timestampForLocalTime(false)
                                          .setObjectForJavaTime(false)
                                          .build())
                .build();
        JdbcClientContext clientContext = dbClient.unwrap(JdbcClient.class).context();
        assertThat(clientContext.parametersConfig().useNString(), is(true));
        assertThat(clientContext.parametersConfig().useStringBinding(), is(false));
        assertThat(clientContext.parametersConfig().stringBindingSize(), is(4096));
        assertThat(clientContext.parametersConfig().useByteArrayBinding(), is(false));
        assertThat(clientContext.parametersConfig().timestampForLocalTime(), is(false));
        assertThat(clientContext.parametersConfig().setObjectForJavaTime(), is(false));
    }

    // Check custom JDBC parameters setter configuration from fonfig file
    @Test
    void testCustomFileParametersSetterConfig() {
        Config config = Config.create(ConfigSources.classpath("params.yaml"));
        DbClient dbClient = new JdbcClientBuilder()
                .config(config.get("db"))
                .addService(context -> TEST_SERVICE_CONTEXT)
                .connectionPool(TEST_POOL)
                .build();
        JdbcClientContext clientContext = dbClient.unwrap(JdbcClient.class).context();
        assertThat(clientContext.parametersConfig().useNString(), is(true));
        assertThat(clientContext.parametersConfig().useStringBinding(), is(false));
        assertThat(clientContext.parametersConfig().stringBindingSize(), is(8192));
        assertThat(clientContext.parametersConfig().useByteArrayBinding(), is(false));
        assertThat(clientContext.parametersConfig().timestampForLocalTime(), is(false));
        assertThat(clientContext.parametersConfig().setObjectForJavaTime(), is(false));
    }

}
