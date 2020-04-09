/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import javax.enterprise.inject.se.SeContainerInitializer;

import org.junit.jupiter.api.BeforeAll;

/**
 * A test that registers a single annotated endpoint on the default WebSocket
 * context. See {@code WebSocketCdiExtension#DEFAULT_WEBSOCKET_PATH}.
 */
public class WebSocketEndpointTest extends WebSocketBaseTest {

    @BeforeAll
    static void initClass() {
        container = SeContainerInitializer.newInstance()
                .addBeanClasses(EchoEndpointAnnot.class)
                .initialize();
    }

    @Override
    public String context() {
        return "";
    }
}
