/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.config.spi.testsubjects;

import java.util.Optional;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigValue;
import io.helidon.common.config.spi.ConfigProvider;

import jakarta.inject.Singleton;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Singleton
@SuppressWarnings("unchecked")
@Deprecated
public class TestConfigProvider implements ConfigProvider {

    @Override
    public Optional<Config> __config() {
        ConfigValue<String> val = mock(ConfigValue.class);
        when(val.get()).thenReturn("mock");

        Config cfg = mock(Config.class);
        when(cfg.asString()).thenReturn(val);

        return Optional.of(cfg);
    }

}
