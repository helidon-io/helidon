/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
 * Service descriptor for module components.
 * <p>
 * As modules cannot be injected, so the service descriptor does not need to be public, and it does not have to be a
 * generated singleton.
 */
class ModuleServiceDescriptor implements ServiceDescriptor<ModuleComponent> {

    private static final TypeName MODULE_TYPE = TypeName.create(ModuleComponent.class);

    private final TypeName moduleType;
    private final String moduleName;
    private final Set<Qualifier> qualifiers;

    private ModuleServiceDescriptor(Class<?> moduleClass, String moduleName, Set<Qualifier> qualifiers) {
        this.moduleType = TypeName.create(moduleClass);
        this.moduleName = moduleName;
        this.qualifiers = qualifiers;
    }

    static ModuleServiceDescriptor create(ModuleComponent module, String moduleName) {
        return new ModuleServiceDescriptor(module.getClass(), moduleName, Set.of(Qualifier.createNamed(moduleName)));
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
    public TypeName scope() {
        return Injection.Singleton.TYPE_NAME;
    }

    @Override
    public String toString() {
        return "Service descriptor of module \"" + moduleName + "\"";
    }
}

