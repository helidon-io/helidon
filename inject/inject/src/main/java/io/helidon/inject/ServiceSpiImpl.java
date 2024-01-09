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
