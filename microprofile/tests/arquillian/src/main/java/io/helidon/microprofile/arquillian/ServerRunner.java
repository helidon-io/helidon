/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.helidon.microprofile.server.Server;
import io.helidon.microprofile.server.ServerCdiExtension;

import jakarta.ws.rs.ApplicationPath;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

/**
 * Runner to start server using reflection (as we need to run in a different classloader).
 * As we invoke this from a different classloader, the class must be public.
 */
public class ServerRunner {
    private static final Logger LOGGER = Logger.getLogger(ServerRunner.class.getName());

    private Server server;

    /**
     * Needed for reflection.
     */
    public ServerRunner() {
    }

    private static String getContextRoot(Class<?> application) {
        ApplicationPath path = application.getAnnotation(ApplicationPath.class);
        if (null == path) {
            return null;
        }
        String value = path.value();
        return value.startsWith("/") ? value : "/" + value;
    }

    /**
     * Start the server. Needed for reflection.
     *
     * @param config configuration
     * @param port port to start the server on
     */
    public void start(Config config, int port) {
        // attempt a stop
        stop();

        ConfigProviderResolver.instance()
                .registerConfig(config, Thread.currentThread().getContextClassLoader());

        server = Server.builder()
                .port(port)
                .config(config)
                .build()
                // this is a blocking operation, we will be released once the server is started
                // or it fails to start
                .start();

        LOGGER.finest(() -> "Started server");
    }

    /**
     * Stop the server. Needed for reflection.
     */
    public void stop() {
        if (null != server) {
            LOGGER.finest(() -> "Stopping server");
            server.stop();
        }
    }

    /**
     * Cleanup after abort.
     */
    public void abortedCleanup() {
        stop();
        try {
            Field running = ServerCdiExtension.class.getDeclaredField("IN_PROGRESS_OR_RUNNING");
            running.setAccessible(true);
            AtomicBoolean isRunning = (AtomicBoolean) running.get(null);
            isRunning.set(false);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
