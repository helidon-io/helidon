/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.health;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.faulttolerance.Async;
import io.helidon.faulttolerance.Timeout;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.servicecommon.rest.HelidonRestServiceSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;

/**
 * Health check support for integration with webserver, to expose the health endpoint.
 */
public final class HealthSupport extends HelidonRestServiceSupport {
    /**
     * Default web context root of the Health check endpoint.
     */
    public static final String DEFAULT_WEB_CONTEXT = "/health";

    private static final String SERVICE_NAME = "Health";

    private static final Logger LOGGER = Logger.getLogger(HealthSupport.class.getName());

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final boolean enabled;
    private final List<HealthCheck> allChecks = new LinkedList<>();
    private final List<HealthCheck> livenessChecks = new LinkedList<>();
    private final List<HealthCheck> readinessChecks = new LinkedList<>();
    private final List<HealthCheck> startupChecks = new LinkedList<>();
    private final boolean includeAll;
    private final Set<String> includedHealthChecks;
    private final Set<String> excludedHealthChecks;
    private final MessageBodyWriter<JsonStructure> jsonpWriter = JsonpSupport.writer();
    private final Timeout timeout;
    private final Async async;

    private HealthSupport(Builder builder) {
        super(LOGGER, builder, SERVICE_NAME);
        this.enabled = builder.enabled;

        if (enabled) {
            collectNonexcludedChecks(builder, builder.allChecks, allChecks::add);
            collectNonexcludedChecks(builder, builder.readinessChecks, readinessChecks::add);
            collectNonexcludedChecks(builder, builder.livenessChecks, livenessChecks::add);
            collectNonexcludedChecks(builder, builder.startupChecks, startupChecks::add);

            this.includedHealthChecks = new HashSet<>(builder.includedHealthChecks);
            this.excludedHealthChecks = new HashSet<>(builder.excludedHealthChecks);

            includeAll = includedHealthChecks.isEmpty();
        } else {
            this.includeAll = true;
            this.includedHealthChecks = Collections.emptySet();
            this.excludedHealthChecks = Collections.emptySet();
        }


        this.timeout = Timeout.create(Duration.ofMillis(builder.timeoutMillis));
        this.async = Async.create();
    }

    @Override
    public void update(Routing.Rules rules) {
        configureEndpoint(rules, rules);
    }

