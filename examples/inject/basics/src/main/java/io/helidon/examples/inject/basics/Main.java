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

package io.helidon.examples.inject.basics;

import java.util.List;
import java.util.stream.Collectors;

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
        Services services = InjectionServices.create().services();

        // 0. Demonstrates programmatic lookup from the Services registry.
        // 1. when a service is being managed by a DI provider (like Helidon Injection) it should be "looked up" or injected
        // instead of new'ed
        Lookup lookup = Lookup.builder()
                .runLevel(Injection.RunLevel.STARTUP)
                .build();

        System.out.println("--------------------------------");
        System.out.println("- Initialize services          -");
        System.out.println("--------------------------------");
        // direct lookup (not using Supplier) will immediately initialize all services
        List<Object> startupServices = services.all(lookup);

        System.out.println("--------------------------------");
        System.out.println("- Programmatic lookup          -");
        System.out.println("--------------------------------");
        System.out.println("All services in RunLevel.STARTUP (ranked according to weight): ");
        System.out.println(startupServices.stream()
                                   .map(Object::getClass)
                                   .map(Class::getName)
                                   .collect(Collectors.joining("\n  ", "  ", "")));

        Object highestWeightedServiceProvider = services.get(lookup);
        System.out.println("Highest weighted service provider: " + highestWeightedServiceProvider);
    }

}
