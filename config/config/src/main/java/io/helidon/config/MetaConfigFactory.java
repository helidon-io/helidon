/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

/**
 * A ServiceRegistry factory that creates the meta config only if it is defined.
 */
@Service.Singleton
class MetaConfigFactory implements Service.ServicesFactory<MetaConfig> {
    private static final System.Logger LOGGER = System.getLogger(MetaConfigFactory.class.getName());

    @Override
    public List<Service.QualifiedInstance<MetaConfig>> services() {
        var foundMetaConfig = MetaConfig.metaConfig();
        if (foundMetaConfig.isEmpty()) {
            return List.of();
        }

        var metaConfig = foundMetaConfig.get();

        List<Service.QualifiedInstance<MetaConfig>> instances = new ArrayList<>();

        // add the main meta config
        instances.add(Service.QualifiedInstance.create(new MetaConfig(metaConfig)));

        // now for each config source type defined add a named instance
        var sources = metaConfig.get("sources")
                .asNodeList()
                .orElse(List.of());

        Map<String, List<NameAndConfig>> byType = new HashMap<>();
        for (Config source : sources) {
            String type = source.get("type").asString()
                    .orElseThrow(() -> new ConfigException("Missing type of a config source in config-profile sources."));
            String name = source.get("name").asString().orElse(type);
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(new NameAndConfig(name, source.get("properties")));
        }

        byType.forEach((type, configs) -> {
            if (configs.size() == 1) {
                // only add if exactly one for this type, otherwise we cannot create an instance from registry
                instances.add(Service.QualifiedInstance.create(new MetaConfig(configs.getFirst().config()),
                                                               Qualifier.createNamed(type)));
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               "Adding config source meta-configuration for type {0} to service registry.",
                               type);
                }
            } else {
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               "There are {0} sources of type {1} defined in config-profile sources, "
                                       + "but only one is allowed for service registry. Ignoring this type",
                               configs.size(), type);
                }
            }
        });

        return instances;
    }

    private record NameAndConfig(String name, Config config) {

    }
}
