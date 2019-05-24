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
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.microprofile.server.spi.MpService;
import io.helidon.microprofile.server.spi.MpServiceContext;

import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;

/**
 * Helidon Microprofile Server extension for Health checks.
 */
public class HealthMpService implements MpService {
    @Override
    public void configure(MpServiceContext mpServiceContext) {
        Config healthConfig = mpServiceContext.helidonConfig().get("health");
        HealthSupport.Builder builder = HealthSupport.builder()
                .config(healthConfig);

        mpServiceContext.cdiContainer()
                .select(HealthCheck.class, new Health() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return Health.class;
                    }
                })
                .stream()
                .forEach(builder::add);

        HelidonServiceLoader.create(ServiceLoader.load(HealthCheckProvider.class))
                .forEach(healthCheckProvider -> builder.add(healthCheckProvider.healthChecks()));

        healthConfig.get("routing")
                .asString()
                .flatMap(routeName -> {
                    // support for overriding the routing back to default port using config
                    if ("@default".equals(routeName)) {
                        return Optional.empty();
                    } else {
                        return Optional.of(routeName);
                    }
                })
                // use named routing
                .map(mpServiceContext::serverNamedRoutingBuilder)
                // use default server routing
                .orElseGet(mpServiceContext::serverRoutingBuilder)
                // register health support
                .register(builder.build());

    }
}
