package io.helidon.inject;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;

import static io.helidon.inject.InjectionServicesImpl.shutdownComparator;

/**
 * Services for a specific scope.
 * This type is owned by Helidon Injection, and cannot be customized.
 */
/*
Cardinality: one instance per scope instance
- 1 instance for Singleton scope
- 1 instance per request for Requeston scope
 */
class ScopeServices {
    private final ReadWriteLock serviceProvidersLock = new ReentrantReadWriteLock();
    private final Map<ServiceInfo, RegistryServiceProvider<?>> serviceProviders = new IdentityHashMap<>();
    private final Map<ServiceInfo, Activator<?>> activators = new IdentityHashMap<>();

    private final System.Logger logger;
    private final TypeName scope;
    private final String id;
    private boolean active = true;

    ScopeServices(Services services, TypeName scope, String id, Map<ServiceDescriptor<?>, Object> initialBindings) {
        this.logger = System.getLogger(ScopeServices.class.getName() + "." + scope.className());
        this.scope = scope;
        this.id = id;

        initialBindings.forEach((descriptor, value) -> {
            InitialScopeBindingProvider<Object> provider = new InitialScopeBindingProvider<>(services,
                                                                                             descriptor,
                                                                                             value);
            serviceProviders.put(descriptor, provider);
            activators.put(descriptor, provider);
        });
    }

    @SuppressWarnings("unchecked")
    <T> RegistryServiceProvider<T> serviceProvider(ServiceManager<T> serviceManager) {
        ServiceDescriptor<T> descriptor = serviceManager.descriptor();

        try {
            serviceProvidersLock.readLock().lock();
            checkActive();
            RegistryServiceProvider<?> serviceProvider = serviceProviders.get(descriptor);
            if (serviceProvider != null) {
                return (RegistryServiceProvider<T>) serviceProvider;
            }
        } finally {
            serviceProvidersLock.readLock().unlock();
        }

        // failed to get instance, now let's obtain a write lock and do it again
        try {
            serviceProvidersLock.writeLock().lock();
            checkActive();
            return (RegistryServiceProvider<T>) serviceProviders.computeIfAbsent(descriptor, desc -> {
                Activator<T> activator = serviceManager.activator();
                activator.activate(ActivationRequest.builder()
                                           .targetPhase(Phase.INIT)
                                           .throwIfError(false)
                                           .build());

                activators.put(descriptor, activator);
                RegistryServiceProvider<T> sp = activator.serviceProvider();
                sp.injectionPlan(serviceManager.injectionPlan());
                return sp;
            });
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }
    }

    void close() {
        try {
            serviceProvidersLock.writeLock().lock();
            if (!active) {
                return;
            }

            List<RegistryServiceProvider<?>> toShutdown = serviceProviders.values()
                    .stream()
                    .filter(it -> it.currentActivationPhase().eligibleForDeactivation())
                    .sorted(shutdownComparator())
                    .toList();

            for (RegistryServiceProvider<?> csp : toShutdown) {
                Activator<?> activator = activators.get(csp.serviceInfo());
                ActivationResult result = activator.deactivate(DeActivationRequest.builder()
                                                                       .throwIfError(false)
                                                                       .build());
                if (result.failure() && logger.isLoggable(System.Logger.Level.DEBUG)) {
                    if (result.error().isPresent()) {
                        logger.log(System.Logger.Level.DEBUG,
                                   "[" + id + "] Failed to deactivate " + csp.description(),
                                   result.error().get());
                    } else {
                        logger.log(System.Logger.Level.DEBUG,
                                   "[" + id + "] Failed to deactivate " + csp.description() + ", deactivation result: " + result);
                    }
                }
            }

            active = false;
        } finally {
            serviceProvidersLock.writeLock().unlock();
        }
    }

    private void checkActive() {
        if (!active) {
            throw new InjectionException("Injection scope " + scope.fqName() + "[" + id + "] is no longer active.");
        }
    }

    private static class InitialScopeBindingProvider<T> extends ServiceProviderBase<T> {
        @SuppressWarnings({"rawtypes", "unchecked"})
        InitialScopeBindingProvider(Services rootServices, ServiceDescriptor descriptor, T serviceInstance) {
            super(rootServices, descriptor);
            state(Phase.ACTIVE, serviceInstance);
        }
    }
}
