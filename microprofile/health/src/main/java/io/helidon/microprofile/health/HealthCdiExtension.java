/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.servicecommon.HelidonRestCdiExtension;
import io.helidon.webserver.observe.health.HealthObserver;
import io.helidon.webserver.observe.health.HealthObserverConfig;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;

import static io.helidon.health.HealthCheckType.LIVENESS;
import static io.helidon.health.HealthCheckType.READINESS;
import static io.helidon.health.HealthCheckType.STARTUP;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * Health extension.
 */
public class HealthCdiExtension extends HelidonRestCdiExtension {
    private static final BuiltInHealthCheck BUILT_IN_HEALTH_CHECK_LITERAL = new BuiltInHealthCheck() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return BuiltInHealthCheck.class;
        }
    };

    private static final System.Logger LOGGER = System.getLogger(HealthCdiExtension.class.getName());

    /**
     * Creates a new instance of the health CDI extension.
     */
    public HealthCdiExtension() {
        super(LOGGER, nestedConfigKey("health"), "health");
    }

    /**
     * Register the Health observer with server observer feature.
     * This is a CDI observer method invoked by CDI machinery.
     *
     * @param event  event object
     * @param server Server CDI extension
     */
    public void registerService(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class)
                                Object event,
                                ServerCdiExtension server) {

        server.addObserver(configure());
    }

    private HealthObserver configure() {
        HealthObserverConfig.Builder builder = HealthObserver.builder();
        Config config = componentConfig();
        builder.details(true)
                .endpoint("/health") // absolute URI to align with MP
                .config(config);

        CDI<Object> cdi = CDI.current();

        List<HealthCheck> builtInsFilter;
        if (rootConfig().get("mp.health.disable-default-procedures").asBoolean().orElse(false)) {
            builder.useSystemServices(false);
            builtInsFilter = cdi.select(HealthCheck.class, BUILT_IN_HEALTH_CHECK_LITERAL)
                    .stream()
                    .toList();
        } else {
            builtInsFilter = List.of();
        }

        cdi.select(HealthCheck.class, Liveness.Literal.INSTANCE)
                .stream()
                .filter(hc -> !builtInsFilter.contains(hc))
                .forEach(it -> builder.addCheck(MpCheckWrapper.create(LIVENESS, it)));

        cdi.select(HealthCheck.class, Readiness.Literal.INSTANCE)
                .stream()
                .filter(hc -> !builtInsFilter.contains(hc))
                .forEach(it -> builder.addCheck(MpCheckWrapper.create(READINESS, it)));

        cdi.select(HealthCheck.class, Startup.Literal.INSTANCE)
                .stream()
                .filter(hc -> !builtInsFilter.contains(hc))
                .forEach(it -> builder.addCheck(MpCheckWrapper.create(STARTUP, it)));

        // load MP health check providers
        HelidonServiceLoader.create(ServiceLoader.load(HealthCheckProvider.class))
                .forEach(healthCheckProvider -> {
                    healthCheckProvider.livenessChecks().forEach(it -> builder.addCheck(MpCheckWrapper.create(LIVENESS, it)));
                    healthCheckProvider.readinessChecks().forEach(it -> builder.addCheck(MpCheckWrapper.create(READINESS, it)));
                    healthCheckProvider.startupChecks().forEach(it -> builder.addCheck(MpCheckWrapper.create(STARTUP, it)));
                });

        return builder.build();
    }

}
