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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * REST endpoint for server side endpoint definition.
 */
@Prototype.Blueprint
interface ServerEndpointBlueprint extends RestEndpointBlueprint {
    /**
     * Listener name (optional).
     *
     * @return listener this endpoint should be assigned to
     */
    Optional<String> listener();

    /**
     * Whether the listener must be defined on the server or not.
     * If required, server will fail to start if the listener is not configured.
     *
     * @return whether the listener is required, defaults to {@code false}
     */
    @Option.DefaultBoolean(false)
    boolean listenerRequired();
}
