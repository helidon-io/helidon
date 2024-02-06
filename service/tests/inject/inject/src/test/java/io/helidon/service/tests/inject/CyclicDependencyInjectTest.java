package io.helidon.service.tests.inject;

import java.util.List;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CyclicDependencyInjectTest {
    private static final TypeName SERVICE_1 = TypeName.create(Service1.class);
    private static final TypeName SERVICE_2 = TypeName.create(Service2.class);

    @Test
    public void testCyclicDependency() {
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                               .discoverServices(false)
                                                                               .addServiceDescriptor(new Descriptor1())
                                                                               .addServiceDescriptor(new Descriptor2())
                                                                               .build());

        try {
            ServiceRegistry registry = manager.registry();
            ServiceRegistryException sre = assertThrows(ServiceRegistryException.class, () -> registry.get(Service1.class));
            assertThat(sre.getMessage(), startsWith("Cyclic dependency"));
            sre = assertThrows(ServiceRegistryException.class, () -> registry.get(Service2.class));
            assertThat(sre.getMessage(), startsWith("Cyclic dependency"));
        } finally {
            manager.shutdown();
        }
    }

    private static class Service1 {
        Service1(Service2 second) {
        }
    }

    private static class Service2 {
        Service2(Service1 first) {
        }
    }

    private static class Descriptor1 implements GeneratedInjectService.Descriptor<Service1> {
        private static final TypeName TYPE = TypeName.create(Descriptor1.class);

        private static final Ip DEP = Ip.builder()
                .elementKind(ElementKind.CONSTRUCTOR)
                .contract(SERVICE_2)
                .descriptor(TYPE)
                .descriptorConstant("DEP")
                .name("second")
                .service(SERVICE_1)
                .typeName(SERVICE_2)
                .contractType(new GenericType<Service2>() { })
                .build();

        @Override
        public Object instantiate(DependencyContext ctx, GeneratedInjectService.InterceptionMetadata interceptionMetadata) {
            return new Service1(ctx.dependency(DEP));
        }

        @Override
        public TypeName serviceType() {
            return SERVICE_1;
        }

        @Override
        public TypeName descriptorType() {
            return TYPE;
        }

        @Override
        public List<Ip> injectionPoints() {
            return List.of(DEP);
        }

        @Override
        public Set<TypeName> contracts() {
            return Set.of(SERVICE_1);
        }

        @Override
        public TypeName scope() {
            return Injection.Singleton.TYPE_NAME;
        }
    }

    private static class Descriptor2 implements GeneratedInjectService.Descriptor<Service2> {
        private static final TypeName TYPE = TypeName.create(Descriptor2.class);

        private static final Ip DEP = Ip.builder()
                .elementKind(ElementKind.CONSTRUCTOR)
                .contract(SERVICE_1)
                .descriptor(TYPE)
                .descriptorConstant("DEP")
                .name("first")
                .service(SERVICE_2)
                .typeName(SERVICE_1)
                .contractType(new GenericType<Service1>() { })
                .build();

        @Override
        public Object instantiate(DependencyContext ctx, GeneratedInjectService.InterceptionMetadata interceptionMetadata) {
            return new Service2(ctx.dependency(DEP));
        }

        @Override
        public TypeName serviceType() {
            return SERVICE_1;
        }

        @Override
        public TypeName descriptorType() {
            return TYPE;
        }

        @Override
        public List<Ip> injectionPoints() {
            return List.of(DEP);
        }

        @Override
        public Set<TypeName> contracts() {
            return Set.of(SERVICE_2);
        }

        @Override
        public TypeName scope() {
            return Injection.Singleton.TYPE_NAME;
        }
    }
}
