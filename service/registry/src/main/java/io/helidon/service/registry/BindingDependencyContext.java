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

/**
 * Dependency context backed by a binding, to allow generated binding, late binding, and runtime computed binding.
 */
class BindingDependencyContext implements DependencyContext {
    private final Bindings.ServiceBindingPlan serviceBinding;

    BindingDependencyContext(Bindings.ServiceBindingPlan serviceBinding) {
        this.serviceBinding = serviceBinding;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T dependency(Dependency dependency) {
        Bindings.DependencyBinding binding = serviceBinding.binding(dependency);
        // services that match
        return (T) binding.instanceSupply().get();
    }
}
