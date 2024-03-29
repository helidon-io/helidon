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

package io.helidon.config.tests.service.registry;

import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigProducerTest {
    @AfterEach
    void reset() {
        InjectionTestingSupport.resetAll();
    }

    @Test
    @Order(0) // this must be first, as once we set global config, this method will always fail
    void testConfig() {
        InjectionServices.globalBootstrap(Bootstrap.builder()
                                             .config(GlobalConfig.config())
                                             .build());

        // value should be overridden using our custom config source
        Config config = InjectionServices.realizedServices()
                .lookup(Config.class)
                .get();

        assertThat(config.get("app.value").asString().asOptional(), is(Optional.of("source")));
    }

    @Test
    @Order(1)
    void testExplicitConfig() {
        // value should use the config as we provided it
        GlobalConfig.config(io.helidon.config.Config::create, true);

        InjectionServices.globalBootstrap(Bootstrap.builder()
                                             .config(GlobalConfig.config())
                                             .build());

        Config config = InjectionServices.realizedServices()
                .lookup(Config.class)
                .get();

        assertThat(config.get("app.value").asString().asOptional(), is(Optional.of("file")));
    }
}
