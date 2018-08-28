/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

/**
 * A REST endpoint that implements the microprofile health specification.
 *
 * See https://github.com/eclipse/microprofile-health/blob/master/spec/src/main/asciidoc/protocol-wireformat.adoc
 */
@Path("/")
@RequestScoped
public class HealthEndpoint {
    /**
     * The LOGGER. The name given to this logger is the same as the name of the project.
     */
    private static final Logger LOGGER = Logger.getLogger("io.helidon.microprofile.health"); // or io.helidon.mp.health?

    /**
     * The list of health checks.
     */
    private final List<HealthCheck> healthChecks;

    private final boolean includeAll;
    private final List<String> includedHealthChecks;
    private final List<String> excludedHealthChecks;

    /**
     * Creates a new HealthEndpoint. The supplied healthCheckSource is used to get the list of HealthCheck objects.
     * This list is dynamically constructed by the CDI engine based on which HealthCheck classes are annotated with
     * "Health" and "ApplicationScoped" annotations.
     *
     * @param healthCheckSource    The health checks as discovered by CDI
     * @param includedHealthChecks The list of health checks that should be included. Anything not in this list is
     *                             automatically excluded. Can have splat ('*')
     * @param excludedHealthChecks The list of health checks to exclude.
     */
    @SuppressWarnings("WeakerAccess")
    @Inject
    public HealthEndpoint(
            @Health Instance<HealthCheck> healthCheckSource,
            @ConfigProperty(name = "helidon.health.include", defaultValue = "*") List<String> includedHealthChecks,
            @ConfigProperty(name = "helidon.health.exclude", defaultValue = "abcd") List<String> excludedHealthChecks) {
        final List<HealthCheck> tmp = new ArrayList<>();
        healthCheckSource.forEach(tmp::add);
        healthChecks = Collections.unmodifiableList(tmp);

        // Since this class is RequestScoped, a new HealthEndpoint class should be created
        // for each request, which would mean that whenever the configuration changes, we
        // should get updated configuration automatically. TODO This assumption needs to be tested
        this.includeAll = includedHealthChecks == null
                || includedHealthChecks.isEmpty()
                || includedHealthChecks.contains("*");

        this.includedHealthChecks = includedHealthChecks == null
                ? Collections.emptyList()
                : new ArrayList<>(includedHealthChecks);

        this.excludedHealthChecks = excludedHealthChecks == null
                ? Collections.emptyList()
                : new ArrayList<>(excludedHealthChecks);
    }

    /**
     * Gets an HTTP Response in the form of JSON to a request for health.
     *
     * @return A non-null HTTP Response. May be 200, 500, or 503.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHealth() {
        // Indicates whether the health check process completed or we ran into an Exception partway through.
        final AtomicBoolean incomplete = new AtomicBoolean();

        // Converts the list of HealthChecks into a list of HealthCheckResponses.
        final List<HealthCheckResponse> responses = healthChecks.stream().map(hc -> {
            // Map from HealthCheck to HealthCheckResponse. If something horrible happens
            // as a results of the call, then we will create a Response that is DOWN and
            // that indicates there was an error. This allows some information to be sent
            // in the final HTTP REST call so the caller knows exactly which health checks
            // failed with errors.
            try {
                return hc.call();
            } catch (Exception e) {
                incomplete.set(true);
                LOGGER.log(Level.SEVERE, "Failed to compute health check for " + hc.getClass().getName(), e);
                return HealthCheckResponse
                        .named(hc.getClass().getName())
                        .withData("message", "Failed to compute health. Check logs.")
                        .down()
                        .build();
            }
        }).filter(response -> includeAll || includedHealthChecks.contains(response.getName()))
                .filter(response -> !excludedHealthChecks.contains(response.getName()))
                .sorted(Comparator.comparing(HealthCheckResponse::getName)).collect(Collectors.toList());

        // Determines the final outcome as the logical conjunction. An empty response list equals success.
        final HealthCheckResponse.State outcome = responses.stream()
                .anyMatch(response -> response.getState() == HealthCheckResponse.State.DOWN)
                ? HealthCheckResponse.State.DOWN
                : HealthCheckResponse.State.UP;

        // Convert to Json.
        final JsonObject result = toJson(outcome, responses);

        // If incomplete then some internal server error occurred while processing one or more of the checks.
        if (incomplete.get()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build();
        }

        // Return the appropriate HTTP status and response based on the result.
        switch (outcome) {
        case UP:
            return Response.status(Response.Status.OK).entity(result).build();
        case DOWN:
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(result).build();
        default:
            LOGGER.severe("Unexpected outcome: " + outcome);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build();
        }
    }

    /**
     * Converts an outcome and list of responses into a properly formed JSON document.
     *
     * @param outcome   The outcome. Cannot be null.
     * @param responses The list of responses. Cannot be null, but can be empty.
     * @return A properly formed JSON document as a String.
     */
    private JsonObject toJson(final HealthCheckResponse.State outcome, final List<HealthCheckResponse> responses) {
        if (outcome == null || responses == null) {
            throw new NullPointerException();
        }

        final JsonObjectBuilder jsonBuilder = Json.createObjectBuilder()
                .add("outcome", outcome.toString());

        final JsonArrayBuilder checkArrayBuilder = Json.createArrayBuilder();

        if (!responses.isEmpty()) {
            for (HealthCheckResponse r : responses) {
                final JsonObjectBuilder checkBuilder = Json.createObjectBuilder();
                checkBuilder.add("name", r.getName());
                checkBuilder.add("state", r.getState().toString());
                Optional<Map<String, Object>> data = r.getData();
                data.ifPresent(m -> checkBuilder.add("data", Json.createObjectBuilder(m)));

                // Have to add this after the checkBuilder is populated
                checkArrayBuilder.add(checkBuilder);
            }
        }

        // Have to add this after the checkArrayBuilder is populated
        jsonBuilder.add("checks", checkArrayBuilder);

        return jsonBuilder.build();
    }
}
