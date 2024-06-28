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

import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;

/**
 * Service for starting a request scope.
 * Do not forget to call {@link io.helidon.service.inject.api.Scope#close()} when the scope should finish.
 */
@Service.Contract
public interface RequestScopeControl {
    /**
     * Start the request scope.
     *
     * @param id              id of the scope, should be something that allows us to map it to an external source
     * @param initialBindings initial bindings for services (already known by the registry)
     * @return a new request scope, that needs to be closed when the request is done
     */
    Scope startRequestScope(String id, Map<ServiceInfo, Object> initialBindings);
}
