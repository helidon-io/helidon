/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
package io.helidon.webserver.websocket.test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;

import static io.helidon.webserver.websocket.test.UppercaseCodec.isDecoded;

/**
 * Class EchoEndpoint. Only one instance of this endpoint should be used at
 * a time. See static {@code EchoEndpoint#modifyHandshakeCalled}.
 */
@ServerEndpoint(
        value = "/echo",
        encoders = { UppercaseCodec.class },
        decoders = { UppercaseCodec.class },
        configurator = EchoEndpoint.ServerConfigurator.class
)
public class EchoEndpoint {
    private static final Logger LOGGER = Logger.getLogger(EchoEndpoint.class.getName());

    static AtomicBoolean modifyHandshakeCalled = new AtomicBoolean(false);

    /**
     * Verify that endpoint methods are running in a Helidon thread pool.
     *
     * @param session Websocket session.
     * @param logger A logger.
     * @throws IOException Exception during close.
     */
    private static void verifyRunningThread(Session session, Logger logger) throws IOException {
        Thread thread = Thread.currentThread();
        if (!thread.getName().contains("EventLoop")) {
            logger.warning("Websocket handler running in incorrect thread " + thread);
            session.close();
        }
    }

    /**
     * Verify session includes expected query params.
     *
     * @param session Websocket session.
     * @param logger A logger.
     * @throws IOException Exception during close.
     */
    private static void verifyQueryParams(Session session, Logger logger) throws IOException {
        if (!"user=Helidon".equals(session.getQueryString())) {
            logger.warning("Websocket session does not include required query params");
            session.close();
        }
        if (!session.getRequestParameterMap().get("user").get(0).equals("Helidon")) {
            logger.warning("Websocket session does not include required query parameter map");
            session.close();
        }
    }

    public static class ServerConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            LOGGER.info("ServerConfigurator called during handshake");
            super.modifyHandshake(sec, request, response);
            EchoEndpoint.modifyHandshakeCalled.set(true);

            // if not user Helidon, fail to authenticate, return reason and user header
            String user = getUserFromParams(request);
            if (!user.equals("Helidon") && response instanceof TyrusUpgradeResponse tyrusResponse) {
                tyrusResponse.setStatus(401);
                tyrusResponse.setReasonPhrase("Failed to authenticate");
                tyrusResponse.getHeaders().put("Endpoint", List.of("EchoEndpoint"));
            }
        }

        private String getUserFromParams(HandshakeRequest request) {
            List<String> values = request.getParameterMap().get("user");
            return values != null && !values.isEmpty() ? values.get(0) : "";
        }
    }

    @OnOpen
    public void onOpen(Session session) throws IOException {
        LOGGER.info("OnOpen called");
        verifyRunningThread(session, LOGGER);
        verifyQueryParams(session, LOGGER);
        if (!modifyHandshakeCalled.get()) {
            session.close();        // unexpected
        }
    }

    @OnMessage
    public void echo(Session session, String message) throws Exception {
        LOGGER.info("Endpoint OnMessage called '" + message + "'");
        verifyRunningThread(session, LOGGER);
        verifyQueryParams(session, LOGGER);
        if (!isDecoded(message)) {
            throw new InternalError("Message has not been decoded");
        }
        session.getBasicRemote().sendObject(message);       // calls encoder
    }

    @OnError
    public void onError(Throwable t) {
        LOGGER.info("OnError called");
        modifyHandshakeCalled.set(false);
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        LOGGER.info("OnClose called");
        verifyRunningThread(session, LOGGER);
        verifyQueryParams(session, LOGGER);
        modifyHandshakeCalled.set(false);
    }
}
