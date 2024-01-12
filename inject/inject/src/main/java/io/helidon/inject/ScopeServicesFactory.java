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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;

/*
Collects information about services that are created within a single scope
 */
class ScopeServicesFactory {
    static final Comparator<ServiceInfo> EAGER_SERVICE_COMPARATOR = Comparator
            .comparingInt(ServiceInfo::runLevel)
            .thenComparing(Comparator.comparingDouble(ServiceInfo::weight)
            .reversed()
            .thenComparing((f, s) -> {
                if (f.qualifiers().isEmpty() && s.qualifiers().isEmpty()) {
                    return 0;
                }
                if (f.qualifiers().isEmpty()) {
                    return -1;
                }
                if (s.qualifiers().isEmpty()) {
                    return 1;
                }
                return 0;
            }))
            .thenComparing(ServiceInfo::serviceType);
    private final Services services;
    private final TypeName scope;

    private final List<ServiceManager<?>> eagerServices = new ArrayList<>();

    ScopeServicesFactory(Services services, TypeName scope) {
        this.services = services;
        this.scope = scope;
    }

    void bindService(ServiceManager<?> serviceManager) {
        if (serviceManager.descriptor().isEager()) {
            eagerServices.add(serviceManager);
        }
    }

    void postBindAllModules() {
        // everything is bound, we are now ready to serve
        // let's order eager services in the order of run level, and within run level as usual (weight, name)
        eagerServices.sort((m1, m2) -> EAGER_SERVICE_COMPARATOR.compare(m1.descriptor(), m2.descriptor()));
    }

    ScopeServices createForScope(String id, Map<ServiceDescriptor<?>, Object> initialBindings) {
        return new ScopeServices(services,
                                 scope,
                                 id,
                                 eagerServices,
                                 initialBindings);
    }
}
