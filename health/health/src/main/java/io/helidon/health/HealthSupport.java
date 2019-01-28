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
package io.helidon.health;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.State;

/**
 * Health check support for integration with webserver, to expose the health endpoint.
 */
public final class HealthSupport implements Service {
    /**
     * Default web context root of the Health check endpoint.
     */
    public static final String DEFAULT_WEB_CONTEXT = "/health";

    private static final Logger LOGGER = Logger.getLogger(HealthSupport.class.getName());

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final String webContext;
    private final List<HealthCheck> healthChecks;
    private final boolean includeAll;
    private final Set<String> includedHealthChecks;
    private final Set<String> excludedHealthChecks;

    private HealthSupport(Builder builder) {
        this.webContext = builder.webContext;
        this.healthChecks = new LinkedList<>(builder.healthChecks);
        this.includedHealthChecks = new HashSet<>(builder.includedHealthChecks);
        this.excludedHealthChecks = new HashSet<>(builder.excludedHealthChecks);

        includeAll = includedHealthChecks.isEmpty();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get(webContext, JsonSupport.create())
                .get(webContext, (req, res) -> {
                    HealthResponse hres = callHealthChecks();

                    res.status(hres.status());
                    res.send(hres.json);
                });
    }

    HealthResponse callHealthChecks() {
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
        final JsonObjectBuilder jsonBuilder = JSON.createObjectBuilder()
                .add("outcome", state.toString());

        final JsonArrayBuilder checkArrayBuilder = JSON.createArrayBuilder();

        for (HcResponse r : responses) {
            JsonObjectBuilder checkBuilder = JSON.createObjectBuilder();
            checkBuilder.add("name", r.name());
            checkBuilder.add("state", r.state().toString());
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
        } catch (Exception e) {
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
    public static HealthSupport create(){
        return builder().build();
    }

    /**
     * Create a new HealthSupport with no health checks, configured from provided config.
     * The endpoint will always return {@code UP}.
     *
     * @param config configuration of this health check, used only to get {@code web-context} property to configure
     *      {@link Builder#webContext(String)}
     * @return health support configured with no health checks
     */
    public static HealthSupport create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Fluent API builder for {@link io.helidon.health.HealthSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<HealthSupport> {
        private final List<HealthCheck> healthChecks = new LinkedList<>();
        private final Set<String> includedHealthChecks = new HashSet<>();
        private final Set<String> excludedHealthChecks = new HashSet<>();
        private String webContext = DEFAULT_WEB_CONTEXT;

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
            this.webContext = path;
            return this;
        }

        /**
         * Add a health check (or healthchecks) to the list.
         * All health checks would get invoked when this endpoint is called (even when
         * the result is excluded).
         *
         * @param healthChecks health check(s) to add
         * @return updated builder instance
         */
        public Builder add(HealthCheck... healthChecks) {
            this.healthChecks.addAll(Arrays.asList(healthChecks));
            return this;
        }

        /**
         * Add health checks to the list.
         * All health checks would get invoked when this endpoint is called (even when
         * the result is excluded).
         *
         * @param healthChecks health checks to add
         * @return updated builder instance
         */
        public Builder add(Collection<HealthCheck> healthChecks) {
            this.healthChecks.addAll(healthChecks);
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
            config.get("web-context").asString().ifPresent(this::webContext);
            config.get("include").asList(String.class).ifPresent(list -> {
                list.forEach(this::addIncluded);
            });
            config.get("exclude").asList(String.class).ifPresent(list -> {
                list.forEach(this::addExcluded);
            });
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
        private Http.ResponseStatus status;
        private JsonObject json;

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
