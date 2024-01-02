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

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.inject.ActivationPhaseReceiver;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.NoOpBinder;
import io.helidon.inject.Phase;
import io.helidon.inject.RegistryServiceProvider;
import io.helidon.inject.ServiceInjectionPlanBinder;
import io.helidon.inject.ServiceProviderBase;
import io.helidon.inject.Services;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.ServiceInfo;

/*
Config bean registry has a single instance per service registry.
 */
class CbrProvider extends ServiceProviderBase<ConfigBeanRegistryImpl>
        implements RegistryServiceProvider<ConfigBeanRegistryImpl>,
                   ActivationPhaseReceiver {
    private final Services services;
    private final ConfigBeanRegistryImpl instance;
    private final InjectionConfig servicesConfig;
    private final CbrBinder binder = new CbrBinder();

    CbrProvider(Services services,
                CbrServiceDescriptor serviceSource) {
        super(services, serviceSource);
        this.services = services;
        this.instance = new ConfigBeanRegistryImpl();
        this.servicesConfig = services.injectionServices().config();

        super.state(Phase.ACTIVATION_FINISHING, instance);
    }

    @Override
    public void onPhaseEvent(Phase phase) {

        if (phase == Phase.POST_BIND_ALL_MODULES) {
            if (services.limitRuntimePhase() == Phase.ACTIVE) {
                Config config;
                if (binder.config != null) {
                    // we run with an Application, no need to lookup
                    config = (Config) services.serviceProviders().get(binder.config).get();
                } else {
                    // we can lookup from the registry, as services can be activated
                    config = servicesConfig.serviceConfig()
                            .or(() -> services.first(Config.class)
                                    .map(Supplier::get))
                            .orElseGet(GlobalConfig::config);
                }

                instance.initialize(config);
                super.state(Phase.ACTIVE, instance);
            } else {
                /*
                 we cannot use registry, and we should not initialize anything
                 if we attempt to create service providers the usual way, we would end up with exceptions,
                 as there may be required instances (and config should/may be empty)
                 */
            }
        }
    }

    @Override
    public ConfigBeanRegistryImpl get() {
        return instance;
    }

    @Override
    public Optional<ServiceInjectionPlanBinder.Binder> injectionPlanBinder() {
        return super.injectionPlanBinder();
    }

    private class CbrBinder extends NoOpBinder {
        private ServiceInfo config;

        private CbrBinder() {
            super(CbrProvider.this);
        }

        @Override
        public ServiceInjectionPlanBinder.Binder bind(Ip injectionPoint, ServiceInfo serviceInfo) {
            // there is only one supported injection point
            config = serviceInfo;
            return this;
        }
    }
}
