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
package io.helidon.tests.integration.dbclient.common;

import java.lang.reflect.InvocationTargetException;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.http.InternalServerException;
import io.helidon.http.NotFoundException;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static java.util.Objects.requireNonNullElse;

/**
 * Service to expose all tests.
 */
final class TestService implements HttpService {

    private final TransactionTestImpl transaction;
    private final StatementTestImpl statement;
    private final SimpleTestImpl simple;
    private final MiscTestImpl misc;
    private final ObservabilityTestImpl observability;

    TestService(DbClient db, Config config) {
        transaction = new TransactionTestImpl(db, config);
        statement = new StatementTestImpl(db, config);
        simple = new SimpleTestImpl(db, config);
        misc = new MiscTestImpl(db, config);
        observability = new ObservabilityTestImpl(db, config, this::client);
    }

    private Http1Client client() {
        return Contexts.context()
                .flatMap(c -> c.get(WebServer.class))
                .map(server -> Http1Client.builder()
                        .baseUri("http://localhost:" + server.port())
                        .build())
                .orElseThrow(() -> new IllegalStateException("Unable to get server instance from current context"));
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/transaction/{testName}", this::transaction)
                .get("/statement/{testName}", this::statement)
                .get("/simple/{testName}", this::simple)
                .get("/misc/{testName}", this::misc)
                .get("/observability/{testName}", this::observability);
    }

    private void transaction(ServerRequest req, ServerResponse res) {
        invokeTest(transaction, req, res);
    }

    private void statement(ServerRequest req, ServerResponse res) {
        invokeTest(statement, req, res);
    }

    private void simple(ServerRequest req, ServerResponse res) {
        invokeTest(simple, req, res);

    }

    private void misc(ServerRequest req, ServerResponse res) {
        invokeTest(misc, req, res);
    }

    private void observability(ServerRequest req, ServerResponse res) {
        invokeTest(observability, req, res);
    }

    private static void invokeTest(Object o, ServerRequest req, ServerResponse res) {
        String testName = req.path().pathParameters().get("testName");
        try {
            o.getClass().getMethod(testName).invoke(o);
            res.send("OK");
        } catch (IllegalAccessException ex) {
            throw new InternalServerException(ex.getMessage(), ex);
        } catch (InvocationTargetException ex) {
            requireNonNullElse(ex.getCause(), ex).printStackTrace(System.err);
            throw new InternalServerException(ex.getMessage(), ex);
        } catch (NoSuchMethodException ignored) {
            throw new NotFoundException("Test not found: " + testName);
        }
    }
}
