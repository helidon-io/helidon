/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.app.tests;

import io.helidon.tests.integration.harness.TestClient;
import io.helidon.tests.integration.harness.TestServiceClient;

import org.junit.jupiter.api.Test;

/**
 * Test {@link io.helidon.dbclient.DbClientService}.
 */
class InterceptorIT {

    private static final System.Logger LOGGER = System.getLogger(InterceptorIT.class.getName());

    private final TestServiceClient testClient;

    InterceptorIT(int serverPort) {
        this.testClient = TestClient.builder()
                .port(serverPort)
                .service("Interceptor")
                .build();
    }

    /**
     * Check that statement interceptor was called before statement execution.
     */
    @Test
    void testStatementInterceptor() {
        LOGGER.log(System.Logger.Level.DEBUG, () -> String.format("Running %s.%s on client", getClass().getSimpleName(), "testStatementInterceptor"));
        testClient.callServiceAndGetData("testStatementInterceptor");
    }
}
