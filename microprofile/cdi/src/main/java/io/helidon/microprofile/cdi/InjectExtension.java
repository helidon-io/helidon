package io.helidon.microprofile.cdi;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.metadata.reflection.AnnotationFactory;
import io.helidon.metadata.reflection.TypeFactory;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistry;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

class InjectExtension implements Extension {
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

    void registerInjectBeans(@Observes AfterBeanDiscovery abb) {

        var registry = GlobalServiceRegistry.registry();
        List<ServiceInfo> allServices = registry.lookupServices(Lookup.EMPTY);
        Set<UniqueBean> processedTypes = new HashSet<>();

        for (ServiceInfo service : allServices) {
            Set<ResolvedType> contracts = service.contracts();
            for (ResolvedType contract : contracts) {
                if (processedTypes.add(new UniqueBean(contract, service.qualifiers()))) {
                    // ignore java.lang.Object
                    // ignore wildcard types
                    if (contract.type().equals(TypeNames.OBJECT)) {
                        continue;
                    }
                    if (hasWildcard(contract.type())) {
                        continue;
                    }

                    // not yet registered with CDI
                    BeanConfigurator<Object> builder = abb.addBean();
                    for (Qualifier qualifier : service.qualifiers()) {
                        AnnotationFactory.synthesize(qualifier).ifPresent(builder::addQualifier);
                    }
                    builder.id("helidon-inject-" + contract.resolvedName())
                            .addType(TypeFactory.toType(contract.type()))
                            .scope(toScope(service))
                            .createWith(context -> registry.get(service));
                }
            }
        }
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

    void afterBeanDiscovery(@Observes AfterBeanDiscovery abd) {
        // add support for injecting the service registry itself into CDI beans
        abd.addBean()
                .id("helidon-service-registry")
                .scope(ApplicationScoped.class)
                .addType(ServiceRegistry.class)
                .addType(ServiceRegistry.class)
                .createWith(context -> GlobalServiceRegistry.registry());
    }

    @SuppressWarnings("unchecked")
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
        return (Class<? extends Annotation>) TypeFactory.toClass(scope);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @InterceptorBinding
    @interface InjectIntercepted {
        /**
         * Literal used to obtain an instance of the annotation.
         */
        class Literal extends AnnotationLiteral<InjectIntercepted> implements InjectIntercepted {
            /**
             * Annotation literal. As this annotation does not have any properties, the same literal can be reused.
             */
            static final InjectIntercepted INSTANCE = new Literal();
        }
    }

    private record UniqueBean(ResolvedType contract, Set<Qualifier> qualifiers) {
    }

    @InjectIntercepted
    @Priority(Interceptor.Priority.LIBRARY_BEFORE)
    @Interceptor
    static class InjectInterceptor {
        private final ServiceRegistry registry;

        @Inject
        InjectInterceptor(ServiceRegistry registry) {
            this.registry = registry;
        }

        Object invoke(InvocationContext cdiCtx) throws Exception {
            // todo finish
            return cdiCtx.proceed();
        }
    }
}
