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

import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

class BasicExample {

    // tag::snippet_1[]
    @Service.Singleton
    class Greeter {

        String greet(String name) {
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
