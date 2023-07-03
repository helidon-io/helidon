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
import java.util.Map;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.CommonClientContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DbClientBuilderTest {

    // Service context mock
    private static final DbClientServiceContext TEST_SERVICE_CONTEXT = new DbClientServiceContext() {
        @Override
        public String dbType() {
            return "TEST_TYPE";
        }

        @Override
        public Context context() {
            return null;
        }

        @Override
        public String statementName() {
            return "TEST_NAME";
        }

        @Override
        public String statement() {
            return "SELECT 0";
        }

        @Override
        public Optional<List<Object>> indexedParameters() {
            return Optional.empty();
        }

        @Override
        public Optional<Map<String, Object>> namedParameters() {
            return Optional.empty();
        }

        @Override
        public boolean isIndexed() {
            return false;
        }

        @Override
        public boolean isNamed() {
            return false;
        }

        @Override
        public DbStatementType statementType() {
            return null;
        }

        @Override
        public DbClientServiceContext context(Context context) {
            return this;
        }

        @Override
        public DbClientServiceContext statementName(String newName) {
            return this;
        }

        @Override
        public DbClientServiceContext statement(String statement, List<Object> indexedParams) {
            return this;
        }

        @Override
        public DbClientServiceContext statement(String statement, Map<String, Object> namedParams) {
            return this;
        }

        @Override
        public DbClientServiceContext parameters(List<Object> indexedParameters) {
            return this;
        }

        @Override
        public DbClientServiceContext parameters(Map<String, Object> namedParameters) {
            return this;
        }

    };

    // Verify that mocked DbClientService and DbClientServiceContext is set properly into DbClient's CommonClientContext.
    @Test
    void testDbClientBuildWithService() {
        DbClient dbClient = new JdbcClientBuilder()
                .addService(context -> TEST_SERVICE_CONTEXT)
                .connectionPool(() -> null)
                .build();
        CommonClientContext clientContext = dbClient.unwrap(JdbcClient.class).context();
        List<DbClientService> services = clientContext.clientServices();
        // Services list must contain 1 item (configured one)
        assertThat(services.size(), is(1));
        DbClientService service = services.get(0);
        // Retrieve context from mocked service
        DbClientServiceContext serviceContext = service.statement(null);
        assertThat(serviceContext.dbType(), is(TEST_SERVICE_CONTEXT.dbType()));
    }

}
