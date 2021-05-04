/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.appl.interceptor;

import java.util.Map;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.appl.AbstractService;
import io.helidon.tests.integration.dbclient.appl.model.Pokemon;
import io.helidon.tests.integration.tools.service.AppResponse;
import io.helidon.tests.integration.tools.service.RemoteTestException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import static io.helidon.tests.integration.tools.service.AppResponse.exceptionStatus;

/**
 * Web resource to verify services handling.
 */
public class InterceptorService extends AbstractService {

    private static final Logger LOGGER = Logger.getLogger(InterceptorService.class.getName());

    private final DbClientService interceptor;

    /**
     * Creates an instance of web resource to verify services handling.
     *
     * @param dbClient DbClient instance
     * @param statements statements from configuration file
     * @param interceptor DbClientService interceptor instance used in test
     */
    public InterceptorService(final DbClient dbClient, final Map<String, String> statements, final DbClientService interceptor) {
        super(dbClient, statements);
        this.interceptor = interceptor;
    }

    // Interceptor is registered in AppMain
    public static final class TestClientService implements DbClientService {

        private boolean called;
        private DbClientServiceContext context;

        public TestClientService() {
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

    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/testStatementInterceptor", this::testStatementInterceptor);
    }

    // Check that statement interceptor was called before statement execution.
    private void testStatementInterceptor(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine(() -> "Running Interceptor.testStatementInterceptor on server");
        Multi<DbRow> rows = dbClient().execute(exec -> exec
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", Pokemon.POKEMONS.get(6).getName())
                .execute());
        final JsonArrayBuilder jab = Json.createArrayBuilder();
        rows
                .forEach(row -> jab.add(row.as(JsonObject.class)))
                .onComplete(() -> {
                    if (((TestClientService) interceptor).called()) {
                        response.send(AppResponse.okStatus(jab.build()));
                    } else {
                        response.send(exceptionStatus(new RemoteTestException("Interceptor service was not called")));
                    }
                })
                .exceptionally(t -> {
                    response.send(exceptionStatus(t));
                    return null;
                });
    }

}
