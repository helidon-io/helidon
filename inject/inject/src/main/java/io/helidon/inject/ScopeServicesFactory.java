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
