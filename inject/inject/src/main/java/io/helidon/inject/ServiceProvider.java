package io.helidon.inject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;

/**
 * Manager of a single service descriptor.
 * <p>
 * The manager takes care of all operations that must be handled in the singleton scope of the registry,
 * it also handles instance management in the correct scope.
 *
 * @param <T> type of the provided service
 */
class ServiceProvider<T> {
    private final Services registry;
    private final ServiceDescriptor<T> descriptor;
    private final TypeName scope;
    private final ActivationRequest activationRequest;
    private final InterceptionMetadata interceptionMetadata;
    private final Contracts.ContractLookup contracts;
    private volatile Map<Ip, IpPlan<?>> injectionPlan = null;

    ServiceProvider(Services serviceRegistry,
                    ServiceDescriptor<T> descriptor) {
        this.registry = serviceRegistry;
        this.descriptor = descriptor;
        this.scope = descriptor.scope();

        this.contracts = Contracts.create(descriptor);
        this.activationRequest = ActivationRequest.builder()
                .targetPhase(serviceRegistry.limitRuntimePhase())
                .build();
        this.interceptionMetadata = new InterceptionMetadataImpl(registry);
    }

    @Override
    public String toString() {
        return descriptor.serviceType().fqName();
    }

    public Map<Ip, IpPlan<?>> injectionPlan() {
        Map<Ip, IpPlan<?>> usedIp = injectionPlan;
        if (usedIp == null) {
            // no application, we have to create injection plan from current services
            usedIp = createInjectionPlan();
            this.injectionPlan = usedIp;
        }
        return usedIp;
    }

    Set<TypeName> contracts(Lookup lookup) {
        return contracts.contracts(lookup);
    }

    Services registry() {
        return registry;
    }

    InterceptionMetadata interceptMetadata() {
        return interceptionMetadata;
    }

    ActivationRequest activationRequest() {
        return activationRequest;
    }

    ServiceInjectionPlanBinder.Binder servicePlanBinder() {
        return ServicePlanBinder.create(registry, descriptor, it -> this.injectionPlan = it);
    }

    ServiceDescriptor<T> descriptor() {
        return descriptor;
    }

    private Map<Ip, IpPlan<?>> createInjectionPlan() {
        List<Ip> dependencies = descriptor.dependencies();

        if (dependencies.isEmpty()) {
            return Map.of();
        }

        AtomicReference<Map<Ip, IpPlan<?>>> injectionPlan = new AtomicReference<>();

        ServiceInjectionPlanBinder.Binder binder = ServicePlanBinder.create(registry,
                                                                            descriptor,
                                                                            injectionPlan::set);
        for (Ip injectionPoint : dependencies) {
            planForIp(binder, injectionPoint);
        }

        binder.commit();

        return injectionPlan.get();
    }

    private void planForIp(ServiceInjectionPlanBinder.Binder injectionPlan, Ip injectionPoint) {
        /*
         very similar code is used in ApplicationCreator.injectionPlan
         make sure this is kept in sync!
         */
        Lookup lookup = Lookup.create(injectionPoint);

        if (descriptor.contracts().containsAll(lookup.contracts())
                && descriptor.qualifiers().equals(lookup.qualifiers())) {
            // injection point lookup must have a single contract for each injection point
            // if this service implements the contracts actually required, we must look for services with lower weight
            // but only if we also have the same qualifiers
            lookup = Lookup.builder(lookup)
                    .weight(descriptor.weight())
                    .build();
        }

        List<ServiceInfo> discovered = registry.lookupServices(lookup)
                .stream()
                .filter(it -> it != descriptor)
                .toList();

        /*
        Very similar code is used for build time code generation in ApplicationCreator.buildTimeBinding
        make sure this is kept in sync!
         */

        TypeName ipType = injectionPoint.typeName();

        // now there are a few options - optional, list, and single instance
        if (ipType.isList()) {
            ServiceInfo[] descriptors = discovered.toArray(new ServiceInfo[0]);
            TypeName typeOfList = ipType.typeArguments().getFirst();
            if (typeOfList.isSupplier()) {
                // inject List<Supplier<Contract>>
                injectionPlan.bindListOfSuppliers(injectionPoint, descriptors);
            } else {
                // inject List<Contract>
                injectionPlan.bindList(injectionPoint, descriptors);
            }
        } else if (ipType.isOptional()) {
            // inject Optional<Contract>
            if (discovered.isEmpty()) {
                injectionPlan.bindOptional(injectionPoint);
            } else {
                TypeName typeOfOptional = ipType.typeArguments().getFirst();
                if (typeOfOptional.isSupplier()) {
                    injectionPlan.bindOptionalOfSupplier(injectionPoint, discovered.getFirst());
                } else {
                    injectionPlan.bindOptional(injectionPoint, discovered.getFirst());
                }
            }
        } else if (ipType.isSupplier()) {
            // one of the supplier options
            TypeName typeOfSupplier = ipType.typeArguments().getFirst();
            if (typeOfSupplier.isOptional()) {
                // inject Supplier<Optional<Contract>>
                injectionPlan.bindSupplierOfOptional(injectionPoint, discovered.toArray(new ServiceInfo[0]));
            } else if (typeOfSupplier.isList()) {
                // inject Supplier<List<Contract>>
                injectionPlan.bindSupplierOfList(injectionPoint, discovered.toArray(new ServiceInfo[0]));
            } else {
                // inject Supplier<Contract>
                if (discovered.isEmpty()) {
                    // null binding is not supported at runtime
                    throw new InjectionServiceProviderException("Expected to resolve a service matching injection point "
                                                                        + injectionPoint);
                }
                injectionPlan.bindSupplier(injectionPoint, discovered.getFirst());
            }
        } else {
            // inject Contract
            if (discovered.isEmpty()) {
                // null binding is not supported at runtime
                throw new InjectionServiceProviderException("Expected to resolve a service matching injection point "
                                                                    + injectionPoint);
            }
            injectionPlan.bind(injectionPoint, discovered.getFirst());
        }
    }
}
