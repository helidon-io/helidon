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

package io.helidon.service.inject.api;

import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;

/**
 * Inject service registry SPI, to be used for scope handlers and other extension services.
 */
@Service.Contract
@Injection.Describe
public interface InjectRegistrySpi extends InjectRegistry {
    /**
     * Type name of this interface.
     */
    TypeName TYPE = TypeName.create(InjectRegistrySpi.class);

    /**
     * Create a registry for a specific scope.
     *
     * @param scope           scope of the registry
     * @param id              id of the scope instance (i.e. each request scope should have a unique id)
     * @param initialBindings bindings to bind to enable injection within this scope, such as server request for HTTP
     *                        request scope
     * @return a new scoped registry that takes care of lifecycle of service instances with the scope
     */
    ScopedRegistry createForScope(TypeName scope, String id, Map<ServiceInfo, Object> initialBindings);
}
