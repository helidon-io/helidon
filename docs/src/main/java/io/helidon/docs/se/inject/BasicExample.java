package io.helidon.docs.se.inject;

import java.util.function.Function;

import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

class BasicExample {

    // tag::snippet_1[]
    @Service.Singleton
    class Greeter {

        public String greet(String name) {
            return "Hello %s!".formatted(name);
        }

    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Service.Singleton
    class GreetingInjectionService {

        private final Greeter greeter;

        @Service.Inject
        GreetingInjectionService(Greeter greeter) {
            this.greeter = greeter;
        }

        void printGreeting(String name) {
            System.out.println(greeter.greet(name));
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    public static void main(String[] args) {
        var registry = ServiceRegistryManager.create().registry();
        var greetings = registry.get(GreetingInjectionService.class);
        greetings.printGreeting("Tomas");
    }
    // end::snippet_3[]

}
