/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;

import io.helidon.config.Config;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.server.Server;

import org.glassfish.jersey.server.ResourceConfig;

/**
 * Runner to start server using reflection (as we need to run in a different classloader).
 */
class ServerRunner {
    private static final Logger LOGGER = Logger.getLogger(ServerRunner.class.getName());

    private Server server;

    ServerRunner() {
    }

    private static String getContextRoot(Class<?> application) {
        ApplicationPath path = application.getAnnotation(ApplicationPath.class);
        if (null == path) {
            return null;
        }
        String value = path.value();
        return value.startsWith("/") ? value : "/" + value;
    }

    void start(Config config, HelidonContainerConfiguration containerConfig, Set<String> classNames, ClassLoader cl) {
        //cl.getResources("beans.xml")
        Server.Builder builder = Server.builder()
                .port(containerConfig.getPort())
                .config(MpConfig.builder()
                                .config(config)
                                .addDiscoveredSources()
                                .build());

        handleClasses(cl, classNames, builder, containerConfig.getAddResourcesToApps());

        server = builder.build();
        // this is a blocking operation, we will be released once the server is started
        // or it fails to start
        server.start();
        LOGGER.finest(() -> "Started server");
    }

    void stop() {
        if (null != server) {
            LOGGER.finest(() -> "Stopping server");
            server.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleClasses(ClassLoader classLoader,
                               Set<String> classNames,
                               Server.Builder builder,
                               boolean addResourcesToApps) {

        // first create classes end get all applications
        List<Class<?>> applicationClasses = new LinkedList<>();
        List<Class<?>> resourceClasses = new LinkedList<>();

        for (String className : classNames) {
            try {
                LOGGER.finest(() -> "Will attempt to add class: " + className);
                final Class<?> c = classLoader.loadClass(className);
                if (Application.class.isAssignableFrom(c)) {
                    LOGGER.finest(() -> "Adding application class: " + c.getName());
                    applicationClasses.add(c);
                } else if (c.isAnnotationPresent(Path.class) && !c.isInterface()) {
                    LOGGER.finest(() -> "Adding resource class: " + c.getName());
                    resourceClasses.add(c);
                } else {
                    LOGGER.finest(() -> "Class " + c.getName() + " is neither annotated with Path nor an application.");
                }
            } catch (NoClassDefFoundError | ClassNotFoundException e) {
                throw new HelidonArquillianException("Failed to load class to be added to server: " + className, e);
            }
        }

        // workaround for tck-jwt-auth
        if (addResourcesToApps) {
            for (Class<?> aClass : applicationClasses) {
                ResourceConfig resourceConfig = ResourceConfig.forApplicationClass((Class<? extends Application>) aClass);
                resourceClasses.forEach(resourceConfig::register);
                builder.addApplication(getContextRoot(aClass), resourceConfig);
            }
            if (applicationClasses.isEmpty()) {
                for (Class<?> resourceClass : resourceClasses) {
                    builder.addResourceClass(resourceClass);
                }
            }
        }
    }
}
