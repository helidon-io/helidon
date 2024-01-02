/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.inject.basics;

import java.util.List;
import java.util.function.Supplier;

import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;

/**
 * Basics example.
 */
public class Main {

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        // get the global service registry
        Services services = InjectionServices.instance().services();

        // 0. Demonstrates programmatic lookup from the Services registry.
        // 1. when a service is being managed by a DI provider (like Helidon Injection) it should be "looked up" or injected instead of new'ed
        // 2. Notice we get a ServiceProvider - service providers allow for lazy initialization
        Lookup lookup = Lookup.builder()
                .runLevel(Injection.RunLevel.STARTUP)
                .build();

        List<Supplier<Object>> startupServiceProviders = services.all(lookup);
        System.out.println("Startup service providers (ranked according to weight, pre-activated): " + startupServiceProviders);

        Supplier<Object> highestWeightedServiceProvider = services.get(lookup);
        System.out.println("Highest weighted service provider: " + highestWeightedServiceProvider);

        // trigger lazy activations for the highest weighted service provider
        System.out.println("Highest weighted service provider (after activation): " + highestWeightedServiceProvider.get());

        // trigger all activations for the (remaining unactivated) startup service providers
        startupServiceProviders.forEach(Supplier::get);
        System.out.println("All service providers (after all activations): " + startupServiceProviders);
    }

}
