/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.config;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.registry.Service;

/**
 * Default factory for configuration, that returns the current global config, or creates a new config instance.
 * This is to ensure we have a service registry instance even when there is no implementation on classpath.
 */
@SuppressWarnings("removal")
@Weight(Weighted.DEFAULT_WEIGHT - 20)
@Service.Singleton
class ConfigFactory implements Supplier<Config> {
    @Override
    public Config get() {
        // once GlobalConfig gets removed, we just return Config.create()
        return GlobalConfig.configured() ? GlobalConfig.config() : Config.create();
    }
}
