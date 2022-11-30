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
import io.helidon.nima.websocket.webserver.WsUpgradeProvider;

class EchoService implements WsListener {
    private final AtomicReference<CloseInfo> closed = new AtomicReference<>();

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
        if (headers.contains(WsUpgradeProvider.PROTOCOL)) {
            List<String> subProtocols = headers.get(WsUpgradeProvider.PROTOCOL).allValues(true);
            if (subProtocols.contains("chat")) {
                Headers upgradeHeaders = WritableHeaders.create().set(WsUpgradeProvider.PROTOCOL, "chat");
                return Optional.of(upgradeHeaders);     // negotiated
            } else {
                throw new WsUpgradeException("Unable to negotiate WS sub-protocol");
            }
        }
        return Optional.empty();
    }

    void resetClosed() {
        closed.set(null);
    }

    CloseInfo closeInfo() {
        return closed.get();
    }

    record CloseInfo(int status, String reason) { }

}
