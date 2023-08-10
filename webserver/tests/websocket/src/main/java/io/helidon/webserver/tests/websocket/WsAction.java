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

import java.util.Random;
import java.util.StringTokenizer;

/**
 * A websocket action of sending or receiving a text or binary message.
 * A websocket conversation comprises a list of these actions.
 */
class WsAction {

    enum Operation {
        SND, RCV
    }

    enum OperationType {
        TEXT, BINARY
    }

    Operation op;
    OperationType opType;
    String message;

    WsAction() {
    }

    WsAction(Operation op, OperationType opType, String message) {
        this.op = op;
        this.opType = opType;
        this.message = message;
    }

    WsAction dual() {
        return new WsAction(op == Operation.SND ? Operation.RCV : Operation.SND, opType, message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WsAction wsAction)) {
            return false;
        }
        return op == wsAction.op && opType == wsAction.opType && message.equals(wsAction.message);
    }

    @Override
    public String toString() {
        return op + " " + opType + " '" + message + "'";
    }

    static WsAction fromString(String s) {
        WsAction action = new WsAction();
        StringTokenizer st = new StringTokenizer(s, " '", true);
        action.op = Operation.valueOf(st.nextToken());
        assert st.nextToken().equals(" ");
        action.opType = OperationType.valueOf(st.nextToken());
        assert st.nextToken().equals(" ");
        assert st.nextToken().equals("'");
        StringBuilder sb = new StringBuilder();
        while (st.hasMoreTokens()) {
            String t = st.nextToken();
            if (t.equals("'")) {
                break;
            }
            sb.append(t);
        }
        action.message = sb.toString();
        return action;
    }

    static WsAction createRandom() {
        Random random = new Random();
        WsAction action = new WsAction();
        action.op = random.nextInt(2) == 0 ? Operation.RCV : Operation.SND;
        action.opType = random.nextInt(2) == 0 ? OperationType.BINARY : OperationType.TEXT;
        action.message = randomString(random.nextInt(10, 20), random);
        return action;
    }

    private static String randomString(int length, Random random) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        return random.ints(leftLimit, rightLimit + 1)
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
