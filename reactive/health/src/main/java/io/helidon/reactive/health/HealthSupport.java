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

package io.helidon.reactive.health;

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
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.reactive.faulttolerance.Async;
import io.helidon.reactive.faulttolerance.Timeout;
import io.helidon.reactive.media.common.MessageBodyWriter;
import io.helidon.reactive.media.jsonp.JsonpSupport;
import io.helidon.reactive.servicecommon.HelidonRestServiceSupport;
import io.helidon.reactive.webserver.Routing.Rules;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;
import org.eclipse.microprofile.health.HealthCheck;

import static io.helidon.health.HealthCheckResponse.Status.DOWN;
import static io.helidon.health.HealthCheckResponse.Status.ERROR;
import static io.helidon.health.HealthCheckResponse.Status.UP;

/**
 * Health check support for integration with webserver, to expose the health endpoint.
 * The format is aligned with MicroProfile Health (if {@link Builder#details(boolean)} is set to {@code true}).
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
    private final boolean details;
    private final List<io.helidon.health.HealthCheck> allChecks = new LinkedList<>();
    private final List<io.helidon.health.HealthCheck> livenessChecks = new LinkedList<>();
    private final List<io.helidon.health.HealthCheck> readinessChecks = new LinkedList<>();
    private final List<io.helidon.health.HealthCheck> startupChecks = new LinkedList<>();
    private final boolean includeAll;
    private final Set<String> includedHealthChecks;
    private final Set<String> excludedHealthChecks;
    private final MessageBodyWriter<JsonStructure> jsonpWriter = JsonpSupport.writer();
    private final LazyValue<? extends Timeout> timeout;
    private final LazyValue<? extends Async> async;

    private HealthSupport(Builder builder) {
        super(LOGGER, builder, SERVICE_NAME);
        this.details = builder.details;
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

        // Lazy values to prevent early init of maybe-not-yet-configured FT thread pools
        this.timeout = LazyValue.create(() -> Timeout.create(builder.timeout));
        this.async = LazyValue.create(Async::create);
    }

    @Override
    public void update(Rules rules) {
        configureEndpoint(rules, rules);
    }

    @Override
    protected void postConfigureEndpoint(Rules defaultRules, Rules serviceEndpointRoutingRules) {
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

    private static void collectNonexcludedChecks(Builder builder,
                                                 List<ReactiveHealthCheck> checks,
                                                 Consumer<io.helidon.health.HealthCheck> adder) {
        checks.stream()
               .filter(health -> !builder.excludedClasses.contains(health.checkClass()))
               .forEach(it -> adder.accept(it));
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

    private void get(ServerResponse res, Collection<io.helidon.health.HealthCheck> healthChecks) {
        invoke(res, healthChecks, details);
    }

    private void head(ServerResponse res, Collection<io.helidon.health.HealthCheck> healthChecks) {
        invoke(res, healthChecks, false);
    }

    void invoke(ServerResponse res, Collection<io.helidon.health.HealthCheck> healthChecks, boolean sendDetails) {
        // timeout on the asynchronous execution
        Single<HealthHttpResponse> result = timeout.get().invoke(
                () -> async.get().invoke(() -> callHealthChecks(healthChecks)));

        // handle timeouts and failures in execution
        result = result.onErrorResume(throwable -> {
            LOGGER.log(Level.SEVERE, "Failed to call health checks", throwable);
            HcResponse response = new HcResponse("health",
                                                 HealthCheckResponse.builder().status(ERROR).build(),
                                                 true);
            return new HealthHttpResponse(Http.Status.INTERNAL_SERVER_ERROR_500,
                                          toJson(DOWN, List.of(response)));
        });

        result.thenAccept(hres -> {
            int status = hres.status().code();
            if (status == Http.Status.OK_200.code() && !sendDetails) {
                status = Http.Status.NO_CONTENT_204.code();
            }
            res.cachingStrategy(ServerResponse.CachingStrategy.NO_CACHING)
                    .status(status);

            if (sendDetails) {
                res.send(jsonpWriter.marshall(hres.json));
            } else {
                res.send();
            }
        });
    }

    HealthHttpResponse callHealthChecks(Collection<io.helidon.health.HealthCheck> healthChecks) {
        List<HcResponse> responses = healthChecks.stream()
                .filter(this::notExcluded)
                .filter(this::allOrIncluded)
                .map(this::callHealthChecks)
                .sorted(Comparator.comparing(HcResponse::name))
                .collect(Collectors.toList());

        HealthCheckResponse.Status status = responses.stream()
                .map(HcResponse::status)
                .filter(Predicate.not(UP::equals))
                .findFirst()
                .orElse(UP);
        if (status != UP) {
            // there is no support for ERROR in MP Health, and we want to be backward compatible
            status = DOWN;
        }

        Http.Status httpStatus = responses.stream()
                .filter(HcResponse::internalError)
                .findFirst()
                .map(it -> Http.Status.INTERNAL_SERVER_ERROR_500)
                .orElse((status == HealthCheckResponse.Status.UP) ? Http.Status.OK_200 : Http.Status.SERVICE_UNAVAILABLE_503);

        JsonObject json = toJson(status, responses);
        return new HealthHttpResponse(httpStatus, json);
    }

    private JsonObject toJson(HealthCheckResponse.Status status, List<HcResponse> responses) {
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

    private boolean allOrIncluded(io.helidon.health.HealthCheck response) {
        return includeAll || includedHealthChecks.contains(response.name());
    }

    private boolean notExcluded(io.helidon.health.HealthCheck response) {
        return !excludedHealthChecks.contains(response.name());
    }

    private HcResponse callHealthChecks(io.helidon.health.HealthCheck hc) {
        try {
            return new HcResponse(hc.name(), hc.call());
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to compute health check for " + hc.getClass().getName(), e);

            return new HcResponse(hc.name(),
                                  HealthCheckResponse.builder()
                                          .detail("message", "Failed to compute health. Error logged")
                                          .status(ERROR)
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
     * Fluent API builder for {@link HealthSupport}.
     */
    @Configured(prefix = Builder.HEALTH_CONFIG_KEY, root = true)
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
        private final List<ReactiveHealthCheck> allChecks = new LinkedList<>();
        private final List<ReactiveHealthCheck> livenessChecks = new LinkedList<>();
        private final List<ReactiveHealthCheck> readinessChecks = new LinkedList<>();
        private final List<ReactiveHealthCheck> startupChecks = new LinkedList<>();

        private final Set<Class<?>> excludedClasses = new HashSet<>();
        private final Set<String> includedHealthChecks = new HashSet<>();
        private final Set<String> excludedHealthChecks = new HashSet<>();
        private boolean details = true;
        private boolean enabled = true;
        private Duration timeout = Duration.ofMillis(DEFAULT_TIMEOUT_MILLIS);

        private Builder() {
            super(DEFAULT_WEB_CONTEXT);
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
        @ConfiguredOption(key = INCLUDE_CONFIG_KEY, type = String.class, kind = ConfiguredOption.Kind.LIST)
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
        @ConfiguredOption(key = EXCLUDE_CONFIG_KEY, type = String.class, kind = ConfiguredOption.Kind.LIST)
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
            config.get("details").asBoolean().ifPresent(this::details);
            config.get(INCLUDE_CONFIG_KEY).asList(String.class).ifPresent(list -> list.forEach(this::addIncluded));
            config.get(EXCLUDE_CONFIG_KEY).asList(String.class).ifPresent(list -> list.forEach(this::addExcluded));
            config.get(EXCLUDE_CLASSES_CONFIG_KEY).asList(Class.class).ifPresent(list -> list.forEach(this::addExcludedClass));
            config.get(TIMEOUT_CONFIG_KEY).asLong().ifPresent(this::timeoutMillis);
            return this;
        }

        private void timeoutMillis(long aLong) {
            this.timeout = Duration.ofMillis(aLong);
        }

        /**
         * Configure overall timeout of health check call.
         *
         * @param timeout timeout value
         * @param unit timeout time unit
         * @return updated builder instance
         * @deprecated use {@link #timeout(Duration)} instead
         */
        @ConfiguredOption(key = TIMEOUT_CONFIG_KEY,
                          description = "health endpoint timeout (ms)",
                          type = Long.class,
                          value = "10000")
        @Deprecated(since = "4.0.0")
        public Builder timeout(long timeout, TimeUnit unit) {
            timeoutMillis(unit.toMillis(timeout));
            return this;
        }

        /**
         * Configure overall timeout of health check call.
         *
         * @param timeout timeout value
         * @return updated builder instance
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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
         * @deprecated use {@link #add(io.helidon.health.HealthCheck...)} instead, as we will remove dependency on
         * MP health checks in the future
         */
        @Deprecated(since = "4.0.0", forRemoval = true)
        public Builder addLiveness(HealthCheck... healthChecks) {
            return addLiveness(List.of(healthChecks));
        }

        /**
         * Add liveness health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         * @deprecated use {@link #add(java.util.Collection)} instead, as we will remove dependency on
         * MP health checks in the future
         */
        @Deprecated(since = "4.0.0", forRemoval = true)
        public Builder addLiveness(Collection<HealthCheck> healthChecks) {
            for (HealthCheck healthCheck : healthChecks) {
                add(MpCheckWrapper.create(HealthCheckType.LIVENESS, healthCheck));
            }
            return this;
        }

        /**
         * Add readiness health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         * @deprecated use {@link #add(io.helidon.health.HealthCheck...)} instead, as we will remove dependency on
         * MP health checks in the future
         */
        @Deprecated(since = "4.0.0", forRemoval = true)
        public Builder addReadiness(HealthCheck... healthChecks) {
            return addReadiness(List.of(healthChecks));
        }

        /**
         * Add readiness health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         * @deprecated use {@link #add(java.util.Collection)} instead, as we will remove dependency on
         * MP health checks in the future
         */
        @Deprecated(since = "4.0.0", forRemoval = true)
        public Builder addReadiness(Collection<HealthCheck> healthChecks) {
            for (HealthCheck healthCheck : healthChecks) {
                add(MpCheckWrapper.create(HealthCheckType.READINESS, healthCheck));
            }
            return this;
        }

        /**
         * Add start-up health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         * @deprecated use {@link #add(io.helidon.health.HealthCheck...)} instead, as we will remove dependency on
         * MP health checks in the future
         */
        @Deprecated(since = "4.0.0", forRemoval = true)
        public Builder addStartup(HealthCheck... healthChecks) {
            return addStartup(List.of(healthChecks));
        }

        /**
         * Add start-up health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         * @deprecated use {@link #add(java.util.Collection)} instead, as we will remove dependency on
         * MP health checks in the future
         */
        @Deprecated(since = "4.0.0", forRemoval = true)
        public Builder addStartup(Collection<HealthCheck> healthChecks) {
            for (HealthCheck healthCheck : healthChecks) {
                add(MpCheckWrapper.create(HealthCheckType.STARTUP, healthCheck));
            }
            return this;
        }

        /**
         * Add health check(s).
         *
         * @param healthCheck health check to add
         * @return updated builder instance
         */
        public Builder add(io.helidon.health.HealthCheck healthCheck) {
            return add(List.of(healthCheck));
        }

        /**
         * Add health check.
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         */
        public Builder add(io.helidon.health.HealthCheck... healthChecks) {
            return add(List.of(healthChecks));
        }

        /**
         * Add health check(s).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         */
        public Builder add(Collection<io.helidon.health.HealthCheck> healthChecks) {
            for (io.helidon.health.HealthCheck healthCheck : healthChecks) {
                HelidonCheckWrapper wrapper = new HelidonCheckWrapper(healthCheck);
                this.allChecks.add(wrapper);
                switch (wrapper.type()) {
                case READINESS -> this.readinessChecks.add(wrapper);
                case LIVENESS -> this.livenessChecks.add(wrapper);
                case STARTUP -> this.startupChecks.add(wrapper);
                default -> throw new IllegalStateException("Unsupported health check type: " + wrapper.type());
                }
            }
            return this;
        }

        /**
         * HealthSupport can be disabled by invoking this method.
         *
         * @param enabled whether to enable the health support (defaults to {@code true})
         * @return updated builder instance
         */
        @ConfiguredOption(key = ENABLED_CONFIG_KEY, value = "true")
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Whether details should be printed.
         * Set to {@code true} by default (to be backward compatible).
         *
         * Without details, returns a {@link io.helidon.common.http.Http.Status#NO_CONTENT_204} for success,
         * {@link io.helidon.common.http.Http.Status#SERVICE_UNAVAILABLE_503} for health down,
         * and {@link io.helidon.common.http.Http.Status#INTERNAL_SERVER_ERROR_500} in case of error with no entity.
         * When details are enabled, health returns {@link io.helidon.common.http.Http.Status#OK_200} for success,
         * same codes otherwise
         * and a JSON entity with detailed information about each health check executed.
         *
         * @param details set to {@code true} to enable details
         * @return updated builder
         */
        @ConfiguredOption("true")
        public Builder details(boolean details) {
            this.details = details;
            return this;
        }
    }

    private static final class HcResponse {
        private final String name;
        private final HealthCheckResponse hcr;
        private final boolean internalServerError;

        private HcResponse(String name, HealthCheckResponse response, boolean internalServerError) {
            this.name = name;
            this.hcr = response;
            this.internalServerError = internalServerError;
        }

        private HcResponse(String name, HealthCheckResponse response) {
            this(name, response, false);
        }

        String name() {
            return name;
        }

        HealthCheckResponse.Status status() {
            return hcr.status();
        }

        boolean internalError() {
            return internalServerError;
        }

        public Optional<Map<String, Object>> data() {
            Map<String, Object> details = hcr.details();
            if (details.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(details);
        }
    }

    static final class HealthHttpResponse {
        private final Http.Status status;
        private final JsonObject json;

        private HealthHttpResponse(Http.Status status, JsonObject json) {
            this.status = status;
            this.json = json;
        }

        Http.Status status() {
            return status;
        }

        JsonObject json() {
            return json;
        }
    }
}
