package io.helidon.service.inject;

import java.util.Map;
import java.util.NoSuchElementException;

import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyContext;

class InjectionContext implements DependencyContext {
    private final Map<Dependency, IpPlan<?>> injectionPlan;

    public InjectionContext(Map<Dependency, IpPlan<?>> injectionPlan) {
        this.injectionPlan = injectionPlan;
    }

    static DependencyContext create(Map<Dependency, IpPlan<?>> injectionPlan) {
        return new InjectionContext(injectionPlan);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T dependency(Dependency dependency) {
        IpPlan<?> ipPlan = injectionPlan.get(dependency);
        if (ipPlan == null) {
            throw new NoSuchElementException("Cannot resolve injection id " + dependency + " for service "
                                                     + dependency.service().fqName()
                                                     + ", this dependency was not declared in "
                                                     + "the service descriptor");
        }
        return (T) ipPlan.get();
    }
}
