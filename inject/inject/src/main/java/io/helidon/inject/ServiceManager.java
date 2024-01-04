package io.helidon.inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.ContextualLookup;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceProvider;

/**
 * Manager of a single service descriptor.
 * <p>
 * The manager takes care of all operations that must be handled in the singleton scope of the registry,
 * it also handles instance management in the correct scope.
 * <p>
 * Call to {@link #serviceProvider()} will return a provider instance in the scope it should be in, or throw
 * an exception if that scope is not available right now.
 *
 * @param <T> type of the provided service
 */
class ServiceManager<T> {
    private final ServicesImpl registry;
    private final ServiceDescriptor<T> descriptor;
    private final TypeName scope;
    private final Supplier<Activator<T>> activator;
    private volatile Map<Ip, Supplier<?>> injectionPlan = null;

    public ServiceManager(ServicesImpl serviceRegistry,
                          ServiceDescriptor<T> descriptor,
                          Supplier<Activator<T>> activatorSupplier) {
        this.registry = serviceRegistry;
        this.descriptor = descriptor;
        this.scope = descriptor.scope();
        this.activator = activatorSupplier;
    }

    @Override
    public String toString() {
        return descriptor.serviceType().fqName();
    }

    public Map<Ip, Supplier<?>> injectionPlan() {
        Map<Ip, Supplier<?>> usedIp = injectionPlan;
        if (usedIp == null) {
            // no application, we have to create injection plan from current services
            usedIp = createInjectionPlan();
            this.injectionPlan = usedIp;
        }
        return usedIp;
    }

    ServiceInjectionPlanBinder.Binder servicePlanBinder() {
        return ServicePlanBinder.create(registry, descriptor, it -> this.injectionPlan = it);
    }

    /*
      Get service provider for the scope it is in (always works for singleton, may fail for other)
    */
    RegistryServiceProvider<T> serviceProvider() {
        return registry.scopeHandler(scope)
                .currentScope()
                .orElseThrow(() -> new InjectionException("Requested instance that is expected in " + scope.fqName()
                                                                  + ", yet this scope is currently not active."))
                .services()
                .serviceProvider(this);
    }

    ServiceDescriptor<T> descriptor() {
        return descriptor;
    }

    Activator<T> activator() {
        // this must return a new instance for each call
        // for example each request scope has its own activation lifecycle
        return activator.get();
    }

    double weight() {
        return descriptor.weight();
    }

    TypeName serviceType() {
        return descriptor.serviceType();
    }

    private Map<Ip, Supplier<?>> createInjectionPlan() {
        List<Ip> dependencies = descriptor.dependencies();

        if (dependencies.isEmpty()) {
            return Map.of();
        }

        AtomicReference<Map<Ip, Supplier<?>>> injectionPlan = new AtomicReference<>();

        ServiceInjectionPlanBinder.Binder binder = ServicePlanBinder.create(registry,
                                                                            descriptor,
                                                                            injectionPlan::set);
        for (Ip injectionPoint : dependencies) {
            planForIp(binder, injectionPoint);
        }

        return injectionPlan.get();
    }

    private void planForIp(ServiceInjectionPlanBinder.Binder injectionPlan, Ip injectionPoint) {
        ContextualLookup lookup = ContextualLookup.create(injectionPoint);

        List<ServiceManager<Object>> discovered = registry.lookupManagers(lookup)
                .stream()
                .filter(it -> it != this)
                .toList();

        TypeName ipType = injectionPoint.typeName();

        // now there are a few options - optional, list, and single instance
        if (discovered.isEmpty()) {
            if (ipType.isOptional()) {
                injectionPlan.put(injectionPoint, Optional::empty);
                return;
            }
            if (ipType.isList()) {
                injectionPlan.put(injectionPoint, List::of);
                return;
            }
            throw new InjectionServiceProviderException("Expected to resolve a service matching injection point "
                                                                + injectionPoint);
        }

        if (ipType.isList()) {
            // inject List<Contract>
        } else if (ipType.isOptional()) {
            // inject Optional<Contract>
        } else if (ipType.isSupplier()) {
            // one of the supplier options
        } else {
            // inject Contract
        }



        // we have a response
        if (ipType.isList()) {
            // is a list needed?
            TypeName typeOfElements = ipType.typeArguments().getFirst();
            if (typeOfElements.equals(SUPPLIER_TYPE) || typeOfElements.equals(ServiceProvider.TYPE)) {
                injectionPlan.put(injectionPoint, new ServiceProviderBase.IpValue<>(injectionPoint, discovered));
                return;
            }

            if (discovered.size() == 1) {
                injectionPlan.put(injectionPoint, () -> {
                    Object resolved = discovered.getFirst().get();
                    if (resolved instanceof List<?>) {
                        return resolved;
                    }
                    return List.of(resolved);
                });
                return;
            }

            injectionPlan.put(injectionPoint, () -> discovered.stream()
                    .map(RegistryServiceProvider::get)
                    .toList());
            return;
        }
        if (ipType.isOptional()) {
            // is an Optional needed?
            TypeName typeOfElement = ipType.typeArguments().getFirst();
            if (typeOfElement.equals(SUPPLIER_TYPE) || typeOfElement.equals(ServiceProvider.TYPE)) {
                injectionPlan.put(injectionPoint, () -> Optional.of(discovered.getFirst()));
                return;
            }

            injectionPlan.put(injectionPoint, () -> {
                Optional<?> firstResult = discovered.getFirst().first(ContextualLookup.EMPTY);
                if (firstResult.isEmpty()) {
                    return Optional.empty();
                }
                Object resolved = firstResult.get();
                if (resolved instanceof Optional<?>) {
                    return resolved;
                }
                return Optional.ofNullable(resolved);
            });
            return;
        }

        if (ipType.equals(SUPPLIER_TYPE)
                || ipType.equals(ServiceProvider.TYPE)
                || ipType.equals(InjectionPointProvider.TYPE)) {
            // is a provider needed?
            injectionPlan.put(injectionPoint, discovered::getFirst);
            return;
        }
        // and finally just get the value of the first service
        injectionPlan.put(injectionPoint, discovered.getFirst()::get);
    }
}
