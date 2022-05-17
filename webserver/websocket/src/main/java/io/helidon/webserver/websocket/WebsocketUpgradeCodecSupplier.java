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
 *
 */

package io.helidon.webserver.websocket;

import java.util.Optional;

import io.helidon.webserver.Router;
import io.helidon.webserver.UpgradeCodecSupplier;

/**
 * Service providing WebSocket upgrade codec for Helidon webserver.
 */
public class WebsocketUpgradeCodecSupplier implements UpgradeCodecSupplier {

    /**
     * Creates a new {@link WebsocketUpgradeCodecSupplier}.
     * @deprecated Only intended for service loader, do not instantiate
     */
    @Deprecated
    public WebsocketUpgradeCodecSupplier() {
    }

    @Override
    public CharSequence clearTextProtocol() {
        return "websocket";
    }

    @Override
    public Optional<String> tlsProtocol() {
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, A> R upgradeCodec(A httpServerCodec,
                                 Router router,
                                 int maxContentLength) {
        WebSocketRouting routing = router.routing(WebSocketRouting.class, null);
        if (routing != null) {
            return (R) new WebSocketUpgradeCodec(routing);
        }
        return null;
    }
}
