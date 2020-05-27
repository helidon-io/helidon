/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.health;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.health.HealthSupport;
import io.helidon.health.common.BuiltInHealthCheck;
import io.helidon.microprofile.server.RoutingBuilders;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

/**
 * Health extension.
 */
public class HealthCdiExtension implements Extension {
    // must be used until removed from MP specification
    @SuppressWarnings("deprecation")
    private static final Health HEALTH_LITERAL = new Health() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Health.class;
        }
    };

    private static final Readiness READINESS_LITERAL = new Readiness() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Readiness.class;
        }
    };

    private static final Liveness LIVENESS_LITERAL = new Liveness() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Liveness.class;
        }
    };

    private static final BuiltInHealthCheck BUILT_IN_HEALTH_CHECK_LITERAL = new BuiltInHealthCheck() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return BuiltInHealthCheck.class;
        }
    };

    private static final Logger LOGGER = Logger.getLogger(HealthCdiExtension.class.getName());

    static {
        HelidonFeatures.register(HelidonFlavor.MP, "Health");
    }

    void registerProducers(@Observes BeforeBeanDiscovery bbd) {
        bbd.addAnnotatedType(JvmRuntimeProducers.class, "health.JvmRuntimeProducers")
                .add(ApplicationScoped.Literal.INSTANCE);
    }

    void registerHealth(@Observes @Initialized(ApplicationScoped.class) Object adv) {
        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        Config helidonConfig = MpConfig.toHelidonConfig(config).get("health");

        if (!config.getOptionalValue("health.enabled", Boolean.class).orElse(true)) {
            LOGGER.finest("Health support is disabled in configuration");
            return;
        }

        HealthSupport.Builder builder = HealthSupport.builder()
                .config(helidonConfig);

        CDI<Object> cdi = CDI.current();

        // Collect built-in checks if disabled, otherwise set list to empty for filtering
        Optional<Boolean> disableDefaults = config.getOptionalValue("mp.health.disable-default-procedures",
                                                                    Boolean.class);

        List<HealthCheck> builtInHealthChecks = disableDefaults.map(
                b -> b ? cdi.select(HealthCheck.class, BUILT_IN_HEALTH_CHECK_LITERAL)
                        .stream()
                        .collect(Collectors.toList()) : Collections.<HealthCheck>emptyList())
                .orElse(Collections.emptyList());

        // we must use builder.add(HealthCheck) as long as HEALTH_LITERAL can be used
        //noinspection deprecation
        cdi.select(HealthCheck.class, HEALTH_LITERAL)
                .stream()
                .filter(hc -> !builtInHealthChecks.contains(hc))
                .forEach(builder::add);

        cdi.select(HealthCheck.class, LIVENESS_LITERAL)
                .stream()
                .filter(hc -> !builtInHealthChecks.contains(hc))
                .forEach(builder::addLiveness);

        cdi.select(HealthCheck.class, READINESS_LITERAL)
                .stream()
                .filter(hc -> !builtInHealthChecks.contains(hc))
                .forEach(builder::addReadiness);

        HelidonServiceLoader.create(ServiceLoader.load(HealthCheckProvider.class))
                .forEach(healthCheckProvider -> {
                    healthCheckProvider.livenessChecks().forEach(builder::addLiveness);
                    healthCheckProvider.readinessChecks().forEach(builder::addReadiness);
                });

        RoutingBuilders.create(helidonConfig)
                .routingBuilder()
                .register(builder.build());
    }
}
