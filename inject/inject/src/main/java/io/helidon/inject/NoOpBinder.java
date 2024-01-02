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
    public ServiceInjectionPlanBinder.Binder bindProvider(Ip injectionPoint, ServiceInfo serviceInfo) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindOptional(Ip injectionPoint, ServiceInfo... serviceInfos) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindProviderOptional(Ip injectionPoint, ServiceInfo... serviceInfos) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindList(Ip injectionPoint, ServiceInfo... serviceInfos) {
        return this;
    }

    @Override
    public ServiceInjectionPlanBinder.Binder bindProviderList(Ip injectionPoint, ServiceInfo... serviceInfos) {
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
