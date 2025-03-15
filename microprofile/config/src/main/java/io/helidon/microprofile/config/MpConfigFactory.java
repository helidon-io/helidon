/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.config;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.service.registry.Service;

import org.eclipse.microprofile.config.ConfigProvider;

// per lookup, to make sure we provide a config wrapping the current MP config
@Service.PerLookup
// Higher than default, as we are in MicroProfile and we should use MP config
@Weight(Weighted.DEFAULT_WEIGHT + 1)
class MpConfigFactory implements Supplier<Config> {
    MpConfigFactory() {
    }

    @Override
    public Config get() {
        // creates an instance that is automatically provided to CDI through the registry/CDI bridge
        return MpConfig.toHelidonConfig(ConfigProvider.getConfig());
    }
}
