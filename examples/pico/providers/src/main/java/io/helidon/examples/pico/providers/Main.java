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

package io.helidon.examples.pico.providers;

import java.util.List;

import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.RunLevel;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceInfoCriteriaDefault;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;

/**
 * Providers example.
 */
public class Main {

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        Services services = PicoServices.realizedServices();

        ServiceInfoCriteria criteria = ServiceInfoCriteriaDefault.builder()
                .runLevel(RunLevel.STARTUP)
                .build();

        List<ServiceProvider<?>> startupServiceProviders = services.lookupAll(criteria);
        System.out.println("Startup service providers (ranked according to weight, pre-activated): " + startupServiceProviders);

        // trigger all activations for startup service providers
        startupServiceProviders.forEach(ServiceProvider::get);
        System.out.println("All service providers (after all activations): " + startupServiceProviders);
    }

}
