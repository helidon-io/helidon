package io.helidon.inject;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.types.TypeName;
import io.helidon.inject.service.ServiceBinder;
import io.helidon.inject.service.ServiceDescriptor;

class ScopeServicesImpl implements ScopeServices {
    private final Map<TypeName, LazyValue<ServiceProvider<?>>> servicesByTypeName;
    private final Map<TypeName, Set<LazyValue<ServiceProvider<?>>>> servicesByContract;

    private final Map<ServiceProvider<?>, Activator<?>> providersToActivators = new IdentityHashMap<>();
    private final ServicesImpl rootServices;
    private final TypeName scopeType;
    private final Map<ServiceDescriptor<?>, Supplier<Activator<?>>> activators;
    private final System.Logger logger;
    private boolean initialized;

    ScopeServicesImpl(ServicesImpl rootServices,
                      TypeName scopeType, Map<ServiceDescriptor<?>,
            Supplier<Activator<?>>> activators,
                      Map<ServiceDescriptor<Object>, Object> initialBindings) {
        this.rootServices = rootServices;
        this.scopeType = scopeType;
        this.activators = activators;

        this.logger = System.getLogger(ScopeServices.class.getName() + "." + scopeType.classNameWithEnclosingNames());

        activators.forEach((descriptor, activator) -> {
            LazyValue
        });

        initialized = false;
        initialBindings.forEach((descriptor, value) -> bind(new InitialScopeBindingProvider<>(descriptor, value)));
        initialized = true;
    }

    @Override
    public void bind(Activator<?> activator) {
        if (initialized) {
            if (!rootServices.injectionServices().config().permitsDynamic()) {
                throw new IllegalStateException(
                        "Attempting to bind to " + scopeType.className() + " Services that do not support dynamic updates."
                                + " Set option permitsDynamic, or configuration option 'inject.permits-dynamic=true' to enable");
            }
        }
        // make sure the activator has a chance to do something, such as create the initial service provider instance
        activator.activate(ActivationRequest.builder()
                                   .targetPhase(Phase.INIT)
                                   .throwIfError(false)
                                   .build());

        ServiceProvider<?> serviceProvider = activator.serviceProvider();
        this.providersToActivators.put(serviceProvider, activator);

        TypeName serviceType = serviceProvider.serviceType();

        // only put if absent, as this may be a lower weight provider for the same type
        LazyValue<?> providerLazyValue = LazyValue.create(serviceProvider);
        var previousValue = addByType(serviceType, providerLazyValue);
        if (previousValue != null) {
            // a value was already registered for this service type, ignore this registration
            if (logger.isLoggable(System.Logger.Level.TRACE)) {
                logger.log(System.Logger.Level.TRACE, "Attempt to register another service provider for the same service type."
                        + " Service type: " + serviceType.fqName()
                        + ", existing value: " + previousValue
                        + ", new value: " + serviceProvider);
            }
            return;
        }
        addContract(serviceType, providerLazyValue);

        for (TypeName contract : serviceProvider.contracts()) {
            addContract(contract, providerLazyValue);
        }
    }

    @Override
    public void close() {

    }

    @Override
    public <T> Optional<Supplier<T>> first(Lookup lookup) {
        return Optional.empty();
    }

    @Override
    public <T> List<Supplier<T>> all(Lookup lookup) {
        return null;
    }

    @Override
    public InjectionServices injectionServices() {
        return null;
    }

    @Override
    public ServiceBinder binder() {
        return null;
    }

    @Override
    public ServiceProviderRegistry serviceProviders() {
        return null;
    }

    private void addContract(TypeName serviceType, LazyValue providerLazyValue) {
        servicesByContract.computeIfAbsent(serviceType, it -> new TreeSet<>(ServiceProviderComparator.instance()))
                .add(providerLazyValue);
    }

    private LazyValue<?> addByType(TypeName serviceType, LazyValue lazyValue) {
        return servicesByTypeName.putIfAbsent(serviceType, lazyValue);
    }

    private class InitialScopeBindingProvider<T> extends ServiceProviderBase<T> {
        InitialScopeBindingProvider(ServiceDescriptor<T> descriptor, T serviceInstance) {
            super(rootServices, descriptor);
            state(Phase.ACTIVE, serviceInstance);
        }
    }
}
