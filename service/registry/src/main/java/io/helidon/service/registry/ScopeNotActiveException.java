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

package io.helidon.service.registry;

import java.util.Objects;

import io.helidon.common.types.TypeName;

/**
 * An attempt was done to get a service instance from a scope that is not active.
 */
public class ScopeNotActiveException extends ServiceRegistryException {
    /**
     * Scope that was expected to be active.
     */
    private final TypeName scope;

    /**
     * Create a new exception with a description and scope this exception is created for.
     *
     * @param msg   descriptive message
     * @param scope scope that failed to be found
     */
    public ScopeNotActiveException(String msg, TypeName scope) {
        super(msg);

        Objects.requireNonNull(scope);
        this.scope = scope;
    }

    /**
     * Scope that was not active.
     *
     * @return scope
     */
    public TypeName scope() {
        return scope;
    }
}
