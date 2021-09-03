/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.faulttolerance.Async;
import io.helidon.faulttolerance.Timeout;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.cors.CrossOriginConfig;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.State;

import static io.helidon.webserver.cors.CorsEnabledServiceHelper.CORS_CONFIG_KEY;

/**
 * Health check support for integration with webserver, to expose the health endpoint.
 */
public final class HealthSupport implements Service {
    /**
     * Default web context root of the Health check endpoint.
     */
    public static final String DEFAULT_WEB_CONTEXT = "/health";

    private static final String SERVICE_NAME = "Health";

    private static final Logger LOGGER = Logger.getLogger(HealthSupport.class.getName());

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final boolean enabled;
    private final String webContext;
    private final List<HealthCheck> allChecks = new LinkedList<>();
    private final List<HealthCheck> livenessChecks = new LinkedList<>();
    private final List<HealthCheck> readinessChecks = new LinkedList<>();
    private final boolean includeAll;
    private final Set<String> includedHealthChecks;
    private final Set<String> excludedHealthChecks;
    private final boolean backwardCompatible;
    private final CorsEnabledServiceHelper corsEnabledServiceHelper;
    private final MessageBodyWriter<JsonStructure> jsonpWriter = JsonpSupport.writer();
    private final Timeout timeout;
    private final Async async;

