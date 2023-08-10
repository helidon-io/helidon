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

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.servicecommon.HelidonRestCdiExtension;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.observe.health.HealthFeature;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import org.eclipse.microprofile.config.ConfigProvider;
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
public class HealthCdiExtension extends HelidonRestCdiExtension<HealthFeature> {
    private static final BuiltInHealthCheck BUILT_IN_HEALTH_CHECK_LITERAL = new BuiltInHealthCheck() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return BuiltInHealthCheck.class;
        }
    };

    private static final System.Logger LOGGER = System.getLogger(HealthCdiExtension.class.getName());
    private static final Function<Config, HealthFeature> HEALTH_SUPPORT_FACTORY = (Config helidonConfig) -> {

        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();

        HealthFeature.Builder builder = HealthFeature.builder()
                .details(true)
                .config(helidonConfig);

        CDI<Object> cdi = CDI.current();

        // Collect built-in checks if disabled, otherwise set list to empty for filtering
        Optional<Boolean> disableDefaults = config.getOptionalValue("mp.health.disable-default-procedures",
                                                                    Boolean.class);

        if (!disableDefaults.orElse(false)) {
            // defaults are enabled
            HelidonServiceLoader.create(ServiceLoader.load(io.helidon.health.spi.HealthCheckProvider.class))
                    .asList()
                    .stream()
                    .flatMap(it -> it.healthChecks(helidonConfig).stream())
                    .forEach(builder::addCheck);
        }

        List<HealthCheck> builtInHealthChecks = disableDefaults.map(
                        b -> b ? cdi.select(HealthCheck.class, BUILT_IN_HEALTH_CHECK_LITERAL)
                                .stream()
                                .collect(Collectors.toList()) : Collections.<HealthCheck>emptyList())
                .orElse(Collections.emptyList());

        cdi.select(HealthCheck.class, Liveness.Literal.INSTANCE)
                .stream()
                .filter(hc -> !builtInHealthChecks.contains(hc))
                .forEach(it -> builder.addCheck(MpCheckWrapper.create(LIVENESS, it)));

        cdi.select(HealthCheck.class, Readiness.Literal.INSTANCE)
                .stream()
                .filter(hc -> !builtInHealthChecks.contains(hc))
                .forEach(it -> builder.addCheck(MpCheckWrapper.create(READINESS, it)));

        cdi.select(HealthCheck.class, Startup.Literal.INSTANCE)
                .stream()
                .filter(hc -> !builtInHealthChecks.contains(hc))
                .forEach(it -> builder.addCheck(MpCheckWrapper.create(STARTUP, it)));

        HelidonServiceLoader.create(ServiceLoader.load(HealthCheckProvider.class))
                .forEach(healthCheckProvider -> {
                    healthCheckProvider.livenessChecks().forEach(it -> builder.addCheck(MpCheckWrapper.create(LIVENESS, it)));
                    healthCheckProvider.readinessChecks().forEach(it -> builder.addCheck(MpCheckWrapper.create(READINESS, it)));
                    healthCheckProvider.startupChecks().forEach(it -> builder.addCheck(MpCheckWrapper.create(STARTUP, it)));
                });

        return builder.build();
    };

    /**
     * Creates a new instance of the health CDI extension.
     */
    public HealthCdiExtension() {
        super(LOGGER, HEALTH_SUPPORT_FACTORY, "health");
    }

    @Override
    protected void processManagedBean(ProcessManagedBean<?> processManagedBean) {
        // Annotated sites are handled in registerHealth.
    }

    @Override
    public HttpRules registerService(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class)
                                     Object adv,
                                     BeanManager bm,
                                     ServerCdiExtension server) {
        HttpRules defaultRouting = super.registerService(adv, bm, server);

        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        if (!config.getOptionalValue("health.enabled", Boolean.class).orElse(true)) {
            LOGGER.log(Level.TRACE, "Health support is disabled in configuration");
        }
        return defaultRouting;
    }

}
