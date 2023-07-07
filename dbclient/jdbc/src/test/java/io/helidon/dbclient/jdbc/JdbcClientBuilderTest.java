/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientContext;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JdbcClientBuilderTest {

    private static final DbClientServiceContext TEST_SERVICE_CONTEXT = Mockito.mock(DbClientServiceContext.class);

    @Test
    void testDbClientBuildWithService() {
        DbClient dbClient = new JdbcClientBuilder()
                .addService(context -> TEST_SERVICE_CONTEXT)
                .connectionPool(() -> null)
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

}
