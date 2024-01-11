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

import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Additional methods for extensibility of the registry, such as when adding a new scope.
 * <p>
 * This type can be injected, and is expected to be used by custom scope controls.
 */
@Injection.Contract
public interface ServicesSpi {
    /**
     * Type name of this interface.
     * {@link io.helidon.common.types.TypeName} is used in Helidon Inject APIs.
     */
    TypeName TYPE_NAME = TypeName.create(ServicesSpi.class);

    /**
     * Create new scope services - a service registry maintaining service instances within a single scope instance.
     * <p>
     * Don't forget to call {@link io.helidon.inject.ScopeServices#activate()} once the scope is discoverable,
     * so eager services can be initialized.
     *
     * @param scope scope to create services for
     * @param id id of the newly created scope
     * @param initialBindings initial bindings for services already known to the service registry
     * @return a new scope service instance
     */
    ScopeServices createForScope(TypeName scope, String id, Map<ServiceDescriptor<?>, Object> initialBindings);
}
