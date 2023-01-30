/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.websocket.Endpoint;
import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerApplicationConfig;

/**
 * Represents a websocket application with class and config endpoints.
 */
public final class TyrusApplication {

    private Set<Class<? extends ServerApplicationConfig>> applicationClasses;
    private final Set<Class<?>> annotatedEndpoints;
    private final Set<Class<? extends Endpoint>> programmaticEndpoints;
    private final Set<Extension> extensions;

    private TyrusApplication(Builder builder) {
        this.applicationClasses = builder.applicationClasses;
        this.annotatedEndpoints = builder.annotatedEndpoints;
        this.programmaticEndpoints = builder.programmaticEndpoints;
        this.extensions = builder.extensions;
    }

    /**
     * A new fluent API builder to create a customized {@link TyrusApplication}.
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
        return applicationClasses.isEmpty() ? Optional.empty() : Optional.of(applicationClasses.iterator().next());
    }

    /**
     * Get access to all application classes. Possibly an empty set.
     *
     * @return Immutable set of application classes.
     */
    public Set<Class<? extends ServerApplicationConfig>> applicationClasses() {
        return Collections.unmodifiableSet(applicationClasses);
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
     * Fluent API builder to create {@link TyrusApplication} instances.
     */
    public static class Builder {
        private static final System.Logger LOGGER = System.getLogger(TyrusApplication.Builder.class.getName());

        private final Set<Class<? extends ServerApplicationConfig>> applicationClasses = new HashSet<>();
        private final Set<Class<?>> annotatedEndpoints = new HashSet<>();
        private final Set<Class<? extends Endpoint>> programmaticEndpoints = new HashSet<>();
        private final Set<Extension> extensions = new HashSet<>();

        /**
         * Updates an application class in the builder. Clears all results from scanning.
         *
         * @param applicationClass The application class.
         * @return The builder.
         */
        Builder updateApplicationClass(Class<? extends ServerApplicationConfig> applicationClass) {
            if (!applicationClasses.isEmpty()) {
                LOGGER.log(Level.DEBUG, () -> "Overriding websocket applications using " + applicationClass);
            }
            applicationClasses.clear();
            applicationClasses.add(applicationClass);
            return this;
        }

        /**
         * Set an application class in the builder.
         *
         * @param applicationClass The application class.
         * @return The builder.
         */
        public Builder applicationClass(Class<? extends ServerApplicationConfig> applicationClass) {
            applicationClasses.add(applicationClass);
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
        public TyrusApplication build() {
            return new TyrusApplication(this);
        }
    }
}
