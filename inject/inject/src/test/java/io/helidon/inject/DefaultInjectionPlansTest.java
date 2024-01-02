/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.io.Closeable;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.ServiceBinder;
import io.helidon.inject.service.ServiceDescriptor;

import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DefaultInjectionPlansTest {
    static final FakeInjectionPointDescriptor sp1 = new FakeInjectionPointDescriptor();
    static final FakeRegularDescriptor sp2 = new FakeRegularDescriptor();
    private static final TypeName IP_PROVIDER = TypeName.create(InjectionPointProvider.class);

    @BeforeEach
    void init() {
        InjectionServices.configure(InjectionConfig.builder()
                                            .permitsDynamic(true)
                                            .build());
    }

    @AfterEach
    void tearDown() {
        Resettor.reset();
    }

    /**
     * Also exercised in examples/inject.
     */
    @Test
    void testInjectionPointResolversFor() {
        InjectionServices injectionServices = InjectionServices.instance();
        ServicesImpl services = (ServicesImpl) injectionServices.services();

        services.bind(new FakeModuleComponent());

        Lookup criteria = Lookup.builder()
                .addContract(Closeable.class)
                .build();
        List<String> result = services.allProviders(criteria)
                .stream()
                .filter(it -> it.contracts().contains(IP_PROVIDER))
                .map(RegistryServiceProvider::description)
                .toList();

        assertThat(result, IsCollectionWithSize.hasSize(1));
        String description = result.getFirst();
        assertThat(description, is("DefaultInjectionPlansTest.FakeInjectionPointDescriptor:INIT"));
    }

    static class FakeModuleComponent implements ModuleComponent {
        @Override
        public String name() {
            return "fake.module";
        }

        @Override
        public void configure(ServiceBinder binder) {
            binder.bind(sp1);
            binder.bind(sp2);
        }
    }

    static class FakeInjectionPointDescriptor implements ServiceDescriptor<FakeInjectionPointDescriptor> {
        @Override
        public Set<TypeName> scopes() {
            return Set.of(Injection.Singleton.TYPE_NAME);
        }

        @Override
        public Set<TypeName> contracts() {
            return Set.of(TypeName.create(Closeable.class),
                          IP_PROVIDER,
                          TypeName.create(Supplier.class));
        }

        @Override
        public TypeName serviceType() {
            return TypeName.create(FakeInjectionPointDescriptor.class);
        }
    }

    static class FakeRegularDescriptor implements ServiceDescriptor<FakeRegularDescriptor> {
        @Override
        public Set<TypeName> scopes() {
            return Set.of(Injection.Singleton.TYPE_NAME);
        }

        @Override
        public Set<TypeName> contracts() {
            return Set.of(TypeName.create(Closeable.class),
                          TypeName.create(Supplier.class));
        }

        @Override
        public TypeName serviceType() {
            return TypeName.create(FakeRegularDescriptor.class);
        }
    }

    private static class Resettor extends ResettableHandler {
        protected static void reset() {
            ResettableHandler.reset();
        }
    }
}
