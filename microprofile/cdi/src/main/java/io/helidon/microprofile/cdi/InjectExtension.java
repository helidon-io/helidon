/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.metadata.reflection.TypeFactory;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import org.jboss.weld.literal.NamedLiteral;

@SuppressWarnings({"unchecked", "rawtypes"})
public class InjectExtension implements Extension {
    @SuppressWarnings("unchecked")
    void registerQualifiers(@Observes BeforeBeanDiscovery bbd) {
        var registry = GlobalServiceRegistry.registry();
        List<ServiceInfo> allServices = registry.lookupServices(Lookup.EMPTY);

        Set<TypeName> addedQualifiers = new HashSet<>();

        for (ServiceInfo service : allServices) {
            for (Qualifier qualifier : service.qualifiers()) {
                TypeName typeName = qualifier.typeName();
                if (addedQualifiers.add(typeName)) {
                    bbd.addQualifier((Class<? extends Annotation>) TypeFactory.toClass(typeName));
                }
            }
        }
    }

    void registerInjectBeans(@Observes AfterBeanDiscovery abd, BeanManager bm) {

        var registry = GlobalServiceRegistry.registry();
        List<ServiceInfo> allServices = registry.lookupServices(Lookup.EMPTY);
        Set<UniqueBean> processedTypes = new HashSet<>();

        for (ServiceInfo service : allServices) {
            Set<ResolvedType> contracts = service.contracts();
            Set<ResolvedType> usedContracts = new HashSet<>();
            // first collect all contracts that we should advertise for this bean
            for (ResolvedType contract : contracts) {
                if (!processedTypes.add(new UniqueBean(contract, service.qualifiers()))) {
                    // this contract is already advertised by a higher weighted service; CDI only supports one bean
                    continue;
                }

                if (invalidContract(contract)) {
                    // this is not a contract we can support
                    continue;
                }

                usedContracts.add(contract);
            }

            // we also advertise the service implementation itself
            usedContracts.add(ResolvedType.create(service.serviceType()));
            /*
            bean class: service implementation [MyService]
            bean types: each contract has its own bean
            id: contract fq name with our prefix
            scope: "guessed" from registry scope
             */
            Class<?> beanClass = TypeFactory.toClass(service.serviceType());
            for (ResolvedType usedContract : usedContracts) {
                TypeName contractTypeName = usedContract.type();
                Type contractType = TypeFactory.toType(contractTypeName);
                abd.addBean()
                        .beanClass(beanClass)
                        .addType(contractType)
                        .id("helidon-registry-" + service.serviceType().fqName())
                        .scope(toScope(service))
                        .createWith(ctx -> {
                            Object instance = registry.get(contractTypeName);

                            return bm.createInterceptionFactory((CreationalContext) ctx, TypeFactory.toClass(contractTypeName))
                                    .createInterceptedInstance(instance);
                        });
                Optional<Qualifier> named = namedQualifier(service.qualifiers());
                if (named.isPresent()) {
                    // also add with a Jakarta named qualifier
                    String name = named.get().value().orElseThrow();
                    if (name.equals("*")) {
                        // this is most likely a ServicesFactory
                        List<ServiceInstance<Object>> instances = registry.lookupInstances(Lookup.create(usedContract.type()));
                        for (ServiceInstance<Object> instance : instances) {
                            var bean = abd.addBean()
                                    .beanClass(beanClass)
                                    .addType(contractType)
                                    .id("helidon-registry-named-" + service.serviceType().fqName())
                                    .scope(toScope(service))
                                    .createWith(ctx -> {
                                        return bm.createInterceptionFactory((CreationalContext) ctx,
                                                                            TypeFactory.toClass(contractTypeName))
                                                .createInterceptedInstance(instance.get());
                                    });
                            Optional<Qualifier> qualifier = namedQualifier(instance.qualifiers());
                            if (qualifier.isPresent()) {
                                // use the actual name
                                bean.addQualifier(new NamedLiteral(qualifier.get().value().orElseThrow()));
                            } else {
                                // use the default name (for unnamed in Helidon Service Registry), as CDI
                                // does not support injection of unnamed instances if more than one named/unnamed instances
                                // exists
                                bean.addQualifier(new NamedLiteral(Service.Named.DEFAULT_NAME));
                            }
                        }
                    } else {
                        // * means that we provide a factory that has names resolved at runtime
                        // we would have to get the actual named instances for the contract, which we do
                        // not have right now implemented
                        abd.addBean()
                                .beanClass(beanClass)
                                .addType(contractType)
                                .id("helidon-registry-named-" + service.serviceType().fqName())
                                .scope(toScope(service))
                                .addQualifier(new NamedLiteral(name))
                                .createWith(ctx -> {
                                    Object instance = registry.get(contractTypeName);

                                    return bm.createInterceptionFactory((CreationalContext) ctx,
                                                                        TypeFactory.toClass(contractTypeName))
                                            .createInterceptedInstance(instance);
                                });
                    }
                }
            }
        }
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery abd) {
        // add support for injecting the service registry itself into CDI beans
        abd.addBean()
                .id("helidon-service-registry")
                .scope(ApplicationScoped.class)
                .beanClass(ServiceRegistry.class)
                .addType(ServiceRegistry.class)
                .createWith(context -> GlobalServiceRegistry.registry());
    }

    private Optional<Qualifier> namedQualifier(Set<Qualifier> qualifiers) {
        for (Qualifier qualifier : qualifiers) {
            if (qualifier.typeName().equals(Service.Named.TYPE)) {
                return Optional.of(qualifier);
            }
        }
        return Optional.empty();
    }

    private boolean invalidContract(ResolvedType contract) {
        // ignore java.lang.Object
        // ignore wildcard types
        if (contract.type().equals(TypeNames.OBJECT)) {
            return true;
        }
        if (hasWildcard(contract.type())) {
            return true;
        }
        return !contract.type().typeArguments().isEmpty();
    }

    private boolean hasWildcard(TypeName type) {
        if (type.wildcard()) {
            return true;
        }

        if (!type.typeArguments().isEmpty()) {
            for (TypeName typeArgument : type.typeArguments()) {
                if (hasWildcard(typeArgument)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Class<? extends Annotation> toScope(ServiceInfo service) {
        TypeName scope = service.scope();
        if (scope.equals(Service.Singleton.TYPE)) {
            return ApplicationScoped.class;
        }
        if (scope.equals(Service.PerLookup.TYPE)) {
            return Dependent.class;
        }
        if (scope.equals(Service.PerRequest.TYPE)) {
            return RequestScoped.class;
        }
        // ignore scope, use dependent - this will always use service registry to create an instance, for each injection point
        return Dependent.class;
    }

    private record UniqueBean(ResolvedType contract, Set<Qualifier> qualifiers) {
    }
}
