/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
 * <h2>Logging</h2>>
 * Helidon uses Java Util Logging. You can configure logging using:
 * <ul>
 *     <li>A system property {@code java.util.logging.config.class}</li>
 *     <li>A system property {@code java.util.logging.config.file}</li>
 *     <li>Placing file {@code logging.properties} on the current path</li>
 *     <li>Placing file {@code logging.properties} on the classpath</li>
 * </ul>
 */
public final class Main {
    private static final HelidonContainer CONTAINER;

    static {
        // static initialization to support GraalVM native image
        CONTAINER = ContainerInstanceHolder.get();
    }

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
        CONTAINER.start();
    }

    /**
     * Shutdown CDI container.
     */
    public static void shutdown() {
        CONTAINER.shutdown();
    }
}
