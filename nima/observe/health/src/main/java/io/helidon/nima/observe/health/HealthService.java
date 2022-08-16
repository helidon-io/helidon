/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.observe.health;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckType;
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.jsonp.JsonpMediaSupportProvider;
import io.helidon.nima.servicecommon.HelidonRestServiceSupport;
import io.helidon.nima.webserver.http.HttpRules;

import jakarta.json.JsonObject;

import static io.helidon.health.HealthCheckType.LIVENESS;
import static io.helidon.health.HealthCheckType.READINESS;
import static io.helidon.health.HealthCheckType.STARTUP;

/**
 * Observe health endpoints.
 * This service provides endpoints for {@link io.helidon.common.http.Http.Method#GET} and
 * {@link io.helidon.common.http.Http.Method#HEAD} methods.
 */
public class HealthService extends HelidonRestServiceSupport {
    private static final System.Logger LOGGER = System.getLogger(HealthService.class.getName());

    private final boolean details;
    private final Map<String, HealthCheck> all;
    private final Map<String, HealthCheck> ready;
    private final Map<String, HealthCheck> live;
    private final Map<String, HealthCheck> start;
    private final boolean enabled;

    private HealthService(Builder builder) {
        super(LOGGER, builder, "health");

        this.details = builder.details;
        this.enabled = builder.enabled;

        this.all = new LinkedHashMap<>(builder.allChecks);
        this.ready = new LinkedHashMap<>(builder.readyChecks);
        this.live = new LinkedHashMap<>(builder.liveChecks);
        this.start = new LinkedHashMap<>(builder.startChecks);
    }

    /**
     * Create a new builder.
     *
     * @return new builder to customize configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new instance with explicit list of health checks. Discover through {@link java.util.ServiceLoader}
     * will be disabled.
     *
     * @param healthChecks health checks to use
     * @return a new health observer
     */
    public static HealthService create(HealthCheck... healthChecks) {
        return builder()
                .useSystemServices(false)
                .update(it -> {
                    for (HealthCheck healthCheck : healthChecks) {
                        it.addCheck(healthCheck);
                    }
                })
                .build();
    }

    @Override
    public void routing(HttpRules rules) {
        if (enabled) {
            configureEndpoint(rules, rules);
        }
    }

