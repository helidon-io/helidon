/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.model.http;

import java.util.Optional;

import io.helidon.builder.api.Prototype;

/**
 * Rest endpoint for Typed HTTP client.
 */
@Prototype.Blueprint
interface ClientEndpointBlueprint extends RestEndpointBlueprint {
    /**
     * Configured URI.
     *
     * @return URI if configured
     */
    Optional<String> uri();

    /**
     * Configuration key to use at runtime.
     *
     * @return configuration key to override annotation based values
     */
    String configKey();

    /**
     * Name of a web client that should be injected and used by the generated typed client.
     *
     * @return rest client name
     */
    Optional<String> clientName();
}
