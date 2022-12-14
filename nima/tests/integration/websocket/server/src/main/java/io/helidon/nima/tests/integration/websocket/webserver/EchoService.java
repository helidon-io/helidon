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

package io.helidon.nima.tests.integration.websocket.webserver;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Headers;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.WsSession;
import io.helidon.nima.websocket.WsUpgradeException;
import io.helidon.nima.websocket.webserver.WsUpgrader;

class EchoService implements WsListener {
    private final AtomicReference<CloseInfo> closed = new AtomicReference<>();

    private volatile String subProtocol;

    @Override
    public void onOpen(WsSession session) {
        String p = session.subProtocol().orElse(null);
        if (subProtocol != null && !subProtocol.equals(p)) {
            throw new InternalError("Invalid sub-protocol in session");
        }
    }

    @Override
    public void receive(WsSession session, String text, boolean last) {
        session.send(text, last);
    }

    @Override
    public void onClose(WsSession session, int status, String reason) {
        closed.set(new CloseInfo(status, reason));
    }

    @Override
    public Optional<Headers> onHttpUpgrade(HttpPrologue prologue, Headers headers) throws WsUpgradeException {
        WritableHeaders<?> upgradeHeaders = WritableHeaders.create();
        if (headers.contains(WsUpgrader.PROTOCOL)) {
            List<String> subProtocols = headers.get(WsUpgrader.PROTOCOL).allValues(true);
            if (subProtocols.contains("chat")) {
                upgradeHeaders.set(WsUpgrader.PROTOCOL, "chat");
                subProtocol = "chat";
            } else {
                throw new WsUpgradeException("Unable to negotiate WS sub-protocol");
            }
        } else {
            subProtocol = null;
        }
        if (headers.contains(WsUpgrader.EXTENSIONS)) {
            List<String> extensions = headers.get(WsUpgrader.EXTENSIONS).allValues(true);
            if (extensions.contains("nima")) {
                upgradeHeaders.set(WsUpgrader.EXTENSIONS, "nima");
            } else {
                throw new WsUpgradeException("Unable to negotiate WS extensions");
            }
        }
        return upgradeHeaders.size() > 0 ? Optional.of(upgradeHeaders) : Optional.empty();
    }

    void resetClosed() {
        closed.set(null);
    }

    CloseInfo closeInfo() {
        return closed.get();
    }

    record CloseInfo(int status, String reason) { }

}
