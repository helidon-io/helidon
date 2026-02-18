/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.websocket;

import io.helidon.common.types.TypeName;

/**
 * {@link io.helidon.common.types.TypeName type names} used by WebSocket.
 */
public final class WebSocketTypes {
    /**
     * Type name for {@code io.helidon.websocket.WebSocket.OnOpen}.
     */
    public static final TypeName ANNOTATION_ON_OPEN = TypeName.create("io.helidon.websocket.WebSocket.OnOpen");

    /**
     * Type name for {@code io.helidon.websocket.WebSocket.OnMessage}.
     */
    public static final TypeName ANNOTATION_ON_MESSAGE = TypeName.create("io.helidon.websocket.WebSocket.OnMessage");

    /**
     * Type name for {@code io.helidon.websocket.WebSocket.OnError}.
     */
    public static final TypeName ANNOTATION_ON_ERROR = TypeName.create("io.helidon.websocket.WebSocket.OnError");

    /**
     * Type name for {@code io.helidon.websocket.WebSocket.OnClose}.
     */
    public static final TypeName ANNOTATION_ON_CLOSE = TypeName.create("io.helidon.websocket.WebSocket.OnClose");

    /**
     * Type name for {@code io.helidon.websocket.WebSocket.OnHttpUpgrade}.
     */
    public static final TypeName ANNOTATION_ON_UPGRADE = TypeName.create("io.helidon.websocket.WebSocket.OnHttpUpgrade");

    /**
     * Type name for {@code io.helidon.websocket.WsListenerBase}.
     */
    public static final TypeName WS_LISTENER_BASE = TypeName.create("io.helidon.websocket.WsListenerBase");

    /**
     * Type name for {@code io.helidon.websocket.WsSession}.
     */
    public static final TypeName WS_SESSION = TypeName.create("io.helidon.websocket.WsSession");

    /**
     * Type name for {@code io.helidon.websocket.WsUpgradeException}.
     */
    public static final TypeName WS_UPGRADE_EXCEPTION =  TypeName.create("io.helidon.websocket.WsUpgradeException");

    private WebSocketTypes() {
    }
}
