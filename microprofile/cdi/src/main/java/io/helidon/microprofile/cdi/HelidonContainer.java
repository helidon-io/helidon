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

import javax.enterprise.inject.se.SeContainer;

import io.helidon.common.context.Context;

/**
 * Helidon CDI Container, separates initialization and runtime phases of the bootstrapping.
 * Initialization is static to support GraalVM native image, start is controlled by application.
 * @see io.helidon.microprofile.cdi.Main
 */
public interface HelidonContainer {
    /**
     * Start the container. This will finish the lifecycle of CDI implementation.
     * If already started, this method is a noop.
     *
     * @return the started container
     */
    SeContainer start();

    /**
     * The root context of MP.
     *
     * @return context
     */
    Context context();

    /**
     * Get the (initialized or started) container.
     * @return the singleton instance of container
     */
    static HelidonContainer instance() {
        return ContainerInstanceHolder.get();
    }

    /**
     * Shutdown the container (and CDI).
     */
    void shutdown();
}
