/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(HealthMpServiceIT.HealthCheckOne.class)
@AddBean(HealthMpServiceIT.HealthCheckTwo.class)
@AddBean(HealthMpServiceIT.HealthCheckBad.class)
public class HealthMpServiceIT {

    @Inject
    private WebTarget webTarget;

    /**
     * Verify that the {@link HealthCheck} CDI beans (inner classes below)
     * annotated with {@link Health} are discovered and added to the json
     * returned from the {@code /health} endpoint.
     */
    @Test
    public void shouldAddHealthCheckBeans() {
        JsonObject json = getHealthJson();
        assertThat(healthCheckExists(json, "One"), is(true));
        assertThat(healthCheckExists(json, "Two"), is(true));
    }

    /**
     * Verify that the {@link HealthCheck} CDI bean (inner classes below)
     * NOT annotated with {@link Health} is not added to the json returned
     * from the {@code /health} endpoint.
     */
    @Test
    public void shouldNotAddHealthCheckBeanNotAnnotatedWithHealth() {
        JsonObject json = getHealthJson();
        assertThat(healthCheckExists(json, "Bad"), is(false));
    }

    /**
     * Verify that the {@link HealthCheckProvider}s (inner classes below)
     * are discovered by the service loader and their provided {@link HealthCheck}s
     * added to the json returned from the {@code /health} endpoint.
     */
    @Test
    public void shouldAddProvidedHealthChecks() {
        JsonObject json = getHealthJson();
        Assertions.assertAll(
                () -> assertThat("Three exists", healthCheckExists(json, "Three"), is(true)),
                () -> assertThat("Four exists", healthCheckExists(json, "Four"), is(true)),
                () -> assertThat("Five exists", healthCheckExists(json, "Five"), is(true)),
                () -> assertThat("Six exists", healthCheckExists(json, "Six"), is(true))
        );

    }

    private boolean healthCheckExists(JsonObject json, String name) {
        return json.getJsonArray("checks")
                   .stream()
                   .map(JsonObject.class::cast)
                   .anyMatch(check -> name.equals(check.getString("name")));
    }

    private JsonObject getHealthJson() {
        // request the application metrics in json format from the web server
        String health = webTarget
                .path("health")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get(String.class);

        JsonObject json = (JsonObject) Json.createReader(new StringReader(health)).read();
        assertThat(json, is(notNullValue()));
        assertThat(json.getJsonString("outcome"), is(notNullValue()));      // backward compatibility default
        return json;
    }

    /**
     * A test {@link HealthCheck} bean that should be discovered
     * by CDI and added to the health check endpoint.
     */
    @Readiness
    public static class HealthCheckOne
            implements HealthCheck {

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.builder().name("One").up().build();
        }
    }

    /**
     * A test {@link HealthCheck} bean that should be discovered
     * by CDI and added to the health check endpoint.
     */
    @Liveness
    public static class HealthCheckTwo
            implements HealthCheck {

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.builder().name("Two").up().build();
        }
    }

    /**
     * A test {@link HealthCheck} bean that should be NOT discovered
     * as it does not have the {@link Health} qualifier.
     */
    @ApplicationScoped
    public static class HealthCheckBad
            implements HealthCheck {

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.builder().name("Bad").up().build();
        }
    }

    /**
     * A test {@link HealthCheckProvider} bean that should be discovered
     * by the service loader and its provided {@link HealthCheck}s added
     * to the health check endpoint.
     */
    public static class HealthCheckProviderOne
            implements HealthCheckProvider {
        @Override
        public List<HealthCheck> livenessChecks() {
            return Arrays.asList(
                    new HealthCheckStub("Three"),
                    new HealthCheckStub("Four"));
        }
    }

    /**
     * A test {@link HealthCheckProvider} bean that should be discovered
     * by the service loader and its provided {@link HealthCheck}s added
     * to the health check endpoint.
     */
    public static class HealthCheckProviderTwo
            implements HealthCheckProvider {
        @Override
        public List<HealthCheck> livenessChecks() {
            return Arrays.asList(
                    new HealthCheckStub("Five"),
                    new HealthCheckStub("Six"));
        }
    }

    /**
     * A {@link HealthCheck} stub.
     */
    static class HealthCheckStub
            implements HealthCheck {

        private final String name;

        HealthCheckStub(String name) {
            this.name = name;
        }

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.builder().name(name).up().build();
        }
    }
}