    @Override
    protected void postConfigureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules) {
        if (enabled) {
            serviceEndpointRoutingRules
                    .get(context(), this::getAll)
                    .get(context() + "/live", this::getLiveness)
                    .get(context() + "/ready", this::getReadiness)
                    .get(context() + "/started", this::getStartup)
                    .head(context(), this::headAll)
                    .head(context() + "/live", this::headLiveness)
                    .head(context() + "/ready", this::headReadiness)
                    .head(context() + "/started", this::headStartup);
        }
    }

    private static void collectNonexcludedChecks(Builder builder, List<HealthCheck> checks, Consumer<HealthCheck> adder) {
        checks.stream()
               .filter(health -> !builder.excludedClasses.contains(health.getClass()))
               .forEach(adder);
    }

    private void getAll(ServerRequest req, ServerResponse res) {
        get(res, allChecks);
    }

    private void getLiveness(ServerRequest req, ServerResponse res) {
        get(res, livenessChecks);
    }

    private void getReadiness(ServerRequest req, ServerResponse res) {
        get(res, readinessChecks);
    }

    private void getStartup(ServerRequest req, ServerResponse res) {
        get(res, startupChecks);
    }

    private void headAll(ServerRequest req, ServerResponse res) {
        head(res, allChecks);
    }

    private void headLiveness(ServerRequest req, ServerResponse res) {
        head(res, livenessChecks);
    }

    private void headReadiness(ServerRequest req, ServerResponse res) {
        head(res, readinessChecks);
    }

    private void headStartup(ServerRequest req, ServerResponse res) {
        head(res, startupChecks);
    }

    private void get(ServerResponse res, List<HealthCheck> healthChecks) {
        invoke(res, healthChecks, true);
    }

    private void head(ServerResponse res, List<HealthCheck> healthChecks) {
        invoke(res, healthChecks, false);
    }

    void invoke(ServerResponse res, List<HealthCheck> healthChecks, boolean sendDetails) {
        // timeout on the asynchronous execution
        Single<HealthResponse> result = timeout.invoke(() -> async.invoke(() -> callHealthChecks(healthChecks)));

        // handle timeouts and failures in execution
        result = result.onErrorResume(throwable -> {
            LOGGER.log(Level.SEVERE, "Failed to call health checks", throwable);
            HcResponse response = new HcResponse(HealthCheckResponse.down("InternalError"), true);
            return new HealthResponse(Http.Status.INTERNAL_SERVER_ERROR_500, toJson(Status.DOWN, List.of(response)));
        });

        result.thenAccept(hres -> {
            int status = hres.status().code();
            if (status == Http.Status.OK_200.code() && !sendDetails) {
                status = Http.Status.NO_CONTENT_204.code();
            }
            res.status(status);
            if (sendDetails) {
                res.send(jsonpWriter.marshall(hres.json));
            } else {
                res.send();
            }
        });
    }

    HealthResponse callHealthChecks(List<HealthCheck> healthChecks) {
        List<HcResponse> responses = healthChecks.stream()
                .map(this::callHealthChecks)
                .filter(this::notExcluded)
                .filter(this::allOrIncluded)
                .sorted(Comparator.comparing(HcResponse::name))
                .collect(Collectors.toList());

        Status status = responses.stream()
                .map(HcResponse::status)
                .filter(Status.DOWN::equals)
                .findFirst()
                .orElse(Status.UP);

        Http.ResponseStatus httpStatus = responses.stream()
                .filter(HcResponse::internalError)
                .findFirst()
                .map(it -> Http.Status.INTERNAL_SERVER_ERROR_500)
                .orElse((status == Status.UP) ? Http.Status.OK_200 : Http.Status.SERVICE_UNAVAILABLE_503);

        JsonObject json = toJson(status, responses);
        return new HealthResponse(httpStatus, json);
    }

    private JsonObject toJson(Status status, List<HcResponse> responses) {
        final JsonObjectBuilder jsonBuilder = JSON.createObjectBuilder();
        jsonBuilder.add("status", status.toString());

        final JsonArrayBuilder checkArrayBuilder = JSON.createArrayBuilder();

        for (HcResponse r : responses) {
            JsonObjectBuilder checkBuilder = JSON.createObjectBuilder();
            checkBuilder.add("name", r.name());
            checkBuilder.add("status", r.status().toString());
            Optional<Map<String, Object>> data = r.data();
            data.ifPresent(m -> checkBuilder.add("data", JSON.createObjectBuilder(m)));

            checkArrayBuilder.add(checkBuilder);
        }

        // Have to add this after the checkArrayBuilder is populated
        jsonBuilder.add("checks", checkArrayBuilder);

        return jsonBuilder.build();
    }

    private boolean allOrIncluded(HcResponse response) {
        return includeAll || includedHealthChecks.contains(response.hcr.getName());
    }

    private boolean notExcluded(HcResponse response) {
        return !excludedHealthChecks.contains(response.hcr.getName());
    }

    private HcResponse callHealthChecks(HealthCheck hc) {
        try {
            return new HcResponse(hc.call());
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to compute health check for " + hc.getClass().getName(), e);

            return new HcResponse(HealthCheckResponse
                                          .named(hc.getClass().getName())
                                          .withData("message", "Failed to compute health. Error logged")
                                          .down()
                                          .build(),
                                  true);
        }
    }

    /**
     * Get a builder to configure health support instance.
     *
     * @return fluent API builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new HealthSupport with no health checks configured.
     * The endpoint will always return {@code UP}.
     *
     * @return health support configured with no health checks
     */
    public static HealthSupport create() {
        return builder().build();
    }

    /**
     * Create a new HealthSupport with no health checks, configured from provided config.
     * The endpoint will always return {@code UP}.
     *
     * @param config configuration of this health check, used only to get {@code web-context} property to configure
     *               {@link Builder#webContext(String)}
     * @return health support configured with no health checks
     */
    public static HealthSupport create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Fluent API builder for {@link io.helidon.health.HealthSupport}.
     */
    @Configured(prefix = Builder.HEALTH_CONFIG_KEY)
    public static final class Builder extends HelidonRestServiceSupport.Builder<Builder, HealthSupport> {

        /**
         * Config key for the {@code health} section.
         */
        public static final String HEALTH_CONFIG_KEY = "health";

        /**
         * Config key within the config {@code health} section controlling whether health is enabled.
         */
        public static final String ENABLED_CONFIG_KEY = "enabled";

        /**
         * Config key within the config {@code health} section indicating health checks to include.
         */
        public static final String INCLUDE_CONFIG_KEY = "include";

        /**
         * Config key within the config {@code health} section indicating health checks to exclude.
         */
        public static final String EXCLUDE_CONFIG_KEY = "exclude";

        /**
         * Config key within the config {@code health} section indicating health check implementation classes to exclude.
         */
        public static final String EXCLUDE_CLASSES_CONFIG_KEY = "exclude-classes";

        /**
         * Config key within the config {@code health} section controlling the timeout for calculating the health report when
         * clients access the health endpoint.
         */
        public static final String TIMEOUT_CONFIG_KEY = "timeout-millis";

        // 10 seconds
        private static final long DEFAULT_TIMEOUT_MILLIS = 10 * 1000;
        private final List<HealthCheck> allChecks = new LinkedList<>();
        private final List<HealthCheck> livenessChecks = new LinkedList<>();
        private final List<HealthCheck> readinessChecks = new LinkedList<>();
        private final List<HealthCheck> startupChecks = new LinkedList<>();

        private final Set<Class<?>> excludedClasses = new HashSet<>();
        private final Set<String> includedHealthChecks = new HashSet<>();
        private final Set<String> excludedHealthChecks = new HashSet<>();
        private boolean enabled = true;
        private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;

        private Builder() {
            super(Builder.class, DEFAULT_WEB_CONTEXT);
        }

        @Override
        public HealthSupport build() {
            return new HealthSupport(this);
        }

        /**
         * Add a health check to a white list (in case {@link #includeAll} is set to {@code false}.
         *
         * @param healthCheckName name of a health check to include
         * @return updated builder instance
         */
        public Builder addIncluded(String healthCheckName) {
            includedHealthChecks.add(healthCheckName);
            return this;
        }

        /**
         * Add health checks to a white list (in case {@link #includeAll} is set to {@code false}.
         *
         * @param names names of health checks to include
         * @return updated builder instance
         */
        @ConfiguredOption(key = INCLUDE_CONFIG_KEY)
        public Builder addIncluded(Collection<String> names) {
            if (null == names) {
                return this;
            }
            includedHealthChecks.addAll(names);
            return this;
        }

        /**
         * Add a health check to a black list.
         * Health check results that match by name with a blacklisted records will not be
         * part of the result.
         *
         * @param healthCheckName name of a health check to exclude
         * @return updated builder instance
         */
        public Builder addExcluded(String healthCheckName) {
            excludedHealthChecks.add(healthCheckName);
            return this;
        }

        /**
         * Add health checks to a black list.
         * Health check results that match by name with a blacklisted records will not be
         * part of the result.
         *
         * @param names names of health checks to exclude
         * @return updated builder instance
         */
        @ConfiguredOption(key = EXCLUDE_CONFIG_KEY)
        public Builder addExcluded(Collection<String> names) {
            if (null == names) {
                return this;
            }
            excludedHealthChecks.addAll(names);
            return this;
        }

        /**
         * Update this builder from configuration.
         *
         * @param config node located on this component's configuration
         * @return updated builder instance
         */
        public Builder config(Config config) {
            super.config(config);
            config.get(ENABLED_CONFIG_KEY).asBoolean().ifPresent(this::enabled);
            config.get(INCLUDE_CONFIG_KEY).asList(String.class).ifPresent(list -> list.forEach(this::addIncluded));
            config.get(EXCLUDE_CONFIG_KEY).asList(String.class).ifPresent(list -> list.forEach(this::addExcluded));
            config.get(EXCLUDE_CLASSES_CONFIG_KEY).asList(Class.class).ifPresent(list -> list.forEach(this::addExcludedClass));
            config.get(TIMEOUT_CONFIG_KEY).asLong().ifPresent(this::timeoutMillis);
            return this;
        }

        private void timeoutMillis(long aLong) {
            this.timeoutMillis = aLong;
        }

        /**
         * Configure overall timeout of health check call.
         *
         * @param timeout timeout value
         * @param unit timeout time unit
         * @return updated builder instance
         */
        @ConfiguredOption(key = TIMEOUT_CONFIG_KEY, description = "health endpoint timeout (ms)")
        public Builder timeout(long timeout, TimeUnit unit) {
            timeoutMillis(unit.toMillis(timeout));
            return this;
        }

        /**
         * A class may be excluded from invoking health checks on it.
         * This allows configurable approach to disabling broken health-checks.
         *
         * @param aClass class to ignore (any health check instance of this class will be ignored)
         * @return updated builder instance
         */
        @ConfiguredOption(key = EXCLUDE_CLASSES_CONFIG_KEY, kind = ConfiguredOption.Kind.LIST)
        public Builder addExcludedClass(Class<?> aClass) {
            this.excludedClasses.add(aClass);
            return this;
        }

        /**
         * Add liveness health check(s).
         *
         * @param healthChecks health check(s) to add
         * @return updated builder instance
         */
        public Builder addLiveness(HealthCheck... healthChecks) {
            return addLiveness(List.of(healthChecks));
        }

        /**
         * Add liveness health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         */
        public Builder addLiveness(Collection<HealthCheck> healthChecks) {
            this.allChecks.addAll(healthChecks);
            this.livenessChecks.addAll(healthChecks);

            return this;
        }

        /**
         * Add readiness health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         */
        public Builder addReadiness(HealthCheck... healthChecks) {
            return addReadiness(List.of(healthChecks));
        }

        /**
         * Add readiness health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         */
        public Builder addReadiness(Collection<HealthCheck> healthChecks) {
            this.allChecks.addAll(healthChecks);
            this.readinessChecks.addAll(healthChecks);

            return this;
        }

        /**
         * Add start-up health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         */
        public Builder addStartup(HealthCheck... healthChecks) {
            return addStartup(List.of(healthChecks));
        }

        /**
         * Add start-up health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         */
        public Builder addStartup(Collection<HealthCheck> healthChecks) {
            this.allChecks.addAll(healthChecks);
            this.startupChecks.addAll(healthChecks);
            return this;
        }

        /**
         * HealthSupport can be disabled by invoking this method.
         *
         * @param enabled whether to enable the health support (defaults to {@code true})
         * @return updated builder instance
         */
        @ConfiguredOption(key = ENABLED_CONFIG_KEY)
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
    }

    private static final class HcResponse {
        private final HealthCheckResponse hcr;
        private final boolean internalServerError;

        private HcResponse(HealthCheckResponse response, boolean internalServerError) {
            this.hcr = response;
            this.internalServerError = internalServerError;
        }

        private HcResponse(HealthCheckResponse response) {
            this(response, false);
        }

        String name() {
            return hcr.getName();
        }

        Status status() {
            return hcr.getStatus();
        }

        boolean internalError() {
            return internalServerError;
        }

        public Optional<Map<String, Object>> data() {
            return hcr.getData();
        }
    }

    static final class HealthResponse {
        private final Http.ResponseStatus status;
        private final JsonObject json;

        private HealthResponse(Http.ResponseStatus status, JsonObject json) {
            this.status = status;
            this.json = json;
        }

        Http.ResponseStatus status() {
            return status;
        }

        JsonObject json() {
            return json;
        }
    }
}
