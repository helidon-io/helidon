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

package io.helidon.pico.services;

import java.util.Map;
import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.DefaultBootstrap;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.services.testsubjects.HelloPicoApplication;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.MatcherAssert.assertThat;

class DefaultPicoServicesTest {

    @BeforeEach
    void setUp() {
        tearDown();
        Config config = Config.create(
                ConfigSources.create(
                        Map.of(PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_DYNAMIC, "true"), "config-1"));
        PicoServices.globalBootstrap(DefaultBootstrap.builder().config(config).build());
    }

    @AfterEach
    void tearDown() {
        HelloPicoApplication.ENABLED = true;
        SimplePicoTestingSupport.resetAll();
    }

    @Test
    void realizedServices() {
        assertThat(PicoServices.unrealizedServices(), optionalEmpty());

        Objects.requireNonNull(PicoServices.realizedServices());
        assertThat(PicoServices.unrealizedServices(), optionalPresent());
    }

}
