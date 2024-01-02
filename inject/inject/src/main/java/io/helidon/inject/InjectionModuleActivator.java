/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Support for {@link io.helidon.inject.service.ModuleComponent} as a service provider.
 * This activator manages service provider that
 * always returns the same {@link io.helidon.inject.service.ModuleComponent} instance.
 * <p>
 * As modules cannot be injected, so the service descriptor does not need to be public.
 */
class InjectionModuleActivator extends ServiceProviderBase<ModuleComponent> {

    InjectionModuleActivator(Services services,
                             ServiceDescriptor<ModuleComponent> descriptor) {
        super(services, descriptor);
    }

    static InjectionModuleActivator create(Services services,
                                           ModuleComponent module,
                                           String moduleName) {

        Set<Qualifier> qualifiers = Set.of(Qualifier.createNamed(moduleName));
        ServiceDescriptor<ModuleComponent> descriptor = new ModuleServiceDescriptor(module.getClass(), qualifiers);
        InjectionModuleActivator activator = new InjectionModuleActivator(services,
                                                                          descriptor);

        activator.state(Phase.ACTIVE, module);

        return activator;
    }

    private static class ModuleServiceDescriptor implements ServiceDescriptor<ModuleComponent> {
        private static final TypeName MODULE_TYPE = TypeName.create(ModuleComponent.class);

        private final TypeName moduleType;
        private final Set<Qualifier> qualifiers;

        private ModuleServiceDescriptor(Class<?> moduleClass, Set<Qualifier> qualifiers) {
            this.moduleType = TypeName.create(moduleClass);
            this.qualifiers = qualifiers;
        }

        @Override
        public TypeName serviceType() {
            return moduleType;
        }

        @Override
        public Set<TypeName> contracts() {
            return Set.of(MODULE_TYPE);
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return qualifiers;
        }

        @Override
        public Set<TypeName> scopes() {
            return Set.of(Injection.Singleton.TYPE_NAME);
        }
    }
}
