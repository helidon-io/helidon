package io.helidon.docs.se.inject;

import java.util.List;
import java.util.function.Supplier;

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
            return List.of(Service.QualifiedInstance.create(new MyService()));
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Service.Singleton
    @Service.Named("test")
    class MyQualifiedServiceFactory implements Service.ServicesFactory<MyService> {
        @Override
        public List<Service.QualifiedInstance<MyService>> services() {
            return List.of(Service.QualifiedInstance.create(new MyService(), Qualifier.createNamed("test")));
        }
    }
    // end::snippet_3[]
}
