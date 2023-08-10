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

package io.helidon.webserver.tests.websocket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;

/**
 * A sequence of {@link WsAction}s collectively referred to as a conversation.
 */
class WsConversation {

    private final Collection<WsAction> actions = new ArrayList<>();

    void addAction(WsAction action) {
        actions.add(action);
    }

    Iterator<WsAction> actions() {
        return actions.iterator();
    }

    WsConversation dual() {
        return fromDual(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Iterator<WsAction> it = actions();
        while (it.hasNext()) {
            sb.append(it.next().toString()).append("\n");
        }
        return sb.toString();
    }

    static WsConversation createRandom(int size) {
        WsConversation conversation = new WsConversation();
        for (int i = 0; i < size; i++) {
            conversation.addAction(WsAction.createRandom());
        }
        return conversation;
    }

    static WsConversation fromDual(WsConversation other) {
        WsConversation conversation = new WsConversation();
        Iterator<WsAction> it = other.actions();
        while (it.hasNext()) {
            conversation.addAction(it.next().dual());
        }
        return conversation;
    }

    static WsConversation fromString(String s) {
        WsConversation conversation = new WsConversation();
        StringTokenizer st = new StringTokenizer(s, "\n");
        while (st.hasMoreTokens()) {
            conversation.addAction(WsAction.fromString(st.nextToken()));
        }
        return conversation;
    }
}
