/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests.interceptor;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.POKEMONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verify services handling.
 */
public class InterceptorIT {

    private static final class TestClientService implements DbClientService {

        private boolean called;
        private DbClientServiceContext context;

        private TestClientService() {
            this.called = false;
            this.context = null;
        }

        @Override
        public Single<DbClientServiceContext> statement(DbClientServiceContext context) {
            this.called = true;
            this.context = context;
            return Single.just(context);
        }

        private boolean called() {
            return called;
        }

        private DbClientServiceContext getContext() {
            return context;
        }

    }

    private static DbClient initDbClient(TestClientService interceptor) {
        Config dbConfig = AbstractIT.CONFIG.get("db");
        return DbClient.builder(dbConfig).addService(interceptor).build();
    }

    /**
     * Check that statement interceptor was called before statement execution.
     *
     */
    @Test
    public void testStatementInterceptor() {
        TestClientService interceptor = new TestClientService();
        DbClient dbClient = initDbClient(interceptor);
        Multi<DbRow> rows = dbClient.execute(exec -> exec
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", POKEMONS.get(6).getName())
                .execute());

        assertThat(interceptor.called(), equalTo(true));
    }

}
