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

package io.helidon.inject.configdriven;

import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.inject.ActivationPhaseReceiver;
import io.helidon.inject.Phase;
import io.helidon.inject.ServiceProvider;
import io.helidon.inject.ServiceProviderBase;
import io.helidon.inject.Services;

/*
Config bean registry has a single instance per service registry.
 */
class CbrProvider extends ServiceProviderBase<ConfigBeanRegistryImpl>
        implements ServiceProvider<ConfigBeanRegistryImpl>,
                   ActivationPhaseReceiver {
    private final Services services;
    private final ConfigBeanRegistryImpl instance;

    CbrProvider(Services services,
                CbrServiceDescriptor serviceSource) {
        super(services, serviceSource);
        this.services = services;
        instance = new ConfigBeanRegistryImpl();
        super.state(Phase.ACTIVATION_FINISHING, instance);
    }

    @Override
    public void onPhaseEvent(Phase phase) {

        if (phase == Phase.POST_BIND_ALL_MODULES) {
            if (services.limitRuntimePhase() == Phase.ACTIVE) {
                // we can lookup from the registry, as services can be activated
                instance.initialize(services.first(Config.class)
                                       .map(Supplier::get)
                                       .orElseGet(GlobalConfig::config));
                super.state(Phase.ACTIVE, instance);
            } else {
                // we cannot use registry
                instance.initialize(GlobalConfig.config());
            }
        }
    }

    @Override
    public ConfigBeanRegistryImpl get() {
        return instance;
    }
}
