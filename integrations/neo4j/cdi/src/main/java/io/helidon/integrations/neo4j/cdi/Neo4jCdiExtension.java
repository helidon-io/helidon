/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.integrations.neo4j.cdi;

import java.util.logging.Level;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.util.AnnotationLiteral;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfig;

import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Created by Dmitry Alexandrov on 12.11.20.
 */
public class Neo4jCdiExtension implements Extension {

    private static final String NEO4J_METRIC_NAME_PREFIX = "neo4j.";

    private static org.neo4j.driver.Config.ConfigBuilder createBaseConfig() {
        org.neo4j.driver.Config.ConfigBuilder configBuilder = org.neo4j.driver.Config.builder();
        Logging logging;
        try {
            logging = Logging.slf4j();
        } catch (Exception e) {
            logging = Logging.javaUtilLogging(Level.INFO);
        }
        configBuilder.withLogging(logging);
        return configBuilder;
    }

    private static void configureSsl(org.neo4j.driver.Config.ConfigBuilder configBuilder,
                                     Neo4JConfig neo4JConfig) {

        if (neo4JConfig.encrypted) {
            configBuilder.withEncryption();
            configBuilder.withTrustStrategy(neo4JConfig.toInternalRepresentation());
        } else {
            configBuilder.withoutEncryption();
        }
    }

    private static void configurePoolSettings(org.neo4j.driver.Config.ConfigBuilder configBuilder, Neo4JConfig neo4JConfig) {

        configBuilder.withMaxConnectionPoolSize(neo4JConfig.maxConnectionPoolSize);
        configBuilder.withConnectionLivenessCheckTimeout(neo4JConfig.idleTimeBeforeConnectionTest.toMillis(), MILLISECONDS);
        configBuilder.withMaxConnectionLifetime(neo4JConfig.maxConnectionLifetime.toMillis(), MILLISECONDS);
        configBuilder.withConnectionAcquisitionTimeout(neo4JConfig.connectionAcquisitionTimeout.toMillis(), MILLISECONDS);

        if (neo4JConfig.metricsEnabled) {
            configBuilder.withDriverMetrics();
        } else {
            configBuilder.withoutDriverMetrics();
        }
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery addEvent, BeanManager beanManager) {
        final org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        final Config helidonConfig = MpConfig.toHelidonConfig(config).get(NEO4J_METRIC_NAME_PREFIX);

        addEvent.addBean()
                .types(Driver.class)
                .qualifiers(new AnnotationLiteral<Default>() {
                }, new AnnotationLiteral<Any>() {
                })
                .scope(ApplicationScoped.class)
                .name(Driver.class.getName())
                .beanClass(Driver.class)
                .createWith(creationContext -> {
                    ConfigValue<Neo4JConfig> configValue = helidonConfig.as(Neo4JConfig::create);
                    Neo4JConfig neo4JConfig = configValue.get();

                    String uri = neo4JConfig.uri;
                    AuthToken authToken = AuthTokens.none();
                    if (!neo4JConfig.disabled) {
                        authToken = AuthTokens.basic(neo4JConfig.username, neo4JConfig.password);
                    }

                    org.neo4j.driver.Config.ConfigBuilder configBuilder = createBaseConfig();
                    configureSsl(configBuilder, neo4JConfig);
                    configurePoolSettings(configBuilder, neo4JConfig);

                    Driver driver = GraphDatabase.driver(uri, authToken, configBuilder.build());

                    return driver;
                });
    }

}
