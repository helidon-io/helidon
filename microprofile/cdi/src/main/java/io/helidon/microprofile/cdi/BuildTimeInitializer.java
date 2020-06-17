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

import io.helidon.common.LogConfig;

/**
 * Holds the container singleton.
 * This class MUST NOT be referenced if {@link io.helidon.microprofile.cdi.HelidonContainerInitializer}
 *  is used, as otherwise you would load CDI twice.
 */
final class BuildTimeInitializer {
    private static volatile HelidonContainerImpl container;

    static {
        // need to initialize logging as soon as possible
        LogConfig.initClass();
        createContainer();
    }

    private BuildTimeInitializer() {
    }

    static synchronized HelidonContainerImpl get() {
        if (null == container) {
            createContainer();
        }

        return container;
    }

    static synchronized void reset() {
        container = null;
    }

    private static void createContainer() {
        // static initialization to support GraalVM native image
        container = HelidonContainerImpl.create();
        ContainerInstanceHolder.addListener(BuildTimeInitializer::reset);
    }
}
