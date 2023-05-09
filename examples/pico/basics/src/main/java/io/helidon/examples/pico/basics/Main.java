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

package io.helidon.examples.pico.basics;

import java.util.List;

import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.RunLevel;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceInfoCriteriaDefault;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;

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
        Services services = PicoServices.realizedServices();

        // 0. Demonstrates programmatic lookup from Pico's Services registry.
        // 1. when a service is being managed by a DI provider (like Pico) it should be "looked up" or injected instead of new'ed
        // 2. Notice we get a ServiceProvider - service providers allow for lazy initialization
        ServiceInfoCriteria criteria = ServiceInfoCriteriaDefault.builder()
                .runLevel(RunLevel.STARTUP)
                .build();

        List<ServiceProvider<?>> startupServiceProviders = services.lookupAll(criteria);
        System.out.println("Startup service providers (ranked according to weight, pre-activated): " + startupServiceProviders);

        ServiceProvider<?> highestWeightedServiceProvider = services.lookupFirst(criteria);
        System.out.println("Highest weighted service provider: " + highestWeightedServiceProvider);
        System.out.println("-----");

        // trigger lazy activations for the highest weighted service provider
        System.out.println("Highest weighted service provider (after activation): " + highestWeightedServiceProvider.get());
        System.out.println("-----");

        // trigger all activations for the (remaining unactivated) startup service providers
        startupServiceProviders.forEach(ServiceProvider::get);
        System.out.println("All service providers (after all activations): " + startupServiceProviders);
        System.out.println("-----");
    }

}
