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
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
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

    private WebSocketApplication.Builder appBuilder = WebSocketApplication.builder();

    /**
     * Collect application class extending {@code ServerApplicationConfig}.
     *
     * @param applicationClass Application class.
     */
    private void applicationClass(@Observes ProcessAnnotatedType<? extends ServerApplicationConfig> applicationClass) {
        LOGGER.info(() -> "Application class found " + applicationClass.getAnnotatedType().getJavaClass());
        appBuilder.applicationClass(applicationClass.getAnnotatedType().getJavaClass());
    }

    /**
     * Overrides a websocket application class.
     *
     * @param applicationClass Application class.
     */
    public void applicationClass(Class<? extends ServerApplicationConfig> applicationClass) {
        LOGGER.info(() -> "Using manually set application class  " + applicationClass);
        appBuilder.updateApplicationClass(applicationClass);
    }

    /**
     * Collect annotated endpoints.
     *
     * @param endpoint The endpoint.
     */
    private void endpointClasses(@Observes @WithAnnotations(ServerEndpoint.class) ProcessAnnotatedType<?> endpoint) {
        LOGGER.info(() -> "Annotated endpoint found " + endpoint.getAnnotatedType().getJavaClass());
        appBuilder.annotatedEndpoint(endpoint.getAnnotatedType().getJavaClass());
    }

    /**
     * Collects programmatic endpoints .
     *
     * @param endpoint The endpoint.
     */
    private void endpointConfig(@Observes ProcessAnnotatedType<? extends Endpoint> endpoint) {
        LOGGER.info(() -> "Programmatic endpoint found " + endpoint.getAnnotatedType().getJavaClass());
        appBuilder.programmaticEndpoint(endpoint.getAnnotatedType().getJavaClass());
    }

    /**
     * Provides access to websocket application.
     *
     * @return Application.
     */
    public WebSocketApplication toWebSocketApplication() {
        return appBuilder.build();
    }
}
