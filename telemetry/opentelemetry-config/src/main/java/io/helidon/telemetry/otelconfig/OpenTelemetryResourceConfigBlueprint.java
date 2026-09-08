/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.telemetry.otelconfig;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.api.common.AttributesBuilder;

/**
 * OpenTelemetry resource settings shared by all configured signals.
 * Optional values left unspecified do not add or override resource attributes.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(OpenTelemetryResourceConfigSupport.CustomMethods.class)
interface OpenTelemetryResourceConfigBlueprint {

    /**
     * Namespace for the service.
     *
     * @return service namespace
     */
    @Option.Configured
    Optional<String> serviceNamespace();

    /**
     * Identifier for this service instance.
     *
     * @return service instance ID
     */
    @Option.Configured
    Optional<String> serviceInstanceId();

    /**
     * Name of the deployment environment.
     *
     * @return deployment environment name
     */
    @Option.Configured
    Optional<String> deploymentEnvironmentName();

    /**
     * Identifier of the container in which the service instance is running.
     *
     * @return container ID
     */
    @Option.Configured
    Optional<String> containerId();

    /**
     * Name of the container image on which the container was built.
     *
     * @return container image name
     */
    @Option.Configured
    Optional<String> containerImageName();

    /**
     * Tags of the container image on which the container was built.
     *
     * @return container image tags
     */
    @Option.Configured
    @Option.Singular
    List<String> containerImageTags();

    /**
     * Repository digests of the container image as reported by the container runtime.
     *
     * @return container image repository digests
     */
    @Option.Configured
    @Option.Singular
    List<String> containerImageRepoDigests();

    /**
     * Additional resource attributes shared by all configured signals.
     *
     * @return additional resource attributes
     */
    @Option.Configured
    Optional<AttributesBuilder> attributes();
}
