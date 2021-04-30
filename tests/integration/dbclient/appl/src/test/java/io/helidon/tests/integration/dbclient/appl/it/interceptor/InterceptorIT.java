/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.dbclient.appl.it.interceptor;

import java.util.logging.Logger;

import io.helidon.tests.integration.tools.client.HelidonProcessRunner;
import io.helidon.tests.integration.tools.client.TestClient;
import io.helidon.tests.integration.tools.client.TestServiceClient;

import org.junit.jupiter.api.Test;

/**
 * Verify services handling.
 */
public class InterceptorIT {

    private static final Logger LOGGER = Logger.getLogger(InterceptorIT.class.getName());

    private final TestServiceClient testClient = TestClient.builder()
            .port(HelidonProcessRunner.HTTP_PORT)
            .service("Interceptor")
            .build();

    /**
     * Check that statement interceptor was called before statement execution.
     */
    @Test
    public void testStatementInterceptor() {
        LOGGER.fine("Running testStatementInterceptor");
        testClient
                .callServiceAndGetData("testStatementInterceptor");
    }

}
