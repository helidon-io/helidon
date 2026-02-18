/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.service.injected.config;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.MapConfigSource;
import io.helidon.config.MetaConfig;
import io.helidon.config.spi.ConfigSource;
import io.helidon.service.registry.Service;

@Service.Singleton
public class ServiceInjectedConfigSource implements Supplier<ConfigSource> {
    static final String KEY = "key";
    static final String VALUE = "value";
    private final LazyValue<ConfigSource> configSourceLazyValue = LazyValue.create(this::create);

    public ServiceInjectedConfigSource() {
    }

    public ConfigSource create() {
        return MapConfigSource.create(Map.of(KEY, VALUE));
    }

    @Override
    public ConfigSource get() {
        return configSourceLazyValue.get();
    }
}
