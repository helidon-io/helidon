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

import java.util.ArrayList;
import java.util.List;

import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

/**
 * Represents a websocket application with class and config endpoints.
 */
public final class WebSocketApplication {

    private List<Class<?>> endpointClasses;
    private List<Class<? extends ServerEndpointConfig>> endpointConfigs;

    /**
     * Creates a websocket application from classes and configs.
     *
     * @param classesOrConfigs Classes and configs.
     * @return A websocket application.
     */
    @SuppressWarnings("unchecked")
    public static WebSocketApplication create(Class<?>... classesOrConfigs) {
        Builder builder = new Builder();
        for (Class<?> c : classesOrConfigs) {
            if (ServerEndpointConfig.class.isAssignableFrom(c)) {
                builder.endpointConfig((Class<? extends ServerEndpointConfig>) c);
            } else if (c.isAnnotationPresent(ServerEndpoint.class)) {
                builder.endpointClass(c);
            } else {
                throw new IllegalArgumentException("Unable to create WebSocket application using " + c);
            }
        }
        return builder.build();
    }

    private WebSocketApplication(Builder builder) {
        this.endpointConfigs = builder.endpointConfigs;
        this.endpointClasses = builder.endpointClasses;
    }

    /**
     * A new fluent API builder to create a customized {@link WebSocketApplication}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get list of config endpoints.
     *
     * @return List of config endpoints.
     */
    public List<Class<? extends ServerEndpointConfig>> endpointConfigs() {
        return endpointConfigs;
    }

    /**
     * Get list of endpoint classes.
     *
     * @return List of endpoint classes.
     */
    public List<Class<?>> endpointClasses() {
        return endpointClasses;
    }

    /**
     * Fluent API builder to create {@link WebSocketApplication} instances.
     */
    public static class Builder {

        private List<Class<?>> endpointClasses = new ArrayList<>();
        private List<Class<? extends ServerEndpointConfig>> endpointConfigs = new ArrayList<>();

        /**
         * Add single config endpoint.
         *
         * @param endpointConfig Endpoint config.
         * @return The builder.
         */
        public Builder endpointConfig(Class<? extends ServerEndpointConfig> endpointConfig) {
            endpointConfigs.add(endpointConfig);
            return this;
        }

        /**
         * Add single endpoint class.
         *
         * @param endpointClass Endpoint class.
         * @return The builder.
         */
        public Builder endpointClass(Class<?> endpointClass) {
            endpointClasses.add(endpointClass);
            return this;
        }

        /**
         * Builds application.
         *
         * @return The application.
         */
        public WebSocketApplication build() {
            return new WebSocketApplication(this);
        }
    }
}
