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
package io.helidon.dbclient.tests.common.tests;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;

import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.model.Critter.CRITTERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verify services handling.
 */
public abstract class InterceptorIT {

    private final Config config;

    public InterceptorIT(Config config) {
        this.config = config;
    }

    private static final class TestClientService implements DbClientService {

        private boolean called;

        private TestClientService() {
            this.called = false;
        }

        @Override
        public DbClientServiceContext statement(DbClientServiceContext context) {
            this.called = true;
            return context;
        }

        private boolean called() {
            return called;
        }

    }

    /**
     * Check that statement interceptor was called before statement execution.
     */
    @Test
    public void testStatementInterceptor() {
        TestClientService interceptor = new TestClientService();
        DbClient dbClient = DbClient.builder(config.get("db")).addService(interceptor).build();
        dbClient.execute()
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", CRITTERS.get(6).getName())
                .execute();
        assertThat(interceptor.called(), equalTo(true));
    }

}
