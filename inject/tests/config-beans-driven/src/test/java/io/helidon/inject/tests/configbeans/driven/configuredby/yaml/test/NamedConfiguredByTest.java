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

package io.helidon.inject.tests.configbeans.driven.configuredby.yaml.test;

import io.helidon.common.config.GlobalConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NamedConfiguredByTest {
    InjectionServices injectionServices;
    Services services;

    @AfterEach
    void tearDown() {
        InjectionTestingSupport.shutdown(injectionServices);
    }

    @BeforeEach
    void setup() {
        Config config = Config.builder()
                .addSource(ConfigSources.classpath("application.yaml"))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        GlobalConfig.config(() -> config, true);

        this.injectionServices = InjectionServices.create(InjectionConfig.builder()
                                                                  .serviceConfig(config)
                                                                  .build());
        this.services = injectionServices.services();
    }

    @Test
    void namedConfiguredServices() {
        services.get(Lookup.builder()
                             .addContract(AsyncConfig.class)
                             .addQualifier(Qualifier.createNamed("first"))
                             .build());

        services.get(Lookup.builder()
                             .addContract(AsyncConfig.class)
                             .addQualifier(Qualifier.createNamed("second"))
                             .build());
    }

}
