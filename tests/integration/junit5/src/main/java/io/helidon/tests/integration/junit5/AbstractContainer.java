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
package io.helidon.tests.integration.junit5;

import java.lang.reflect.Type;
import java.util.Optional;

import io.helidon.tests.integration.junit5.spi.ContainerProvider;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractContainer implements ContainerProvider, SuiteResolver {

    private static final System.Logger LOGGER = System.getLogger(AbstractContainer.class.getName());

    /** @noinspection OptionalUsedAsFieldOrParameterType*/
    private Optional<String> image;
    /** @noinspection OptionalUsedAsFieldOrParameterType*/
    private ContainerConfig.Builder builder;
    private ContainerConfig config;
    private GenericContainer<?> container;
    private ContainerInfo info;
    private SuiteContext suiteContext;

    protected AbstractContainer() {
        image = Optional.empty();
        builder = null;
        config = null;
        container = null;
        info = null;
        suiteContext = null;
    }

    @Override
    public void image(Optional<String> image) {
        this.image = image;
    }

    @Override
    public void suiteContext(SuiteContext suiteContext) {
        this.suiteContext = suiteContext;
    }

    @Override
    public ContainerConfig.Builder builder() {
        return builder;
    }

    protected Optional<String> image() {
        return image;
    }

    protected ContainerConfig config() {
        return config;
    }

    protected GenericContainer<?> container() {
        return container;
    }

    protected ContainerInfo info() {
        return info;
    }

    protected SuiteContext suiteContext() {
        return suiteContext;
    }

    @Override
    public void setup() {
        builder = ContainerConfig.builder();
                image.ifPresent(name -> builder.image(name));
    }

    /**
     * Creates and stores container configuration from internal builder.
     */
    protected void containerConfig() {
        config = builder.build();
    }

    /**
     * Creates and stores container from internal container config.
     */
    protected void createContainer() {
        if (config == null) {
            containerConfig();
        }
        container = new GenericContainer<>(DockerImageName.parse(config.image()));
        config.environment().forEach(container::withEnv);
        container.addExposedPorts(config.exposedPorts());
    }

    protected void containerInfo(ContainerInfo info) {
        this.info = info;
    }

    @Override
    public void start() {
        if (container == null) {
            createContainer();
        }
        LOGGER.log(System.Logger.Level.TRACE, () -> String.format("Starting the container %s", config.image()));
        container.start();
    }

    @Override
    public void stop() {
        LOGGER.log(System.Logger.Level.TRACE, () -> String.format("Stopping the container %s", config.image()));
        container.stop();
    }

    @Override
    public boolean supportsParameter(Type type) {
        return ContainerConfig.class.isAssignableFrom((Class<?>) type)
                || GenericContainer.class.isAssignableFrom((Class<?>) type)
                || ContainerInfo.class.isAssignableFrom((Class<?>) type);
    }

    @Override
    public Object resolveParameter(Type type) {
        if (ContainerConfig.class.isAssignableFrom((Class<?>) type)) {
            return config;
        } else if (GenericContainer.class.isAssignableFrom((Class<?>) type)) {
            return container;
        } else if (ContainerInfo.class.isAssignableFrom((Class<?>) type)) {
            return info;
        }
        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

}
