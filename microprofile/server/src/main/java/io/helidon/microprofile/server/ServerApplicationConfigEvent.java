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

package io.helidon.microprofile.server;

/**
 * Class ServerApplicationConfigEvent. This wrapper class is used by the server
 * to inform the {@code WebSocketCdiExtension} that an application class has been
 * set in its builder and that any scanned classes should be ignored.
 */
public class ServerApplicationConfigEvent {

    /**
     * Application class of type {@code Class<? extends ServerApplicationConfig>},
     * we use {@code Class<?>} to avoid a static dependency with the WebSocket API.
     */
    private final Class<?> applicationClass;

    /**
     * Construct an event given a websocket application class.
     *
     * @param applicationClass Application class.
     */
    public ServerApplicationConfigEvent(Class<?> applicationClass) {
        this.applicationClass = applicationClass;
    }

    /**
     * Get access to application class.
     *
     * @return Application class.
     */
    public Class<?> applicationClass() {
        return applicationClass;
    }
}
