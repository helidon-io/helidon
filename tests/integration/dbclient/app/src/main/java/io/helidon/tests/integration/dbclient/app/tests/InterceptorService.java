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

import java.util.Map;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.harness.AppResponse;
import io.helidon.tests.integration.harness.RemoteTestException;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

/**
 * Service to verify services handling.
 */
public class InterceptorService extends AbstractService {

    private final DbClientService interceptor;

    /**
     * Creates an instance of web resource to verify services handling.
     *
     * @param dbClient    DbClient instance
     * @param statements  statements from configuration file
     * @param interceptor DbClientService interceptor instance used in test
     */
    public InterceptorService(DbClient dbClient, Map<String, String> statements, DbClientService interceptor) {
        super(dbClient, statements);
        this.interceptor = interceptor;
    }

    /**
     * Test db service.
     */
    public static final class TestClientService implements DbClientService {

        private boolean called;

        /**
         * Create a new instance.
         */
        public TestClientService() {
            this.called = false;
        }

        @Override
        public DbClientServiceContext statement(DbClientServiceContext context) {
            this.called = true;
            return context;
        }
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/testStatementInterceptor", this::testStatementInterceptor);
    }

    // Check that statement interceptor was called before statement execution.
    private void testStatementInterceptor(ServerRequest request, ServerResponse response) {
        JsonArrayBuilder jab = dbClient().execute().createNamedQuery("select-pokemon-named-arg")
                .addParam("name", Pokemon.POKEMONS.get(6).getName())
                .execute()
                .map(row -> row.as(JsonObject.class))
                .collect(Json::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::addAll);
        if (((TestClientService) interceptor).called) {
            response.send(AppResponse.okStatus(jab.build()));
        } else {
            throw new RemoteTestException("Interceptor service was not called");
        }
    }
}
