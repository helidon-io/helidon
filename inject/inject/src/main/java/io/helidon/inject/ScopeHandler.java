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

package io.helidon.inject;

import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;

/**
 * Extension point for the service registry.
 * To support additional scope, a service implementing this interface must be available in the registry.
 * It should be accompanied by a way to start and stop the scope (such as {@link io.helidon.inject.RequestonControl} for
 * request scope).
 */
@Injection.Contract
public interface ScopeHandler {
    /**
     * Type name of this interface.
     * Service registry uses {@link io.helidon.common.types.TypeName} in its APIs.
     */
    TypeName TYPE_NAME = TypeName.create(ScopeHandler.class);

    /**
     * Scope this handle is capable of handling.
     *
     * @return scope type
     */
    TypeName supportedScope();

    /**
     * Get the current scope if available.
     *
     * @return current scope instance, or empty if the scope is not active
     */
    Optional<Scope> currentScope();
}
