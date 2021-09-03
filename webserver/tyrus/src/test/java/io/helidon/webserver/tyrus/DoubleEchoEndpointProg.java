/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.tyrus;

import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import static io.helidon.webserver.tyrus.UppercaseCodec.isDecoded;

/**
 * Class DoubleEchoEndpointProg. Using WebSocket programmatic API.
 */
public class DoubleEchoEndpointProg extends Endpoint {
    private static final Logger LOGGER = Logger.getLogger(DoubleEchoEndpointProg.class.getName());

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                if (!isDecoded(message)) {
                    throw new InternalError("Message has not been decoded");
                }
                try {
                    session.getBasicRemote().sendObject(message + message);     // calls encoder
                } catch (Exception e) {
                    LOGGER.info(e.getMessage());
                }
            }
        });
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        super.onClose(session, closeReason);
    }

    @Override
    public void onError(Session session, Throwable thr) {
        super.onError(session, thr);
    }
}
