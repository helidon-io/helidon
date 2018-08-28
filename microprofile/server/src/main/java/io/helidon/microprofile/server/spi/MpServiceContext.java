/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.server.spi;

import java.util.List;

import javax.enterprise.inject.se.SeContainer;
import javax.ws.rs.core.Application;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;

import org.glassfish.jersey.server.ResourceConfig;

/**
 * A context to allow a microprofile server extension to configure additional
 * resources with Jersey, web server or use the CDI container (already booted).
 */
public interface MpServiceContext {
    /**
     * The Microprofile config instance used to configure this server.
     *
     * @return config instance
     */
    org.eclipse.microprofile.config.Config getConfig();

    /**
     * The Helidon config instance used to configure this server (will have same
     * properties as {@link #getConfig()}).
     *
     * @return Helidon config instance
     */
    Config getHelidonConfig();

    /**
     * Access existing applications configured with the server.
     *
     * @return list of all applications
     */
    List<ResourceConfig> getApplications();

    /**
     * Add a jersey application to the server. Context will be introspected from {@link javax.ws.rs.ApplicationPath} annotation.
     * You can also use {@link #addApplication(String, Application)}.
     *
     * @param application configured as needed
     */
    void addApplication(Application application);

    /**
     * Add a jersey application to the server with an explicit context path.
     *
     * @param contextRoot Context root to use for this application ({@link javax.ws.rs.ApplicationPath} is ignored)
     * @param application configured as needed
     */
    void addApplication(String contextRoot, Application application);

    /**
     * The CDI container used by this server (Weld SE container).
     *
     * @return The CDI container
     */
    SeContainer getCdiContainer();

    /**
     * Helidon web server configuration builder that can be used to re-configure the web server.
     *
     * @return web server configuration builder
     */
    ServerConfiguration.Builder getServerConfigBuilder();

    /**
     * Helidon webserver routing builder that can be used to add routes to the webserver.
     *
     * @return server routing builder
     */
    Routing.Builder getServerRoutingBuilder();

    /**
     * Register an instance of a class for later use (e.g. for outbound configuration).
     * This will replace existing instance of the class if it is already configured.
     *
     * @param key      class to register
     * @param instance instance of a class
     * @param <U>      type of the instance
     */
    <U> void register(Class<? extends U> key, U instance);

}
