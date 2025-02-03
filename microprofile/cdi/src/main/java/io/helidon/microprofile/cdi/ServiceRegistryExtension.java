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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.Weighted;
import io.helidon.common.Weights;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.metadata.reflection.AnnotationFactory;
import io.helidon.metadata.reflection.TypeFactory;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.Services;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessProducer;
import jakarta.enterprise.inject.spi.ProcessSyntheticBean;
import jakarta.inject.Named;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;
import org.jboss.weld.literal.NamedLiteral;

/**
 * {@link java.util.ServiceLoader} provider implementation of CDI extension to add service registry types as CDI beans.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ServiceRegistryExtension implements Extension {
    private static final TypeName CDI_NAMED_TYPE = TypeName.create(Named.class);
    private static final System.Logger LOGGER = System.getLogger(ServiceRegistryExtension.class.getName());
    // higher than default - CDI beans are "more important" as we run in CDI
    private static final double WEIGHT = Weighted.DEFAULT_WEIGHT + 10;

    private final Set<CdiServiceId> processedBeans = new HashSet<>();
    private final List<CdiBean> collectedCdiBeans = new ArrayList<>();
    private final List<CdiProducer> collectedCdiProducers = new ArrayList<>();
    // registry was not yet created, we can collect all the beans
    private boolean registryPending = true;

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

    /*
    We must be one of the last ones, as what we get here, we insert to registry
     */
    void processManagedBean(@Observes @Priority(Interceptor.Priority.PLATFORM_AFTER + 2000) ProcessManagedBean<?> pb) {
        this.doProcessBean(pb);
    }

    void processSyntheticBean(@Observes @Priority(Interceptor.Priority.PLATFORM_AFTER + 2000) ProcessSyntheticBean<?> pb) {
        this.doProcessBean(pb);
    }

    void processProducer(@Observes @Priority(Interceptor.Priority.PLATFORM_AFTER + 2000) ProcessProducer pb) {
        // make sure we also add produced stuff (now we only add beans themself)
        var member = pb.getAnnotatedMember();
        Set<Type> typeClosure = member.getTypeClosure();
        Set<Annotation> annotations = member.getAnnotations();

        Set<Qualifier> qualifiers = findRegistryQualifiers(annotations);
        TypeName beanType = TypeName.create(member.getDeclaringType().getJavaClass());

        for (Type type : typeClosure) {
            this.collectedCdiProducers.add(new CdiProducer(beanType, type, annotations, qualifiers));
        }
    }

    /*
    This must be done as early as possible,
     */
    void crossRegisterBeans(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) AfterBeanDiscovery abd, BeanManager bm) {
        registryPending = false;

        var registry = GlobalServiceRegistry.registry();
        List<ServiceInfo> allServices = registry.lookupServices(Lookup.EMPTY);
        Set<UniqueBean> processedTypes = new HashSet<>();

        // now we can add CDI beans to service registry (must be done after we add service registry to CDI, as
        // otherwise we would have the CDI beans twice)
        for (CdiBean cdiBean : collectedCdiBeans) {
            addCdiBean(bm, cdiBean);
        }

        // and all CDI producers
        for (CdiProducer cdiProducer : collectedCdiProducers) {
            addCdiProducer(bm, cdiProducer);
        }

        for (ServiceInfo service : allServices) {
            if (service instanceof CdiBeanDescriptor || service instanceof CdiProducerDescriptor) {
                // we do not want to re-insert CDI beans into CDI, obviously
                continue;
            }
            addServiceInfo(abd, bm, registry, processedTypes, service);
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

    private static io.helidon.common.types.Annotation mapNamed(io.helidon.common.types.Annotation annotation) {
        if (annotation.typeName().equals(CDI_NAMED_TYPE)) {
            return io.helidon.common.types.Annotation.builder()
                    .typeName(Service.Named.TYPE)
                    .update(it -> annotation.value().ifPresent(it::value))
                    .build();
        }
        return annotation;
    }

    private static Set<Qualifier> findRegistryQualifiers(Set<Annotation> annotations) {

        return Stream.concat(
                annotations.stream()
                        .filter(it -> it.annotationType().isAnnotationPresent(Service.Qualifier.class))
                        .map(AnnotationFactory::create)
                        .map(Qualifier::create),
                annotations.stream()
                        .filter(it -> it.annotationType().equals(Named.class))
                        .map(AnnotationFactory::create)
                        .map(ServiceRegistryExtension::mapNamed)
                        .map(Qualifier::create)
        ).collect(Collectors.toUnmodifiableSet());
    }

    private void addServiceInfo(AfterBeanDiscovery abd,
                                BeanManager bm,
                                ServiceRegistry registry,
                                Set<UniqueBean> processedTypes,
                                ServiceInfo service) {
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
                    .scope(toCdiScope(service))
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
                    Lookup lookup = Lookup.builder()
                            .addContract(usedContract)
                            .serviceType(service.serviceType())
                            .build();
                    List<ServiceInstance<Object>> instances = registry.lookupInstances(lookup);
                    for (ServiceInstance<Object> instance : instances) {
                        var bean = abd.addBean()
                                .beanClass(beanClass)
                                .addType(contractType)
                                .id("helidon-registry-named-" + service.serviceType().fqName())
                                .scope(toCdiScope(service))
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
                            .scope(toCdiScope(service))
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

    private void addCdiProducer(BeanManager bm, CdiProducer cdiProducer) {
        var type = cdiProducer.contract();
        if (invalidContract(ResolvedType.create(type))) {
            // this is not a contract we can support
            return;
        }

        Class<? extends Annotation> scope = findCdiScope(bm, cdiProducer.annotations()).orElse(Dependent.class);
        Optional<TypeName> registryScope = toRegistryScope(scope);
        if (registryScope.isEmpty()) {
            return;
        }
        TypeName actualScope = registryScope.get();
        Annotation[] cdiQualifiers = findCdiQualifiers(bm, cdiProducer.annotations());

        try {
            Services.add(new CdiProducerDescriptor(bm, actualScope, cdiProducer, cdiQualifiers));
        } catch (ServiceRegistryException e) {
            // the service is not used by service registry, so it cannot be added, or already used
            // make sure we can debug problems
            LOGGER.log(System.Logger.Level.TRACE, "Failed to register CDI producer for contract: "
                               + cdiProducer.contract().getTypeName() + "," + " producer: " + cdiProducer.beanType().fqName(),
                       e);
        }
    }

    private void addCdiBean(BeanManager bm, CdiBean cdiBean) {
        Bean<?> bean = cdiBean.bean();

        Class<? extends Annotation> scope = bean.getScope();
        Optional<TypeName> registryScope = toRegistryScope(scope);
        if (registryScope.isEmpty()) {
            return;
        }
        TypeName actualScope = registryScope.get();
        for (Type type : bean.getTypes()) {
            if (invalidContract(ResolvedType.create(type))) {
                // this is not a contract we can support
                continue;
            }
            try {
                Services.add(new CdiBeanDescriptor(bm, cdiBean, type, actualScope));
            } catch (ServiceRegistryException e) {
                // the service was already used - we do not throw, as this may be expected, still log, to make
                // sure we can debug problems
                LOGGER.log(System.Logger.Level.TRACE, "Failed to register CDI bean for contract: " + type + ","
                        + " bean: " + bean, e);
            }
        }
    }

    // find all qualifiers present on the element
    private Annotation[] findCdiQualifiers(BeanManager bm, Set<Annotation> annotations) {
        return annotations.stream()
                .filter(it -> bm.isQualifier(it.annotationType()))
                .toArray(Annotation[]::new);
    }

    /*
    This can probably be done in some more clever way, though I am not sure what it would be
    The task: find the closest annotation with scope (either NormalScope or Scope depending on caller) annotation
    So if there is an annotation such as:
    @ApplicationScoped
    @interface MyScope {}
    we want to get ApplicationScoped.class, not MyScope.class
     */
    private Optional<Class<? extends Annotation>> findCdiScope(BeanManager bm, Set<Annotation> annotations) {
        // find any annotation with @Scope or @NormalScope meta-annotation (through inherited meta-annotations)
        for (Annotation annotation : annotations) {
            if (bm.isNormalScope(annotation.annotationType())) {
                // we need the actual annotation annotated with normal scope
                var metaAnnotated = findMetaAnnotated(annotation, NormalScope.class, new HashSet<>());
                if (metaAnnotated.isPresent()) {
                    return metaAnnotated.map(Annotation::annotationType);
                }
            }
            if (bm.isScope(annotation.annotationType())) {
                // we need the actual annotation annotated with scope
                var metaAnnotated = findMetaAnnotated(annotation, Scope.class, new HashSet<>());
                if (metaAnnotated.isPresent()) {
                    return metaAnnotated.map(Annotation::annotationType);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Annotation> findMetaAnnotated(Annotation annotation,
                                                   Class<? extends Annotation> annotationType,
                                                   Set<Class<? extends Annotation>> processed) {
        Class<? extends Annotation> aClass = annotation.annotationType();
        if (aClass.getDeclaredAnnotation(annotationType) != null) {
            return Optional.of(annotation);
        }
        if (!processed.add(aClass)) {
            return Optional.empty();
        }
        Annotation[] declaredAnnotations = aClass.getDeclaredAnnotations();
        for (Annotation declaredAnnotation : declaredAnnotations) {
            var found = findMetaAnnotated(declaredAnnotation, annotationType, processed);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private void doProcessBean(ProcessBean<?> pb) {
        if (registryPending) {
            Bean<?> bean = pb.getBean();

            // we need to be sure we register each bean only once
            CdiServiceId id = new CdiServiceId(bean.getBeanClass(), bean.hashCode());
            if (processedBeans.add(id)) {
                collectedCdiBeans.add(new CdiBean(id, bean, pb.getAnnotated()));
            }
        }
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
        return hasWildcard(contract.type()) || hasGeneric(contract.type());
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

    private boolean hasGeneric(TypeName type) {
        if (type.generic()) {
            return true;
        }

        if (!type.typeArguments().isEmpty()) {
            for (TypeName typeArgument : type.typeArguments()) {
                if (hasGeneric(typeArgument)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Optional<TypeName> toRegistryScope(Class<? extends Annotation> scope) {
        if (scope == ApplicationScoped.class) {
            return Optional.of(Service.Singleton.TYPE);
        }
        if (scope == Singleton.class) {
            return Optional.of(Service.Singleton.TYPE);
        }
        if (scope == RequestScoped.class) {
            return Optional.of(Service.PerRequest.TYPE);
        }
        if (scope == Dependent.class) {
            return Optional.of(Service.PerLookup.TYPE);
        }
        // we only support the known scopes, cannot map other
        return Optional.empty();
    }

    private Class<? extends Annotation> toCdiScope(ServiceInfo service) {
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

    private record CdiServiceId(Class<?> beanClass, int hash) { }

    private record UniqueBean(ResolvedType contract, Set<Qualifier> qualifiers) {
    }

    private record CdiBean(CdiServiceId id,
                           Bean<?> bean,
                           Annotated annotated) {
    }

    private record CdiProducer(TypeName beanType,
                               Type contract,
                               Set<Annotation> annotations,
                               Set<Qualifier> qualifiers) {
    }

    private static class CdiProducerDescriptor implements ServiceDescriptor<Object> {
        private static final TypeName DESCRIPTOR_TYPE = TypeName.create(CdiProducerDescriptor.class);

        private final TypeName serviceType;
        private final Set<ResolvedType> contracts;
        private final Set<Qualifier> qualifiers;
        private final BeanManager bm;
        private final Type theType;
        private final TypeName scope;
        private final Annotation[] cdiQualifiers;

        CdiProducerDescriptor(BeanManager bm,
                              TypeName scope,
                              CdiProducer producer,
                              Annotation[] cdiQualifiers) {
            this.bm = bm;
            this.scope = scope;
            this.theType = producer.contract();
            this.serviceType = producer.beanType();
            this.contracts = Set.of(ResolvedType.create(producer.contract()));
            this.qualifiers = producer.qualifiers();
            this.cdiQualifiers = cdiQualifiers;
        }

        @Override
        public TypeName serviceType() {
            return serviceType;
        }

        @Override
        public TypeName descriptorType() {
            return DESCRIPTOR_TYPE;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return contracts;
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return qualifiers;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            var beans = bm.getBeans(theType, cdiQualifiers);
            if (beans.isEmpty()) {
                throw new ServiceRegistryException("CDI did not provide any beans for producer. Bean: " + serviceType().fqName()
                                                           + "; type: " + theType);
            }
            Bean<?> bean = beans.iterator().next();

            return bm.getReference(bean, theType, bm.createCreationalContext(bean));
        }

        @Override
        public TypeName scope() {
            return scope;
        }

        @Override
        public double weight() {
            return WEIGHT;
        }

        @Override
        public String toString() {
            return "CDI producer descriptor for: Contract: " + theType.getTypeName()
                    + "; producer: " + serviceType.fqName();
        }
    }

    private static class CdiBeanDescriptor implements ServiceDescriptor<Object> {
        private static final TypeName DESCRIPTOR_TYPE = TypeName.create(CdiBeanDescriptor.class);
        private final TypeName serviceType;
        private final Set<ResolvedType> contracts;
        private final Set<Qualifier> qualifiers;
        private final Bean<?> bean;
        private final BeanManager bm;
        private final Type theType;
        private final double weight;
        private final TypeName scope;

        CdiBeanDescriptor(BeanManager bm,
                          CdiBean cdiBean,
                          Type contract,
                          TypeName scope) {
            this.bm = bm;
            this.bean = cdiBean.bean();
            this.scope = scope;
            this.theType = contract;
            this.serviceType = TypeName.create(bean.getBeanClass());
            this.weight = Weights.find(bean.getBeanClass(), WEIGHT);
            this.contracts = Set.of(ResolvedType.create(contract));
            this.qualifiers = findRegistryQualifiers(cdiBean.annotated().getAnnotations());
        }

        @Override
        public TypeName serviceType() {
            return serviceType;
        }

        @Override
        public TypeName descriptorType() {
            return DESCRIPTOR_TYPE;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return contracts;
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return qualifiers;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return bm.getReference(bean, theType, bm.createCreationalContext(bean));
        }

        @Override
        public TypeName scope() {
            return scope;
        }

        @Override
        public double weight() {
            return weight;
        }

        @Override
        public String toString() {
            return "CDI bean descriptor for: Contract: " + theType.getTypeName()
                    + "; bean: " + serviceType.fqName()
                    + " (" + weight + ")";
        }
    }
}
