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

package io.helidon.examples.inject.application;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class InjectionApplicationTest {

    protected static InjectionServices injectionServices;
    protected static Services services;

    @AfterAll
    static void tearDown() {
        InjectionTestingSupport.shutdown(injectionServices);
    }

    protected void resetWith(Config config) {
        tearDown();
        injectionServices = InjectionServices.create(InjectionConfig.builder()
                                                          .serviceConfig(config)
                                                          .build());
        services = injectionServices.services();
    }

    @Test
    void main() {
        Config config = Config.builder()
                .addSource(ConfigSources.classpath("application.yaml"))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        resetWith(config);
        Main.main();
    }

}
