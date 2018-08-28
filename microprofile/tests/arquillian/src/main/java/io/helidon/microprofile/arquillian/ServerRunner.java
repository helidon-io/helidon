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

package io.helidon.microprofile.arquillian;

import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.Path;
import javax.ws.rs.core.Application;

import io.helidon.config.Config;
import io.helidon.microprofile.server.Server;

/**
 * Runner to start server using reflection (as we need to run in a different classloader).
 */
class ServerRunner {
    private static final Logger LOGGER = Logger.getLogger(ServerRunner.class.getName());

    private Server server;

    ServerRunner() {
    }

    void start(Config config, HelidonContainerConfiguration containerConfig, Set<String> classNames, ClassLoader cl) {
        //cl.getResources("beans.xml")
        Server.Builder builder = Server.builder()
                .port(containerConfig.getPort())
                .config(config);

        for (String className : classNames) {
            handleClass(cl, className, builder);
        }

        server = builder.build();
        // this is a blocking operation, we will be released once the server is started
        // or it fails to start
        server.start();
        LOGGER.finest(() -> "Started server");
    }

    void stop() {
        if (null != server) {
            LOGGER.finest(() ->"Stopping server");
            server.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleClass(ClassLoader classLoader, String className, Server.Builder builder) {
        try {
            LOGGER.finest(() -> "Will attempt to add class: " + className);
            final Class<?> c = classLoader.loadClass(className);
            if (Application.class.isAssignableFrom(c)) {
                LOGGER.finest(() -> "Adding application class: " + c.getName());
                builder.addApplication((Class<? extends Application>) c);
            } else if (c.isAnnotationPresent(Path.class)) {
                LOGGER.finest(() -> "Adding resource class: " + c.getName());
                builder.addResourceClass(c);
            } else {
                LOGGER.finest(() -> "Class " + c.getName() + " is neither annotated with Path nor an application.");
            }
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            throw new HelidonArquillianException("Failed to load class to be added to server: " + className, e);
        }
    }
}
