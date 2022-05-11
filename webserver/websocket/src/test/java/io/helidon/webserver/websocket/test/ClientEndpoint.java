/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

import java.io.IOException;

public class ClientEndpoint extends Endpoint {
    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {

        try {
            session.getBasicRemote().sendText("Hello this sent by Client!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        session.addMessageHandler(new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String partialMessage, boolean last) {
                System.out.println("Client received: " + partialMessage);
            }
        });
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("Client> closed "+closeReason.getReasonPhrase());
        super.onClose(session, closeReason);
    }

    @Override
    public void onError(Session session, Throwable thr) {
        System.out.println("Client> error "+thr.getMessage());
        thr.printStackTrace();
        super.onError(session, thr);
    }

    private void send(Session session, String msg){
        try {
            session.getBasicRemote().sendText(msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
