/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.ServiceLoader;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.microprofile.server.RoutingBuilders;
import io.helidon.microprofile.server.spi.MpService;
import io.helidon.microprofile.server.spi.MpServiceContext;

import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

/**
 * Helidon Microprofile Server extension for Health checks.
 */
public class HealthMpService implements MpService {
    private static final Logger LOGGER = Logger.getLogger(HealthMpService.class.getName());

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

    @Override
    public void configure(MpServiceContext mpServiceContext) {
        Config healthConfig = mpServiceContext.helidonConfig().get("health");

        if (!healthConfig.get("enabled").asBoolean().orElse(true)) {
            LOGGER.finest("Health support is disabled in configuration");
            return;
        }

        HealthSupport.Builder builder = HealthSupport.builder()
                .config(healthConfig);

        SeContainer cdiContainer = mpServiceContext.cdiContainer();

        cdiContainer.select(HealthCheck.class, HEALTH_LITERAL)
                .stream()
                .forEach(builder::add);

        cdiContainer.select(HealthCheck.class, LIVENESS_LITERAL)
                .stream()
                .forEach(builder::addLiveness);

        cdiContainer.select(HealthCheck.class, READINESS_LITERAL)
                .stream()
                .forEach(builder::addReadiness);

        HelidonServiceLoader.create(ServiceLoader.load(HealthCheckProvider.class))
                .forEach(healthCheckProvider -> {
                    builder.add(healthCheckProvider.healthChecks());
                    healthCheckProvider.livenessChecks().forEach(builder::addLiveness);
                    healthCheckProvider.readinessChecks().forEach(builder::addReadiness);
                });

        RoutingBuilders.create(mpServiceContext, healthConfig)
                .routingBuilder()
                .register(builder.build());
    }
}
