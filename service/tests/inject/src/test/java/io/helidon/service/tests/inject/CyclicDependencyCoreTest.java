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

package io.helidon.service.tests.inject;

import java.util.List;
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CyclicDependencyCoreTest {
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

    private static class Descriptor1 implements ServiceDescriptor<Service1> {
        private static final TypeName TYPE = TypeName.create(Descriptor1.class);

        private static final Dependency DEP = Dependency.builder()
                .contract(SERVICE_2)
                .descriptor(TYPE)
                .descriptorConstant("DEP")
                .name("second")
                .service(SERVICE_1)
                .typeName(SERVICE_2)
                .contractType(new GenericType<Service2>() { })
                .build();

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata metadata) {
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
        public List<Dependency> dependencies() {
            return List.of(DEP);
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(ResolvedType.create(SERVICE_1));
        }
    }

    private static class Descriptor2 implements ServiceDescriptor<Service2> {
        private static final TypeName TYPE = TypeName.create(Descriptor2.class);

        private static final Dependency DEP = Dependency.builder()
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
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
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
        public TypeName scope() {
            return Service.Singleton.TYPE;
        }

        @Override
        public List<Dependency> dependencies() {
            return List.of(DEP);
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(ResolvedType.create(SERVICE_2));
        }
    }
}
