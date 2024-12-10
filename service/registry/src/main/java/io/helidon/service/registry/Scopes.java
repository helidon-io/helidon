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

import java.util.Map;

import io.helidon.common.types.TypeName;

/**
 * Service that provides support for creating {@link io.helidon.service.registry.Scope} instances.
 */
@Service.Contract
@Service.Describe
public interface Scopes {
    /**
     * Type name of this interface.
     */
    TypeName TYPE = TypeName.create(Scopes.class);

    /**
     * Create a registry managed scope.
     *
     * @param scope           scope annotation type
     * @param id              id of the scope
     * @param initialBindings initial bindings for the created scope
     * @return a new scope instance
     */
    Scope createScope(TypeName scope, String id, Map<ServiceDescriptor<?>, Object> initialBindings);
}
