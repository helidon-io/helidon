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
package io.helidon.docs.se;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
class TestingSnippets {

    // stub
    class Main {
        static void routing(HttpRouting.Builder builder) {
        }
    }

    // tag::snippet_1[]
    @ServerTest // <1>
    class MyServerTest {

        final Http1Client client;

        MyServerTest(Http1Client client) { // <2>
            this.client = client;
        }

        @SetUpRoute // <3>
        static void routing(HttpRouting.Builder builder) {
            Main.routing(builder);
        }

        @Test
        void testRootRoute() { // <4>
            try (Http1ClientResponse response = client
                    .get("/greet")
                    .request()) { // <5>
                assertThat(response.status(), is(Status.OK_200)); // <6>
            }
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @RoutingTest // <1>
    class MyRoutingTest {

        final Http1Client client;

        MyRoutingTest(DirectClient client) { // <2>
            this.client = client;
        }

        @SetUpRoute // <3>
        static void routing(HttpRouting.Builder builder) {
            Main.routing(builder);
        }

        @Test
        void testRootRoute() { // <4>
            try (Http1ClientResponse response = client
                    .get("/greet")
                    .request()) { // <5>
                JsonObject json = response.as(JsonObject.class); // <6>
                assertThat(json.getString("message"), is("Hello World!"));
            }
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @ServerTest
    class WsSocketTest {

        static final ServerSideListener WS_LISTENER = new ServerSideListener();
        final WsClient wsClient; // <1>

        WsSocketTest(WsClient wsClient) {
            this.wsClient = wsClient;
        }

        @SetUpRoute
        static void routing(WsRouting.Builder ws) { // <2>
            ws.endpoint("/testWs", WS_LISTENER);
        }

        @Test
        void testWsEndpoint() { // <3>
            ClientSideListener clientListener = new ClientSideListener();
            wsClient.connect("/testWs", clientListener); // <4>
            assertThat(clientListener.message, is("ws")); // <5>
        }
    }
    // end::snippet_3[]

    // tag::snippet_4[]
    static class ClientSideListener implements WsListener {
        volatile String message;
        volatile Throwable error;

        @Override
        public void onOpen(WsSession session) { // <1>
            session.send("hello", true);
        }

        @Override
        public void onMessage(WsSession session, String text, boolean last) { // <2>
            message = text;
            session.close(WsCloseCodes.NORMAL_CLOSE, "End");
        }

        @Override
        public void onError(WsSession session, Throwable t) { // <3>
            error = t;
        }
    }
    // end::snippet_4[]

    // tag::snippet_5[]
    static class ServerSideListener implements WsListener {
        volatile String message;

        @Override
        public void onMessage(WsSession session, String text, boolean last) { // <1>
            message = text;
            session.send("ws", true);
        }
    }
    // end::snippet_5[]
}
