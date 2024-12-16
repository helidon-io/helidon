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
package io.helidon.tests.integration.dbclient.pgsql;

import io.helidon.tests.integration.dbclient.common.DbClientITMain;
import io.helidon.tests.integration.dbclient.common.LocalTextContext;
import io.helidon.tests.integration.dbclient.common.ObservabilityTest;
import io.helidon.tests.integration.dbclient.common.ObservabilityTestImpl;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Local observability test.
 */
@ServerTest
final class PostgreSQLObservabilityLocalTestIT extends PostgreSQLLocalTest implements ObservabilityTest {

    private static Http1Client client;
    private static LocalTextContext<ObservabilityTestImpl> ctx;

    @BeforeAll
    static void beforeAll(Http1Client aClient) {
        client = aClient;
    }

    @SetUpServer
    static void setUp(WebServerConfig.Builder builder) {
        ctx = context((db, c) -> new ObservabilityTestImpl(db, c, () -> client));
        DbClientITMain.setup(ctx.db(), ctx.config(), builder);
    }

    @Test
    @Override
    public void testHttpHealthNoDetails() {
        ctx.delegate().testHttpHealthNoDetails();
    }

    @Test
    @Override
    public void testHttpHealthDetails() {
        ctx.delegate().testHttpHealthDetails();
    }

    @Test
    @Override
    public void testHttpMetrics() {
        ctx.delegate().testHttpMetrics();
    }

    @Test
    @Override
    public void testHealthCheck() {
        ctx.delegate().testHealthCheck();
    }

    @Test
    @Override
    public void testHealthCheckWithName() {
        ctx.delegate().testHealthCheckWithName();
    }

    @Override
    @SuppressWarnings("ALL")
    public void testHealthCheckWithCustomNamedDML() {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("ALL")
    public void testHealthCheckWithCustomDML() {
        throw new UnsupportedOperationException();
    }

    @Test
    @Override
    public void testHealthCheckWithCustomNamedQuery() {
        ctx.delegate().testHealthCheckWithCustomNamedQuery();
    }

    @Test
    @Override
    public void testHealthCheckWithCustomQuery() {
        ctx.delegate().testHealthCheckWithCustomQuery();
    }
}