    @Override
    protected void postConfigureEndpoint(HttpRules defaultRules, HttpRules serviceEndpointRoutingRules) {
        EntityWriter<JsonObject> entityWriter = JsonpMediaSupportProvider.serverResponseWriter();

        serviceEndpointRoutingRules.get("/", new HealthHandler(entityWriter, details, all))
                .get("/" + READINESS.defaultEndpoint(), new HealthHandler(entityWriter, details, ready))
                .get("/" + LIVENESS.defaultEndpoint(), new HealthHandler(entityWriter, details, live))
                .get("/" + STARTUP.defaultEndpoint(), new HealthHandler(entityWriter, details, start))
                .get("/" + READINESS.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, details, ready))
                .get("/" + LIVENESS.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, details, live))
                .get("/" + STARTUP.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, details, start))
                .get("/check/{name}", new SingleCheckHandler(entityWriter, details, all))
                .head("/", new HealthHandler(entityWriter, false, all))
                .head("/" + READINESS.defaultEndpoint(), new HealthHandler(entityWriter, false, ready))
                .head("/" + LIVENESS.defaultEndpoint(), new HealthHandler(entityWriter, false, live))
                .head("/" + STARTUP.defaultEndpoint(), new HealthHandler(entityWriter, false, start))
                .head("/" + READINESS.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, false, ready))
                .head("/" + LIVENESS.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, false, live))
                .head("/" + STARTUP.defaultEndpoint() + "/{name}", new SingleCheckHandler(entityWriter, false, start))
                .head("/check/{name}", new SingleCheckHandler(entityWriter, false, all));

    }

    /**
     * Fluent API builder for {@link io.helidon.nima.observe.health.HealthService}.
     */
    public static class Builder extends HelidonRestServiceSupport.Builder<Builder, HealthService> {
        private final HelidonServiceLoader.Builder<HealthCheckProvider> providers =
                HelidonServiceLoader.builder(ServiceLoader.load(HealthCheckProvider.class));
        private final Map<String, HealthCheck> allChecks = new LinkedHashMap<>();
        private final Map<String, HealthCheck> readyChecks = new LinkedHashMap<>();
        private final Map<String, HealthCheck> liveChecks = new LinkedHashMap<>();
        private final Map<String, HealthCheck> startChecks = new LinkedHashMap<>();

        private boolean enabled = true;
        private boolean details = false;

        Builder() {
            super("health");
        }

        @Override
        public HealthService build() {
            providers.build()
                    .asList()
                    .stream()
                    // TODO use configuration
                    .map(provider -> provider.healthChecks(Config.empty()))
                    .flatMap(Collection::stream)
                    .forEach(it -> addCheck(it, it.type(), false));
            return new HealthService(this);
        }

        /**
         * Whether "observe health" should be enabled.
         *
         * @param enabled set to {@code false} to disable health observer
         * @return updated builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Whether details should be printed.
         * By default, health only returns a {@link io.helidon.common.http.Http.Status#NO_CONTENT_204} for success,
         * {@link io.helidon.common.http.Http.Status#SERVICE_UNAVAILABLE_503} for health down,
         * and {@link io.helidon.common.http.Http.Status#INTERNAL_SERVER_ERROR_500} in case of error with no entity.
         * When details are enabled, health returns {@link io.helidon.common.http.Http.Status#OK_200} for success, same codes
         * otherwise
         * and a JSON entity with detailed information about each health check executed.
         *
         * @param details set to {@code true} to enable details
         * @return updated builder
         */
        public Builder details(boolean details) {
            this.details = details;
            return this;
        }

        /**
         * Update this instance from configuration.
         *
         * @param config config located at the node of health support (see {@link HealthObserveProvider#configKey()}).
         * @return updated builder
         */
        public Builder config(Config config) {
            super.config(config);

            config.get("enabled").asBoolean().ifPresent(this::enabled);
            config.get("details").asBoolean().ifPresent(this::details);
            return this;
        }

        /**
         * Add an explicit Health check instance (not discovered through
         * {@link io.helidon.health.spi.HealthCheckProvider}
         * or when {@link #useSystemServices(boolean)} is set to {@code false}.
         *
         * @param healthCheck health check to add
         * @return updated builder
         */
        public Builder addCheck(HealthCheck healthCheck) {
            return addCheck(healthCheck, healthCheck.type());
        }

        /**
         * Add the provided health check using an explicit type (may differ from the
         * {@link io.helidon.health.HealthCheck#type()}.
         *
         * @param healthCheck health check to add
         * @param type        type to use
         * @return updated builder
         */
        public Builder addCheck(HealthCheck healthCheck, HealthCheckType type) {
            return addCheck(healthCheck, type, true);
        }

        /**
         * Whether to use services discovered by {@link java.util.ServiceLoader}.
         *
         * @param useServices set to {@code false} to disable discovery
         * @return updated builder
         */
        public Builder useSystemServices(boolean useServices) {
            providers.useSystemServiceLoader(useServices);
            return this;
        }

        private Builder addCheck(HealthCheck healthCheck, HealthCheckType type, boolean replace) {
            this.allChecks.put(healthCheck.name(), healthCheck);
            Map<String, HealthCheck> map = switch (type) {
                case READINESS -> readyChecks;
                case LIVENESS -> liveChecks;
                case STARTUP -> startChecks;
            };

            if (replace) {
                map.put(healthCheck.name(), healthCheck);
            } else {
                map.putIfAbsent(healthCheck.name(), healthCheck);
            }
            return this;
        }
    }
}
