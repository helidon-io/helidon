/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.websocket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Class MessageBoardTest.
 */
@ServerTest
public class MessageBoardTest {
    private static final Logger LOGGER = Logger.getLogger(MessageBoardTest.class.getName());

    private static final String[] MESSAGES = {"Whisky", "Tango", "Foxtrot"};
    private final WsClient wsClient;
    private final Http1Client client;

    MessageBoardTest(Http1Client client, WsClient wsClient) {
        this.client = client;
        this.wsClient = wsClient;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        Main.setup(server);
    }

    @Test
    public void testBoard() throws InterruptedException {
        // Post messages using REST resource
        for (String message : MESSAGES) {
            try (Http1ClientResponse response = client.post("/rest/board").submit(message)) {
                assertThat(response.status(), is(Status.NO_CONTENT_204));
                LOGGER.info("Posting message '" + message + "'");
            }
        }

        // Now connect to message board using WS and them back

        CountDownLatch messageLatch = new CountDownLatch(MESSAGES.length);

        wsClient.connect("/websocket/board", new WsListener() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                LOGGER.info("Client OnMessage called '" + text + "'");
                messageLatch.countDown();
                if (messageLatch.getCount() == 0) {
                    session.close(WsCloseCodes.NORMAL_CLOSE, "Bye!");
                }
            }

            @Override
            public void onOpen(WsSession session) {
                session.send("SEND", false);
            }
        });

        // Wait until all messages are received
        assertThat(messageLatch.await(1000000, TimeUnit.SECONDS), is(true));
    }
}
