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

package io.helidon.tests.integration.packaging.se1;

import java.lang.System.Logger.Level;

import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

class WebSocketEndpoint implements WsListener {

    private static final System.Logger LOGGER = System.getLogger(WebSocketEndpoint.class.getName());
    private final StringBuilder sb = new StringBuilder();

    @Override
    public void onOpen(WsSession session) {
        // TODO improvement
        //LOGGER.log(Level.INFO, "Session " + session.getId());
        LOGGER.log(Level.INFO, "Session " + session);
    }

    @Override
    public void onMessage(WsSession session, String message, boolean last) {
        LOGGER.log(Level.INFO, "WS Receiving " + message);
        if (message.contains("SEND")) {
            session.send(message, false);
            sb.setLength(0);
        } else {
            sb.append(message);
        }
    }
}