    private HealthSupport(Builder builder) {
        this.enabled = builder.enabled;
        this.webContext = builder.webContext;
        this.backwardCompatible = builder.backwardCompatible;
        corsEnabledServiceHelper = CorsEnabledServiceHelper.create(SERVICE_NAME, builder.crossOriginConfig);

        if (enabled) {
            builder.allChecks
                    .stream()
                    .filter(health -> !builder.excludedClasses.contains(health.getClass()))
                    .forEach(allChecks::add);

            builder.readinessChecks
                    .stream()
                    .filter(health -> !builder.excludedClasses.contains(health.getClass()))
                    .forEach(readinessChecks::add);

            builder.livenessChecks
                    .stream()
                    .filter(health -> !builder.excludedClasses.contains(health.getClass()))
                    .forEach(livenessChecks::add);

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
        if (!enabled) {
            // do not register anything if health check is disabled
            return;
        }
        rules.any(webContext, corsEnabledServiceHelper.processor())
                .get(webContext, this::callAll)
                .get(webContext + "/live", this::callLiveness)
                .get(webContext + "/ready", this::callReadiness);
    }

    private void callAll(ServerRequest req, ServerResponse res) {
        invoke(res, allChecks);
    }

    private void callLiveness(ServerRequest req, ServerResponse res) {
        invoke(res, livenessChecks);
    }

    private void callReadiness(ServerRequest req, ServerResponse res) {
        invoke(res, readinessChecks);
    }

    void invoke(ServerResponse res, List<HealthCheck> healthChecks) {
        // timeout on the asynchronous execution
        Single<HealthResponse> result = timeout.invoke(() -> async.invoke(() -> callHealthChecks(healthChecks)));

        // handle timeouts and failures in execution
        result = result.onErrorResume(throwable -> {
            LOGGER.log(Level.SEVERE, "Failed to call health checks", throwable);
            HcResponse response = new HcResponse(HealthCheckResponse.down("InternalError"), true);
            return new HealthResponse(Http.Status.INTERNAL_SERVER_ERROR_500, toJson(State.DOWN, List.of(response)));
        });

        result.thenAccept(hres -> {
            res.status(hres.status());
            res.send(jsonpWriter.marshall(hres.json));
        });

    }

    HealthResponse callHealthChecks(List<HealthCheck> healthChecks) {
        List<HcResponse> responses = healthChecks.stream()
                .map(this::callHealthChecks)
                .filter(this::notExcluded)
                .filter(this::allOrIncluded)
                .sorted(Comparator.comparing(HcResponse::name))
                .collect(Collectors.toList());

        State state = responses.stream()
                .map(HcResponse::state)
                .filter(State.DOWN::equals)
                .findFirst()
                .orElse(State.UP);

        Http.ResponseStatus status = responses.stream()
                .filter(HcResponse::internalError)
                .findFirst()
                .map(it -> Http.Status.INTERNAL_SERVER_ERROR_500)
                .orElse((state == State.UP) ? Http.Status.OK_200 : Http.Status.SERVICE_UNAVAILABLE_503);

        JsonObject json = toJson(state, responses);
        return new HealthResponse(status, json);
    }

    private JsonObject toJson(State state, List<HcResponse> responses) {
        final JsonObjectBuilder jsonBuilder = JSON.createObjectBuilder();
        if (backwardCompatible) {
            jsonBuilder.add("outcome", state.toString());
        }
        jsonBuilder.add("status", state.toString());

        final JsonArrayBuilder checkArrayBuilder = JSON.createArrayBuilder();

        for (HcResponse r : responses) {
            JsonObjectBuilder checkBuilder = JSON.createObjectBuilder();
            checkBuilder.add("name", r.name());
            checkBuilder.add("state", r.state().toString());
            checkBuilder.add("status", r.state().toString());
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
    public static final class Builder implements io.helidon.common.Builder<HealthSupport> {
        // 10 seconds
        private static final long DEFAULT_TIMEOUT_MILLIS = 10 * 1000;
        private final List<HealthCheck> allChecks = new LinkedList<>();
        private final List<HealthCheck> livenessChecks = new LinkedList<>();
        private final List<HealthCheck> readinessChecks = new LinkedList<>();

        private final Set<Class<?>> excludedClasses = new HashSet<>();
        private final Set<String> includedHealthChecks = new HashSet<>();
        private final Set<String> excludedHealthChecks = new HashSet<>();
        private String webContext = DEFAULT_WEB_CONTEXT;
        private boolean enabled = true;
        private boolean backwardCompatible = true;
        private CrossOriginConfig crossOriginConfig;
        private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;

        private Builder() {
        }

        @Override
        public HealthSupport build() {
            return new HealthSupport(this);
        }

        /**
         * Path under which to register health check endpoint on the web server.
         *
         * @param path webContext to use, defaults to
         * @return updated builder instance
         */
        public Builder webContext(String path) {
            if (path.startsWith("/")) {
                this.webContext = path;
            } else {
                this.webContext = "/" + path;
            }
            return this;
        }

        /**
         * Add a health check (or healthchecks) to the list.
         * All health checks would get invoked when this endpoint is called (even when
         * the result is excluded).
         *
         * @param healthChecks health check(s) to add
         * @return updated builder instance
         * @deprecated use {@link #addReadiness(org.eclipse.microprofile.health.HealthCheck...)} or
         *  {@link #addLiveness(org.eclipse.microprofile.health.HealthCheck...)} instead.
         *  This method is needed until the microprofile specification removes support for generic HealthChecks (which are
         *      already deprecated).
         */
        @Deprecated
        public Builder add(HealthCheck... healthChecks) {
            this.allChecks.addAll(Arrays.asList(healthChecks));
            return this;
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
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            config.get("web-context").asString().ifPresent(this::webContext);
            config.get("include").asList(String.class).ifPresent(list -> list.forEach(this::addIncluded));
            config.get("exclude").asList(String.class).ifPresent(list -> list.forEach(this::addExcluded));
            config.get("exclude-classes").asList(Class.class).ifPresent(list -> list.forEach(this::addExcludedClass));
            config.get("backward-compatible").asBoolean().ifPresent(this::backwardCompatible);
            config.get("timeout-millis").asLong().ifPresent(this::timeoutMillis);
            config.get(CORS_CONFIG_KEY)
                    .as(CrossOriginConfig::create)
                    .ifPresent(this::crossOriginConfig);
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
         * HealthSupport can be disabled by invoking this method.
         *
         * @param enabled whether to enable the health support (defaults to {@code true})
         * @return updated builder instance
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Backward compatibility flag to produce Health 1.X compatible JSON output
         * (including "outcome" property).
         *
         * @param enabled whether to enable backward compatible mode (defaults to {@code true})
         * @return updated builder instance
         */
        public Builder backwardCompatible(boolean enabled) {
            this.backwardCompatible = enabled;
            return this;
        }

        /**
         * Set the CORS config from the specified {@code CrossOriginConfig} object.
         *
         * @param crossOriginConfig {@code CrossOriginConfig} containing CORS set-up
         * @return updated builder instance
         */
        public Builder crossOriginConfig(CrossOriginConfig crossOriginConfig) {
            Objects.requireNonNull(crossOriginConfig, "CrossOriginConfig must be non-null");
            this.crossOriginConfig = crossOriginConfig;
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

        State state() {
            return hcr.getState();
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
