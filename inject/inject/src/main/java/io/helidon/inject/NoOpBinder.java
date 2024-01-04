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

import io.helidon.inject.service.Ip;
import io.helidon.inject.service.ServiceInfo;

/**
 * No op binder for a {@link io.helidon.inject.RegistryServiceProvider}.
 */
public class NoOpBinder implements ServiceInjectionPlanBinder.Binder {
    private final RegistryServiceProvider<?> serviceProvider;

    protected NoOpBinder(RegistryServiceProvider<?> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bind(Ip injectionPoint, ServiceInfo serviceInfo) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindSupplier(Ip injectionPoint, ServiceInfo serviceInfo) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptional(Ip injectionPoint, ServiceInfo... serviceInfos) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptionalSupplier(Ip injectionPoint, ServiceInfo... serviceInfos) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindList(Ip injectionPoint, ServiceInfo... serviceInfos) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindListSupplier(Ip injectionPoint, ServiceInfo... serviceInfos) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindNull(Ip injectionPoint) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder runtimeBind(Ip injectionPoint, ServiceInfo serviceInfo) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder runtimeBindProvider(Ip injectionPoint, ServiceInfo serviceInfo) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder runtimeBindOptional(Ip injectionPoint, ServiceInfo serviceInfo) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder runtimeBindProviderOptional(Ip injectionPoint, ServiceInfo serviceInfo) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder runtimeBindList(Ip injectionPoint, ServiceInfo... serviceInfos) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder runtimeBindProviderList(Ip injectionPoint, ServiceInfo... serviceInfos) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder runtimeBindNullable(Ip injectionPoint, ServiceInfo serviceInfo) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder runtimeBindProviderNullable(Ip injectionPoint, ServiceInfo serviceInfo) {
        return this;
    }

    @Override
    public void commit() {
    }

    @Override
    public String toString() {
        return "No-op binder for " + serviceProvider.description();
    }
}
