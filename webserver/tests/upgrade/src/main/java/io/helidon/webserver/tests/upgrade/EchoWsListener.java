/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.upgrade;

import java.lang.System.Logger.Level;

import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

public class EchoWsListener implements WsListener {

    private static final System.Logger LOGGER = System.getLogger(EchoWsListener.class.getName());

    @Override
    public void onOpen(WsSession session) {
        LOGGER.log(Level.DEBUG, "SERVER: onOpen " + session);
        session.send("Hello this is server calling on open!", true);
    }

    @Override
    public void onMessage(WsSession session, String s, boolean last) {
        LOGGER.log(Level.DEBUG, "SERVER: onMessage " + session + " - " + s);
        session.send(s, last);
    }

    @Override
    public void onError(WsSession session, Throwable t) {
        LOGGER.log(Level.ERROR, "SERVER: onError", t);
    }
}
