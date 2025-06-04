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

import java.util.Arrays;

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
    /*
    These values are mapped in application.yaml
     */
    private static final HeaderName FIRST = HeaderNames.create("X-First");
    private static final String SECOND_CLASSIFIER = "io.helidon.tests.second";
    private static final HeaderName THIRD = HeaderNames.create("X-Third");
    private static final HeaderName TID_HEADER = HeaderNames.create("X-Helidon-Tid");
    private static final String TID_CLASSIFIER = "io.helidon.webclient.context.propagation.tid";
    private static final HeaderName CID_HEADER = HeaderNames.create("X-Helidon-Cid");
    private static final String CID_CLASSIFIER = "io.helidon.webclient.context.propagation.cid";
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

        validate("nothing should be propagated except defaults",
                 response,
                 "${EMPTY}",
                 "${EMPTY}",
                 "${EMPTY}",
                 "unknown",
                 "first,second");
    }

    @Test
    void testPropagationFirst() {
        Context context = Context.create();
        context.register(FIRST.defaultCase(), "some-value");
        context.register(THIRD.defaultCase(), "third-value");

        String response = Contexts.runInContext(context,
                                                () -> client.get("/propagation")
                                                        .requestEntity(String.class));

        validate("Both first should be propagated, arrays are default",
                 response,
                 "some-value",
                 "${EMPTY}",
                 "${EMPTY}",
                 "unknown",
                 "first,second");
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

        validate("Both first and second should be propagated, arrays are default",
                 response,
                 "some-value",
                 "other-value",
                 "${EMPTY}",
                 "unknown",
                 "first,second");
    }

    @Test
    void testPropagationArrays() {
        Context context = Context.create();
        context.register(CID_CLASSIFIER, new String[] {"cid-1", "cid-2"});
        context.register(TID_CLASSIFIER, new String[] {"tid-1"});

        String response = Contexts.runInContext(context,
                                                () -> client.get("/propagation")
                                                        .requestEntity(String.class));

        validate("only arrays should be propagated",
                 response,
                 "${EMPTY}",
                 "${EMPTY}",
                 "${EMPTY}",
                 "cid-1,cid-2",
                 "tid-1");
    }

    private static void validate(String message,
                                 String response,
                                 String first,
                                 String second,
                                 String third,
                                 String cid,
                                 String tid) {
        assertThat(message,
                   response,
                   is(first + "|" + second + "|" + third + "|" + cid + "|" + tid));
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
        String cid = context.get(CID_CLASSIFIER, String[].class)
                .map(Arrays::toString)
                .orElse("{EMPTY}");
        String tid = context.get(TID_CLASSIFIER, String[].class)
                .map(Arrays::toString)
                .orElse("{EMPTY}");

        res.send(first + "|" + second + "|" + third + "|" + cid + "|" + tid);
    }
}
