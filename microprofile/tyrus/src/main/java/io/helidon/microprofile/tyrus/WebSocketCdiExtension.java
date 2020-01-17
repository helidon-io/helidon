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

package io.helidon.microprofile.tyrus;

import java.util.logging.Logger;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.websocket.server.ServerEndpoint;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;

/**
 * Configure Tyrus related things.
 */
public class WebSocketCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(WebSocketCdiExtension.class.getName());

    static {
        HelidonFeatures.register(HelidonFlavor.MP, "WebSocket");
    }

    private WebSocketApplication.Builder applicationBuilder = WebSocketApplication.builder();

    private void endpoints(@Observes @WithAnnotations(ServerEndpoint.class) ProcessAnnotatedType<?> applicationType) {
        LOGGER.info(() -> "Annotated endpoint found " + applicationType.getAnnotatedType().getJavaClass());
        applicationBuilder.endpointClass(applicationType.getAnnotatedType().getJavaClass());
    }

    /**
     * Provides access to websocket application builder.
     *
     * @return Application builder.
     */
    public WebSocketApplication.Builder toWebSocketApplication() {
        return applicationBuilder;
    }
}
