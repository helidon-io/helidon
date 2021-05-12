/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.server.ServerApplicationConfig;

/**
 * Represents a websocket application with class and config endpoints.
 */
public final class WebSocketApplication {

    private Class<? extends ServerApplicationConfig> applicationClass;
    private Set<Class<?>> annotatedEndpoints;
    private Set<Class<? extends Endpoint>> programmaticEndpoints;
    private Set<Extension> extensions;

    private WebSocketApplication(Builder builder) {
        this.applicationClass = builder.applicationClass;
        this.annotatedEndpoints = builder.annotatedEndpoints;
        this.programmaticEndpoints = builder.programmaticEndpoints;
        this.extensions = builder.extensions;
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
     * Get access to application class, if present.
     *
     * @return Application class optional.
     */
    public Optional<Class<? extends ServerApplicationConfig>> applicationClass() {
        return Optional.ofNullable(applicationClass);
    }

    /**
     * Get list of programmatic endpoints.
     *
     * @return List of config endpoints.
     */
    public Set<Class<? extends Endpoint>> programmaticEndpoints() {
        return programmaticEndpoints;
    }

    /**
     * Get list of annotated endpoints.
     *
     * @return List of annotated endpoint.
     */
    public Set<Class<?>> annotatedEndpoints() {
        return annotatedEndpoints;
    }

    /**
     * Get list of installed extensions.
     *
     * @return List of installed extensions.
     */
    public Set<Extension> extensions() {
        return extensions;
    }

    /**
     * Fluent API builder to create {@link WebSocketApplication} instances.
     */
    public static class Builder {
        private static final Logger LOGGER = Logger.getLogger(WebSocketApplication.Builder.class.getName());

        private Class<? extends ServerApplicationConfig> applicationClass;
        private Set<Class<?>> annotatedEndpoints = new HashSet<>();
        private Set<Class<? extends Endpoint>> programmaticEndpoints = new HashSet<>();
        private Set<Extension> extensions = new HashSet<>();

        /**
         * Updates an application class in the builder. Clears all results from scanning.
         *
         * @param applicationClass The application class.
         * @return The builder.
         */
        Builder updateApplicationClass(Class<? extends ServerApplicationConfig> applicationClass) {
            if (this.applicationClass != null) {
                LOGGER.fine(() -> "Overriding websocket application using " + applicationClass);
            }
            this.applicationClass = applicationClass;
            return this;
        }

        /**
         * Set an application class in the builder.
         *
         * @param applicationClass The application class.
         * @return The builder.
         */
        public Builder applicationClass(Class<? extends ServerApplicationConfig> applicationClass) {
            if (this.applicationClass != null) {
                throw new IllegalStateException("At most one subclass of ServerApplicationConfig is permitted");
            }
            this.applicationClass = applicationClass;
            return this;
        }

        /**
         * Add single programmatic endpoint.
         *
         * @param programmaticEndpoint Programmatic endpoint.
         * @return The builder.
         */
        public Builder programmaticEndpoint(Class<? extends Endpoint> programmaticEndpoint) {
            programmaticEndpoints.add(programmaticEndpoint);
            return this;
        }

        /**
         * Add single annotated endpoint.
         *
         * @param annotatedEndpoint Annotated endpoint.
         * @return The builder.
         */
        public Builder annotatedEndpoint(Class<?> annotatedEndpoint) {
            annotatedEndpoints.add(annotatedEndpoint);
            return this;
        }

        /**
         * Add single extension.
         *
         * @param extension Extension.
         * @return The builder.
         */
        public Builder extension(Extension extension) {
            extensions.add(extension);
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
