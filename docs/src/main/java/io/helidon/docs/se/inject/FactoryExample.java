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
package io.helidon.docs.se.inject;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

class FactoryExample {

    record MyService() {}

    // tag::snippet_1[]
    /**
     * Supplier service factory.
     */
    @Service.Singleton
    class MyServiceProvider implements Supplier<MyService> {

        @Override
        public MyService get() {
            return new MyService();
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Service.Singleton
    class MyServiceFactory implements Service.ServicesFactory<MyService> {
        @Override
        public List<Service.QualifiedInstance<MyService>> services() {
            var named = Service.QualifiedInstance.create(new MyService(), Qualifier.createNamed("name"));
            var named2 = Service.QualifiedInstance.create(new MyService(), Qualifier.createNamed("name2"));
            return List.of(named, named2);
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Service.Qualifier
    public @interface Color {
        String value();
    }

    @Service.Singleton
    class QualifiedFactory implements Service.QualifiedFactory<MyService, Color> {

        @Override
        public Optional<Service.QualifiedInstance<MyService>> first(Qualifier qualifier,
                                                                    Lookup lookup,
                                                                    GenericType<MyService> genericType) {
            return Optional.of(Service.QualifiedInstance.create(new MyService(), qualifier));
        }
    }
    // end::snippet_3[]

    // tag::snippet_4[]
    @Service.Singleton
    class InjectionPointFactory implements Service.InjectionPointFactory<MyService> {

        @Override
        public Optional<Service.QualifiedInstance<MyService>> first(Lookup lookup) {
            return Optional.of(Service.QualifiedInstance.create(new MyService()));
        }
    }
    // end::snippet_4[]

    // tag::snippet_5[]
    @Service.Singleton
    @Service.Named("name")
    class InjectionPointFactoryWithQualifier implements Service.InjectionPointFactory<MyService> {

        @Override
        public Optional<Service.QualifiedInstance<MyService>> first(Lookup lookup) {
            return Optional.of(Service.QualifiedInstance.create(new MyService(), Qualifier.createNamed("name")));
        }
    }
    // end::snippet_5[]
}
