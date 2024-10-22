/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.logging.common.LogConfig;

/**
 * Holds the container singleton.
 * This class MUST NOT be referenced if {@link io.helidon.microprofile.cdi.HelidonContainerInitializer}
 *  is used, as otherwise you would load CDI twice.
 */
final class BuildTimeInitializer {
    private static volatile HelidonContainerImpl container;

    private static final Lock CONTAINER_ACCESS = new ReentrantLock(true);

    static {
        // need to initialize logging as soon as possible
        LogConfig.initClass();

        try {
            createContainer();
        } catch (Throwable e) {
            System.getLogger(BuildTimeInitializer.class.getName())
                    .log(System.Logger.Level.ERROR, "Failed to initialize CDI container", e);
            throw e;
        }
    }

    private BuildTimeInitializer() {
    }

    static HelidonContainerImpl get() {
        return accessContainer(() -> {
            if (null == container) {
                createContainer();
            }

            return container;
        });
    }

    static void reset() {
        accessContainer(() -> {
            container = null;
            return null;
        });
    }

    private static void createContainer() {
        // static initialization to support GraalVM native image
        accessContainer(() -> {
            container = HelidonContainerImpl.create();
            ContainerInstanceHolder.addListener(BuildTimeInitializer::reset);
            return null;
        });
    }

    private static <T> T accessContainer(Supplier<T> operation) {
        CONTAINER_ACCESS.lock();
        try {
            return operation.get();
        } finally {
            CONTAINER_ACCESS.unlock();
        }
    }
}
