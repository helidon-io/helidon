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

import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.enterprise.inject.Instance;
import javax.enterprise.util.TypeLiteral;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HealthEndpointTest {
    private static Stream<List<HealthCheck>> goodChecks() {
        return Stream.of(
                singletonList(new GoodHealthCheck("good1")),
                asList(new GoodHealthCheck("good1"), new GoodHealthCheck("good2"))
        );
    }

    private static Stream<List<HealthCheck>> badChecks() {
        return Stream.of(
                singletonList(new BadHealthCheck("bad1")),
                asList(new GoodHealthCheck("good1"), new BadHealthCheck("bad1")),
                asList(new BadHealthCheck("bad1"), new GoodHealthCheck("good1")),
                asList(new BadHealthCheck("bad1"), new BadHealthCheck("bad2")));
    }

    private static Stream<List<HealthCheck>> brokenChecks() {
        return Stream.of(
                singletonList(new BrokenHealthCheck()),
                asList(new GoodHealthCheck("good1"), new BrokenHealthCheck()),
                asList(new BrokenHealthCheck(), new GoodHealthCheck("good1")),
                asList(new BadHealthCheck("bad1"), new BrokenHealthCheck()),
                asList(new BrokenHealthCheck(), new BadHealthCheck("bad1")),
                asList(new BadHealthCheck("bad1"), new GoodHealthCheck("good1"), new BrokenHealthCheck()),
                asList(new BadHealthCheck("bad1"), new BrokenHealthCheck(), new GoodHealthCheck("good1")),
                asList(new BrokenHealthCheck(), new BadHealthCheck("bad1"), new GoodHealthCheck("good1"))
        );
    }

    private static Stream<Arguments> includedAndExcludedHealthChecks() {
        HealthCheck hc1 = new GoodHealthCheck("hc1");
        HealthCheck hc2 = new GoodHealthCheck("hc2");
        HealthCheck hc3 = new GoodHealthCheck("hc3");

        return Stream.of(
                // Null "includes" and empty "excludes" means to include everything
                Arguments.of(null, emptyList(), asList(hc1, hc2, hc3), asList("hc1", "hc2", "hc3")),
                // Empty "includes" and null "excludes" means to include everything
                Arguments.of(emptyList(), null, asList(hc1, hc2, hc3), asList("hc1", "hc2", "hc3")),
                // Null "includes" and an exclusion list results in excluded checks
                Arguments.of(null, singletonList("hc2"), asList(hc1, hc2, hc3), asList("hc1", "hc3")),
                // Null "includes" and an exclusion list with extra bogus stuff results in excluded checks
                Arguments.of(null, asList("hc2", "bogus1"), asList(hc1, hc2, hc3), asList("hc1", "hc3")),
                // Includes effectively exclude other stuff
                Arguments.of(asList("hc1", "hc3"), null, asList(hc1, hc2, hc3), asList("hc1", "hc3")),
                // Includes and Excludes together work such that excludes do a final filtering
                Arguments.of(asList("hc1", "hc3", "bogus2"), asList("hc3", "bogus"), asList(hc1, hc2, hc3), singletonList("hc1"))
        );
    }

    @BeforeEach
    private void disableJDKLogging() {
        // Disable this logger so that we don't get a bunch of "SEVERE" logs in our test output, which are
        // a normal thing to happen according to some tests we run. Since this is a global setting in the JVM,
        // and we are good citizens, we restore it to the WARNING level after the test is done even though
        // technically this isn't necessary since none of our tests care about this output.
        Logger.getLogger("io.helidon.microprofile.health").setLevel(Level.OFF);
    }

    @AfterEach
    void enableJDKLogging() {
        Logger.getLogger("io.helidon.microprofile.health").setLevel(Level.WARNING);
    }

    @Test
    void nullHealthCheckSource() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new HealthEndpoint(null, null, null));
    }

    @ParameterizedTest
    @MethodSource(value = {"includedAndExcludedHealthChecks"})
    void testInclusionAndExclusion(List<String> includedHealthChecks,
                                   List<String> excludedHealthChecks,
                                   List<HealthCheck> allChecks,
                                   List<String> finalCheckNames) {
        final HealthEndpoint endpoint = new HealthEndpoint(new InstanceStub(allChecks),
                                                           includedHealthChecks,
                                                           excludedHealthChecks);
        final Response response = endpoint.getHealth();

        // Test the JSON
        final JsonObject json = getJson(response);
        final JsonArray checks = json.getJsonArray("checks");
        assertNotNull(checks);
        for (int i = 0; i < finalCheckNames.size(); i++) {
            assertThat(checks.getJsonObject(i).getString("name"), equalTo(finalCheckNames.get(i)));
        }
    }

    @Test
    void noHealthChecksResultsInSuccess() {
        final HealthEndpoint endpoint = new HealthEndpoint(new InstanceStub(emptyList()), null, null);
        final Response response = endpoint.getHealth();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Test the JSON
        final JsonObject json = getJson(response);
        assertThat(json.getString("outcome"), equalTo("UP"));
        assertThat(json.getJsonArray("checks"), notNullValue());
        assertThat(json.getJsonArray("checks").size(), equalTo(0));
    }

    @Test
    void checksAreSortedByName() {
        final HealthEndpoint endpoint = new HealthEndpoint(new InstanceStub(
                asList(
                        new GoodHealthCheck("g"),
                        new GoodHealthCheck("a"),
                        new GoodHealthCheck("v"))),
                                                           null, null);

        final Response response = endpoint.getHealth();

        // Test the JSON
        final JsonObject json = getJson(response);
        assertEquals("a", json.getJsonArray("checks").getJsonObject(0).getString("name"));
        assertEquals("g", json.getJsonArray("checks").getJsonObject(1).getString("name"));
        assertEquals("v", json.getJsonArray("checks").getJsonObject(2).getString("name"));
    }

    @ParameterizedTest
    @MethodSource(value = {"goodChecks"})
    void passingHealthChecksResultInSuccess(List<HealthCheck> goodChecks) {
        final HealthEndpoint endpoint = new HealthEndpoint(new InstanceStub(goodChecks), null, null);
        final Response response = endpoint.getHealth();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Test the JSON
        final JsonObject json = getJson(response);
        assertEquals("UP", json.getString("outcome"));
        assertNotNull(json.getJsonArray("checks"));
        assertEquals(goodChecks.size(), json.getJsonArray("checks").size());
    }

    @ParameterizedTest
    @MethodSource(value = {"badChecks"})
    void failingHealthChecksResultInFailure(List<HealthCheck> badChecks) {
        final HealthEndpoint endpoint = new HealthEndpoint(new InstanceStub(badChecks), null, null);
        final Response response = endpoint.getHealth();
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());

        // Test the JSON
        final JsonObject json = getJson(response);
        assertEquals("DOWN", json.getString("outcome"));
        assertNotNull(json.getJsonArray("checks"));
        assertEquals(badChecks.size(), json.getJsonArray("checks").size());
    }

    @ParameterizedTest
    @MethodSource(value = {"brokenChecks"})
    void brokenHealthChecksResultInFailure(List<HealthCheck> brokenChecks) {
        final HealthEndpoint endpoint = new HealthEndpoint(new InstanceStub(brokenChecks), null, null);
        final Response response = endpoint.getHealth();
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());

        // Test the JSON
        final JsonObject json = getJson(response);
        assertEquals("DOWN", json.getString("outcome"));
        assertNotNull(json.getJsonArray("checks"));
        assertEquals(brokenChecks.size(), json.getJsonArray("checks").size());
    }

    private JsonObject getJson(Response response) {
        final String jsonAsString = response.getEntity().toString();
        final JsonReader reader = Json.createReader(new StringReader(jsonAsString));
        return reader.readObject();
    }

    private static final class GoodHealthCheck implements HealthCheck {
        private String name;

        private GoodHealthCheck(String name) {
            this.name = name;
        }

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named(name).up().build();
        }
    }

    private static final class BadHealthCheck implements HealthCheck {
        private String name;

        private BadHealthCheck(String name) {
            this.name = name;
        }

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named(name).down().build();
        }
    }

    private static final class BrokenHealthCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            throw new RuntimeException("Mimicking some kind of bad thing");
        }
    }

    private static final class InstanceStub implements Instance<HealthCheck> {
        private final List<HealthCheck> checks;

        private InstanceStub(List<HealthCheck> checks) {
            this.checks = checks;
        }

        @Override
        public Instance<HealthCheck> select(Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAmbiguous() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroy(HealthCheck instance) {
            throw new UnsupportedOperationException();

        }

        @Override
        public <U extends HealthCheck> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends HealthCheck> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<HealthCheck> iterator() {
            return checks.iterator();
        }

        @Override
        public HealthCheck get() {
            throw new UnsupportedOperationException();
        }
    }
}
