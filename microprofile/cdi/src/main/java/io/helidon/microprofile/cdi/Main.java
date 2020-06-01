/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;

/**
 * This is the "master" main class of Helidon MP.
 * You can boot the Helidon MP instance using this class if you want do not need to modify
 * anything using builders.
 * <h1>Startup</h1>
 * <h2>Configuration</h2>
 * <p>
 * If this method is used, the following approach is taken for configuration:
 * <ul>
 *     <li>If there is a {@code meta-config} file in one of the supported formats (a config parser on the classpath,
 *      e.g. {@code yaml}, it will be used to set up configuration</li>
 *      <li>If there are any MicroProfile config sources, these will be added</li>
 *      <li>If there are any {@code META-INF/microprofile-config.properties} files on the classpath, these will be added</li>
 * </ul>
 * <h2>Logging</h2>
 * Helidon uses Java Util Logging. You can configure logging using:
 * <ul>
 *     <li>A system property {@code java.util.logging.config.class}</li>
 *     <li>A system property {@code java.util.logging.config.file}</li>
 *     <li>Placing file {@code logging.properties} on the current path</li>
 *     <li>Placing file {@code logging.properties} on the classpath</li>
 * </ul>
 */
public final class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final AtomicBoolean MAIN_CALLED = new AtomicBoolean();
    private static final HelidonContainer CONTAINER;

    static {
        // static initialization to support GraalVM native image
        CONTAINER = ContainerInstanceHolder.get();
    }

    private static volatile HelidonContainer inUse;

    private Main() {
    }

    /**
     * Start CDI.
     * This will also start all features on the classpath, such as JAX-RS.
     * This method should only be invoked when custom configuration is not used (in cases you rely on
     * config sources from classpath, or on meta-configuration).
     *
     * @param args command line arguments, currently ignored
     */
    public static void main(String[] args) {
        if (ContainerInstanceHolder.isReset()) {
            // in case somebody restarted the container, we need to get a new one
            inUse = ContainerInstanceHolder.get();
        } else {
            // use the statically initialized one
            inUse = CONTAINER;
        }

        inUse.start();
        MAIN_CALLED.set(true);
    }

    /**
     * Shutdown CDI container.
     */
    public static void shutdown() {
        if (null != inUse && MAIN_CALLED.get()) {
            LOGGER.finest("Shutting down container from cdi.Main");
            // re-set the main called, so if somebody starts, shuts down and starts again, we correctly evaluate the
            // shutdown method
            MAIN_CALLED.set(false);
            inUse.shutdown();
        } else {
            // now we need to cover cases where the main method was not invoked
            try {
                ((SeContainer) CDI.current()).close();
            } catch (IllegalStateException e) {
                LOGGER.log(Level.FINEST, "Failed to obtain a CDI instance to shut down, probably duplicate call", e);
            }
        }
    }
}
