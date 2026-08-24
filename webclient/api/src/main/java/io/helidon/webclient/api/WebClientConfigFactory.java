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

package io.helidon.webclient.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

@Service.Singleton
class WebClientConfigFactory implements Service.ServicesFactory<WebClientConfig> {
    private static final String CONFIG_KEY = "clients";

    private final Supplier<Config> config;
    private final ServiceRegistry serviceRegistry;

    @Service.Inject
    WebClientConfigFactory(Supplier<Config> config, ServiceRegistry serviceRegistry) {
        this.config = config;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public List<Service.QualifiedInstance<WebClientConfig>> services() {
        Config clientsConfig = config.get().get(CONFIG_KEY);
        Map<String, Config> configuredClients = new TreeMap<>();
        boolean isList = clientsConfig.isList();

        for (Config clientConfig : clientsConfig.asNodeList().orElseGet(List::of)) {
            String name = isList
                    ? clientConfig.get("name").asString().orElse(clientConfig.name())
                    : clientConfig.name();
            configuredClients.put(name, clientConfig);
        }

        List<Service.QualifiedInstance<WebClientConfig>> result = new ArrayList<>(configuredClients.size() + 1);
        Config defaultConfig = configuredClients.remove(Service.Named.DEFAULT_NAME);
        result.add(configured(Service.Named.DEFAULT_NAME, defaultConfig == null ? Config.empty() : defaultConfig));
        configuredClients.forEach((name, clientConfig) -> result.add(configured(name, clientConfig)));
        return List.copyOf(result);
    }

    private Service.QualifiedInstance<WebClientConfig> configured(String name, Config config) {
        WebClientConfig webClientConfig = WebClientConfig.builder()
                .config(config)
                .serviceRegistry(serviceRegistry)
                .buildPrototype();
        return Service.QualifiedInstance.create(webClientConfig, Qualifier.createNamed(name));
    }
}
