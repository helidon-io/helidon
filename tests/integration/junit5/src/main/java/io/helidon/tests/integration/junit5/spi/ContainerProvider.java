/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5.spi;

import java.util.Optional;

import io.helidon.tests.integration.junit5.ContainerConfig;
import io.helidon.tests.integration.junit5.SuiteExtensionProvider;

/**
 * Helidon Database Client integration tests Docker container provider interface.
 */
public interface ContainerProvider extends SuiteExtensionProvider {

    /**
     * Docker image from {@link io.helidon.tests.integration.junit5.ContainerTest} annotation.
     * This method is called during {@link ContainerProvider} initialization phase.
     * Implementing class must store this value and handle it properly.
     *
     * @param image name of the Docker image including label or {@link Optional#empty()} when not defined
     */
    void image(Optional<String> image);

    /**
     * Build docker container configuration.
     * Default container configuration must be set in this method.
     *
     * @return docker container configuration builder with default configuration set
     */
    void setup();

    /**
     * Docker container configuration builder with default configuration set.
     * This is the {@link ContainerConfig.Builder} instance passed
     * to {@link io.helidon.tests.integration.junit5.SetUpContainer} annotated method
     * in related {@link SuiteProvider} implementing class.
     *
     * @return container configuration builder with default configuration
     */
    ContainerConfig.Builder builder();

    /**
     * Start Docker container.
     * Calling this method may change provided value of Docker container configuration.
     */
    void start();

    /**
     * Stop Docker container.
     */
    void stop();

    /**
     * Cast {@link io.helidon.tests.integration.junit5.SuiteExtensionProvider} as {@link ContainerProvider}.
     * Implementing class should override this method and add itself.
     *
     * @param cls {@link io.helidon.tests.integration.junit5.SuiteExtensionProvider} child interface or implementing class
     * @return this instance cast to {@link ContainerProvider} and optionally implementing class
     * @param <T> target casting type
     */
    @Override
    default <T extends SuiteExtensionProvider> T as(Class<T> cls) {
        if (cls == ContainerProvider.class) {
            return cls.cast(this);
        }
        throw new IllegalArgumentException(
                String.format("Cannot cast this ContainerProvider implementation as %s", cls.getName()));
    }

}
