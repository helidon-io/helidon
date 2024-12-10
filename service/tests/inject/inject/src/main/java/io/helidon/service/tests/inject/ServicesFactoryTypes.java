/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.inject.api.ServiceInstance;

final class ServicesFactoryTypes {
    private ServicesFactoryTypes() {
    }

    interface NamedConfig {
        String name();
    }

    interface TargetType {
        NamedConfig config();
    }

    @Injection.Singleton
    static class ConfigFactory implements Injection.ServicesFactory<NamedConfig> {
        private final List<NamedConfig> configs;

        @Injection.Inject
        ConfigFactory() {
            configs = List.of(new NamedConfigImpl("first"),
                              new NamedConfigImpl("second"),
                              new NamedConfigImpl("third"));
        }

        ConfigFactory(List<NamedConfig> configs) {
            this.configs = configs;
        }

        @Override
        public List<Injection.QualifiedInstance<NamedConfig>> services() {
            return configs.stream()
                    .map(it -> Injection.QualifiedInstance.create(it, Qualifier.createNamed(it.name())))
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    @Injection.Singleton
    @Injection.PerInstance(NamedConfig.class)
    static class TargetTypeProvider implements TargetType {
        private final NamedConfig config;

        @Injection.Inject
        TargetTypeProvider(@Injection.InstanceName String name,
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
