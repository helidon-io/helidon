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
import java.util.Objects;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceDescriptor;

@Injection.Singleton
class ServiceSpiImpl implements ServicesSpi {
    private final Services services;

    @Injection.Inject
    ServiceSpiImpl(Services services) {
        this.services = services;
    }

    @Override
    public ScopeServices createForScope(TypeName scope, String id, Map<ServiceDescriptor<?>, Object> initialBindings) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(id);
        Objects.requireNonNull(initialBindings);

        if (scope.equals(Injection.Singleton.TYPE_NAME) || scope.equals(Injection.Service.TYPE_NAME)) {
            throw new IllegalArgumentException("Scope services cannot be created for scope: " + scope.fqName()
                                                       + ", this scope is reserved to service registry implementation.");
        }

        return services.createForScope(scope, id, initialBindings);
    }
}
