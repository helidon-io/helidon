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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.ServiceDescriptor;

/*
Collects information about services that are created within a single scope
 */
class ScopeServicesFactory {
    private final Services services;
    private final TypeName scope;

    private final List<ServiceManager<?>> eagerServices = new CopyOnWriteArrayList<>();

    ScopeServicesFactory(Services services, TypeName scope) {
        this.services = services;
        this.scope = scope;
    }

    void bindService(ServiceManager<?> serviceManager) {
        if (serviceManager.descriptor().isEager()) {
            eagerServices.add(serviceManager);
        }
    }

    ScopeServices createForScope(String id, Map<ServiceDescriptor<?>, Object> initialBindings) {
        return new ScopeServices(services,
                                 scope,
                                 id,
                                 eagerServices,
                                 initialBindings);
    }
}
