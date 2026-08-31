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
package io.helidon.data.jdbc.tests.imperative.oracle;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Executes Oracle alternative-quoted literals through the imperative client.
 */
@SuppressWarnings("helidon:api:preview")
@Testcontainers(disabledWithoutDocker = true)
class OracleImperativeAlternativeQuoteTest {
    @Container
    static final GenericContainer<?> ORACLE = OracleImperativeTestSupport.ORACLE;

    /**
     * Proves imperative marker counting preserves a valid Oracle national
     * alternative-quoted literal before the statement reaches the real
     * driver.
     */
    @Test
    void executesNationalAlternativeQuotedLiteral() {
        TestConfigFactory.config(OracleImperativeTestSupport.config());
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            Qualifier provider = Qualifier.builder()
                    .typeName(Data.ProviderType.TYPE)
                    .value("jdbc")
                    .build();
            JdbcClient client = manager.registry()
                    .get(JdbcClient.class,
                         Qualifier.createNamed(Service.Named.DEFAULT_NAME),
                         provider);

            String value = client.create("SELECT NQ'{Oracle's ? :ignored}' FROM DUAL")
                    .map(String.class)
                    .one();

            assertThat(value, is("Oracle's ? :ignored"));
        } finally {
            manager.shutdown();
        }
    }
}
