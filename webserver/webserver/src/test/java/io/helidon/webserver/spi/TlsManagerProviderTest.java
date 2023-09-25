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

package io.helidon.webserver.spi;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webserver.ConfiguredTlsManager;
import io.helidon.webserver.TlsManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TlsManagerProviderTest {

    @Test
    void noConfigurationPresent() {
        Config cfg = Config.just(
                ConfigSources.create(
                        Map.of(),
                        "test-cfg"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> TlsManagerProvider.create(cfg));
        assertThat(e.getMessage(),
                   equalTo("Expected to have one manager defined for config: ''; but instead found: 0"));
    }

    @Test
    void tooManyManagersPresentInConfig() {
        Config cfg = Config.just(
                ConfigSources.create(
                        Map.of("manager1", "1",
                               "manager2", "2"),
                        "test-cfg"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> TlsManagerProvider.create(cfg));
        assertThat(e.getMessage(),
                   equalTo("Expected to have one manager defined for config: ''; but instead found: 2"));
    }

    @Test
    void managerNotFoundInConfig() {
        Config cfg = Config.just(
                ConfigSources.create(
                        Map.of("manager.x", "1",
                               "manager.y", "2",
                               "manager.z", "3"),
                        "test-cfg"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> TlsManagerProvider.create(cfg));
        assertThat(e.getMessage(),
                   equalTo("Expected to find a provider named 'manager' but did not find it in: [fake]"));
    }

    @Test
    void goodConfig() {
        Config cfg = Config.just(
                ConfigSources.create(
                        Map.of("fake.x", "1",
                               "fake.y", "2",
                               "fake.z", "3"),
                        "test-cfg"));
        TlsManager manager = TlsManagerProvider.create(cfg);
        assertThat(manager,
                   instanceOf(ConfiguredTlsManager.class));
        ConfiguredTlsManager configuredTlsManager = (ConfiguredTlsManager) manager;
        assertThat(configuredTlsManager.name(),
                   equalTo("fake"));
        assertThat(configuredTlsManager.type(),
                   equalTo("fake-type"));
    }

}
