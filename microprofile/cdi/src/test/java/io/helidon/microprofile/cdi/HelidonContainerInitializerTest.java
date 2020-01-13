/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cdi;

import java.util.Map;

import javax.enterprise.inject.se.SeContainerInitializer;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link HelidonContainerInitializer}.
 */
class HelidonContainerInitializerTest {
    @Test
    void testSeInitializerFails() {
        assertThrows(IllegalStateException.class, SeContainerInitializer::newInstance);

        Config config = Config.create(ConfigSources.create(Map.of(HelidonContainerInitializer.CONFIG_ALLOW_INITIALIZER, "true")));
        ConfigProviderResolver.instance().registerConfig((org.eclipse.microprofile.config.Config) config, Thread.currentThread().getContextClassLoader());
        SeContainerInitializer seContainerInitializer = SeContainerInitializer.newInstance();
        assertThat(seContainerInitializer, instanceOf(HelidonContainerInitializer.class));
    }
}
