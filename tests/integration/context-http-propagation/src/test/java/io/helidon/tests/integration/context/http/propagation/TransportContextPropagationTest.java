/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.context.http.propagation;

import io.helidon.common.config.Config;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/*
 * Tests both sides of the story - client and server.
 */
@ServerTest
class TransportContextPropagationTest {
    private static final HeaderName FIRST = HeaderNames.create("X-First");
    private static final String SECOND_CLASSIFIER = "io.helidon.tests.second";
    private static final HeaderName THIRD = HeaderNames.create("X-Third");
    private static final Config CONFIG = Config.create();

    private final WebClient client;

    TransportContextPropagationTest(WebServer webServer) {
        this.client = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .config(CONFIG.get("client"))
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder config) {
        config.config(CONFIG.get("server"));
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/propagation", TransportContextPropagationTest::propagationRoute);
    }

    @Test
    void testPropagationNone() {
        Context context = Context.create();
        context.register(THIRD.defaultCase(), "third-value");

        String response = Contexts.runInContext(context,
                                                () -> client.get("/propagation")
                                                        .requestEntity(String.class));

        assertThat(response, is("context-values:{EMPTY},{EMPTY},{EMPTY}"));
    }

    @Test
    void testPropagationFirst() {
        Context context = Context.create();
        context.register(FIRST.defaultCase(), "some-value");
        context.register(THIRD.defaultCase(), "third-value");

        String response = Contexts.runInContext(context,
                                                () -> client.get("/propagation")
                                                        .requestEntity(String.class));

        assertThat(response, is("context-values:some-value,{EMPTY},{EMPTY}"));
    }

    @Test
    void testPropagationBoth() {
        Context context = Context.create();
        context.register(FIRST.defaultCase(), "some-value");
        context.register(SECOND_CLASSIFIER, "other-value");
        context.register(THIRD.defaultCase(), "third-value");

        String response = Contexts.runInContext(context,
                                                () -> client.get("/propagation")
                                                        .requestEntity(String.class));

        assertThat(response, is("context-values:some-value,other-value,{EMPTY}"));
    }

    private static void propagationRoute(ServerRequest req, ServerResponse res) {
        /*
        We should get the propagated headers from the client into the current context.
        Validation of each part is done is respective modules (webclient and webserver context), so here we just make
        sure it all clicks together
         */
        Context context = req.context();

        String first = context.get(FIRST.defaultCase(), String.class)
                .orElse("{EMPTY}");
        String second = context.get(SECOND_CLASSIFIER, String.class)
                .orElse("{EMPTY}");
        String third = context.get(THIRD.defaultCase(), String.class)
                .orElse("{EMPTY}");

        res.send("context-values:" + first + "," + second + "," + third);
    }
}
