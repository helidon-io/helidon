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

package io.helidon.service.inject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.ActivationRequest;
import io.helidon.service.inject.api.InjectServiceDescriptor;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.InterceptionMetadata;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.ServiceInstance;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistryException;

/**
 * Takes care of a single service descriptor.
 *
 * @param <T> type of the provided service
 */
class ServiceProvider<T> {
    private final InjectServiceRegistryImpl registry;
    private final InjectServiceInfo serviceInfo;
    private final InjectServiceDescriptor<T> descriptor;

    private final ActivationRequest activationRequest;
    private final InterceptionMetadata interceptionMetadata;
    private final Contracts.ContractLookup contracts;
    private volatile Map<Dependency, IpPlan<?>> injectionPlan = null;

    ServiceProvider(InjectServiceRegistryImpl serviceRegistry,
                    InjectServiceDescriptor<T> descriptor) {

        Objects.requireNonNull(serviceRegistry);
        Objects.requireNonNull(descriptor);

        this.registry = serviceRegistry;
        this.interceptionMetadata = registry.interceptionMetadata();
        this.activationRequest = registry.activationRequest();
        this.serviceInfo = descriptor;
        this.descriptor = descriptor;

        this.contracts = Contracts.create(descriptor);
    }

    @Override
    public String toString() {
        return "ServiceProvider for " + serviceInfo.serviceType().fqName();
    }

    InjectServiceInfo serviceInfo() {
        return serviceInfo;
    }

    InjectServiceDescriptor<T> descriptor() {
        return descriptor;
    }

    InjectionPlanBinder.Binder servicePlanBinder() {
        return ServicePlanBinder.create(registry, descriptor, it -> this.injectionPlan = it);
    }

    Map<Dependency, IpPlan<?>> injectionPlan() {
        Map<Dependency, IpPlan<?>> usedIp = injectionPlan;
        if (usedIp == null) {
            // no application, we have to create injection plan from current services
            usedIp = createInjectionPlan();
            this.injectionPlan = usedIp;
        }
        return usedIp;
    }

    InterceptionMetadata interceptionMetadata() {
        return interceptionMetadata;
    }

    Set<ResolvedType> contracts(Lookup lookup) {
        return contracts.contracts(lookup);
    }

    ActivationRequest activationRequest() {
        return activationRequest;
    }

    private Map<Dependency, IpPlan<?>> createInjectionPlan() {
        // for core services, we must use Dependency, for inject services, we must use Ip
        List<? extends Dependency> dependencies = descriptor.coreInfo().dependencies();

        if (dependencies.isEmpty()) {
            return Map.of();
        }

        AtomicReference<Map<Dependency, IpPlan<?>>> injectionPlan = new AtomicReference<>();

        InjectionPlanBinder.Binder binder = ServicePlanBinder.create(registry,
                                                                     descriptor,
                                                                     injectionPlan::set);
        for (Dependency injectionPoint : dependencies) {
            planForIp(binder, injectionPoint);
        }

        binder.commit();

        return injectionPlan.get();
    }

    private void planForIp(InjectionPlanBinder.Binder injectionPlan,
                           Dependency injectionPoint) {
        /*
         very similar code is used in ApplicationCreator.buildTimeBinding
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
                .map(InjectServiceInfo::coreInfo)
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
            } else if (typeOfList.equals(ServiceInstance.TYPE)) {
                injectionPlan.bindServiceInstanceList(injectionPoint, descriptors);
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
                } else if (typeOfOptional.equals(ServiceInstance.TYPE)) {
                    injectionPlan.bindOptionalOfServiceInstance(injectionPoint, discovered.getFirst());
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
                    throw new ServiceRegistryException(injectionPoint.service().fqName()
                                                               + ": expected to resolve a service matching injection point "
                                                               + injectionPoint);
                }
                injectionPlan.bindSupplier(injectionPoint, discovered.getFirst());
            }
        } else {
            // inject Contract
            if (discovered.isEmpty()) {
                // null binding is not supported at runtime
                throw new ServiceRegistryException(injectionPoint.service().fqName()
                                                   + ": expected to resolve a service matching injection point "
                                                           + injectionPoint);
            }
            if (ipType.equals(ServiceInstance.TYPE)) {
                injectionPlan.bindServiceInstance(injectionPoint, discovered.getFirst());
            } else {
                injectionPlan.bind(injectionPoint, discovered.getFirst());
            }
        }
    }
}
