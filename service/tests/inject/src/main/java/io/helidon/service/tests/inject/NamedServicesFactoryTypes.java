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

package io.helidon.service.tests.inject;

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;

final class NamedServicesFactoryTypes {
    private NamedServicesFactoryTypes() {
    }

    interface NamedConfig {
        String name();
    }

    interface TargetType {
        NamedConfig config();
    }

    @Service.Singleton
    static class ConfigFactory implements Service.ServicesFactory<NamedConfig> {
        private final List<NamedConfig> configs;

        @Service.Inject
        ConfigFactory() {
            configs = List.of(new NamedConfigImpl("first"),
                              new NamedConfigImpl("second"),
                              new NamedConfigImpl("third"));
        }

        ConfigFactory(List<NamedConfig> configs) {
            this.configs = configs;
        }

        @Override
        public List<Service.QualifiedInstance<NamedConfig>> services() {
            return configs.stream()
                    .map(it -> Service.QualifiedInstance.create(it, Qualifier.createNamed(it.name())))
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    @Service.Singleton
    @Service.PerInstance(NamedConfig.class)
    static class TargetTypeProvider implements TargetType {
        private final NamedConfig config;

        @Service.Inject
        TargetTypeProvider(@Service.InstanceName String name,
                           NamedConfig config,
                           List<ServiceInstance<CharSequence>> emptyList) {
            this.config = config;
            if (!name.equals(config.name())) {
                throw new IllegalStateException("Got name: " + name + " but config is named: " + config.name());
            }
        }

        @Override
        public NamedConfig config() {
            return config;
        }
    }

    @Service.Singleton
    static class NamedReceiver {
        private final NamedConfig config;
        private final List<NamedConfig> all;

        @Service.Inject
        NamedReceiver(@Service.Named("second") NamedConfig config,
                      @Service.Named(Service.Named.WILDCARD_NAME) List<NamedConfig> all) {
            this.config = config;
            this.all = all;
        }

        String name() {
            return config.name();
        }

        List<String> allNames() {
            return all.stream()
                    .map(NamedConfig::name)
                    .collect(Collectors.toList());
        }
    }

    static class NamedConfigImpl implements NamedConfig {
        private final String name;

        NamedConfigImpl(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
