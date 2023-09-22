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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

import org.testcontainers.containers.wait.strategy.WaitStrategy;

/**
 * Docker container configuration bean.
 */
@Prototype.Blueprint
@Configured
interface ContainerConfigBlueprint {

    /**
     * Name of the Docker image including label.
     *
     * @return name of the image
     */
    @ConfiguredOption
    String image();

    /**
     * Environment variables to be set in the container.
     *
     * @return {@link Map} of environment variables
     */
    @ConfiguredOption
    Map<String, String> environment();

    /**
     * Ports that container listens on.
     * @return {@link List} of TCP ports
     */
    @ConfiguredOption
    int[] exposedPorts();

    /**
     * Working directory that the container should use on startup.
     *
     * @return path to the working directory inside the container
     */
    @ConfiguredOption
    Optional<String> workingDirectory();

    /**
     * Command that should be run in the container.
     * @return command in single string format
     */
    @ConfiguredOption
    Optional<String> command();

    /**
     * Container startup timeout.
     * @return startup timeout
     */
    @ConfiguredOption("PT60S")
    Duration startUpTimeout();

    /**
     * Specify the {@link WaitStrategy} to use to determine if the container is ready.
     * @return {@link WaitStrategy} to use
     */
    @Option.DefaultCode("ContainerConfigConstants.defaultWaitStrategy()")
    WaitStrategy waitStrategy();

}
