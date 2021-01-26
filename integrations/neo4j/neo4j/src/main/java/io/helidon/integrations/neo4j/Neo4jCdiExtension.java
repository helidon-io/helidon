/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.integrations.neo4j;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfig;

import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.driver.Driver;

/**
 * A CDI Extension for Neo4j support. To be used in MP environment. Delegates all of it activities to
 * Neo4j for initialization and configuration.
 *
 */
public class Neo4jCdiExtension implements Extension {

    private static final String NEO4J_CONFIG_NAME_PREFIX = "neo4j";

    void afterBeanDiscovery(@Observes AfterBeanDiscovery addEvent) {
        addEvent.addBean()
                .types(Driver.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .scope(ApplicationScoped.class)
                .name(Driver.class.getName())
                .beanClass(Driver.class)
                .createWith(creationContext -> {
                    org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
                    Config helidonConfig = MpConfig.toHelidonConfig(config).get(NEO4J_CONFIG_NAME_PREFIX);

                    ConfigValue<Neo4j> configValue = helidonConfig.as(Neo4j::create);
                    if (configValue.isPresent()) {
                        return configValue.get().driver();
                    }
                    throw new Neo4jException("There is no Neo4j driver configured in configuration under key 'neo4j");
                });
    }
}
