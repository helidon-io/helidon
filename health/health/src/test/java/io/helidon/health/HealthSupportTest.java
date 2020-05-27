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

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.json.JsonArray;
import javax.json.JsonObject;

import io.helidon.common.http.Http;

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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

class HealthSupportTest {
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
    void disableJDKLogging() {
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

    @ParameterizedTest
    @MethodSource(value = {"includedAndExcludedHealthChecks"})
    void testInclusionAndExclusion(List<String> includedHealthChecks,
                                   List<String> excludedHealthChecks,
                                   List<HealthCheck> allChecks,
                                   List<String> finalCheckNames) {
        HealthSupport support = HealthSupport.builder()
                .addLiveness(allChecks)
                .addExcluded(excludedHealthChecks)
                .addIncluded(includedHealthChecks)
                .build();

        HealthSupport.HealthResponse response = support.callHealthChecks(allChecks);

        // Test the JSON
        final JsonObject json = response.json();
        final JsonArray checks = json.getJsonArray("checks");
        assertThat(checks, notNullValue());
        for (int i = 0; i < finalCheckNames.size(); i++) {
            assertThat(checks.getJsonObject(i).getString("name"), equalTo(finalCheckNames.get(i)));
        }
    }

    @Test
    void noHealthChecksResultsInSuccess() {
        HealthSupport support = HealthSupport.builder()
                .build();

        HealthSupport.HealthResponse response = support.callHealthChecks(Collections.emptyList());

        assertThat(response.status(), is(Http.Status.OK_200));

        // Test the JSON
        final JsonObject json = response.json();
        assertThat(json.getString("outcome"), equalTo("UP"));
        assertThat(json.getJsonArray("checks"), notNullValue());
        assertThat(json.getJsonArray("checks").size(), equalTo(0));
    }

    @Test
    void checksAreSortedByName() {
        List<HealthCheck> checks = List.of(new GoodHealthCheck("g"),
                                                            new GoodHealthCheck("a"),
                                                            new GoodHealthCheck("v"));
        HealthSupport support = HealthSupport.builder()
                .addLiveness(checks)
                .build();

        HealthSupport.HealthResponse response = support.callHealthChecks(checks);

        // Test the JSON
        final JsonObject json = response.json();
        assertThat(json.getJsonArray("checks").getJsonObject(0).getString("name"), is("a"));
        assertThat(json.getJsonArray("checks").getJsonObject(1).getString("name"), is("g"));
        assertThat(json.getJsonArray("checks").getJsonObject(2).getString("name"), is("v"));
    }

    @ParameterizedTest
    @MethodSource(value = {"goodChecks"})
    void passingHealthChecksResultInSuccess(List<HealthCheck> goodChecks) {
        HealthSupport support = HealthSupport.builder()
                .addLiveness(goodChecks)
                .build();

        HealthSupport.HealthResponse response = support.callHealthChecks(goodChecks);

        assertThat(response.status(), is(Http.Status.OK_200));

        // Test the JSON
        final JsonObject json = response.json();
        assertThat(json.getString("outcome"), is("UP"));
        assertThat(json.getJsonArray("checks"), notNullValue());
        assertThat(json.getJsonArray("checks"), hasSize(goodChecks.size()));
    }

    @ParameterizedTest
    @MethodSource(value = {"badChecks"})
    void failingHealthChecksResultInFailure(List<HealthCheck> badChecks) {
        HealthSupport support = HealthSupport.builder()
                .addLiveness(badChecks)
                .build();

        HealthSupport.HealthResponse response = support.callHealthChecks(badChecks);

        assertThat(response.status(), is(Http.Status.SERVICE_UNAVAILABLE_503));

        // Test the JSON
        final JsonObject json = response.json();
        assertThat(json.getString("outcome"), is("DOWN"));
        assertThat(json.getJsonArray("checks"), notNullValue());
        assertThat(json.getJsonArray("checks"), hasSize(badChecks.size()));
    }

    @ParameterizedTest
    @MethodSource(value = {"brokenChecks"})
    void brokenHealthChecksResultInFailure(List<HealthCheck> brokenChecks) {
        HealthSupport support = HealthSupport.builder()
                .addLiveness(brokenChecks)
                .build();

        HealthSupport.HealthResponse response = support.callHealthChecks(brokenChecks);

        assertThat(response.status(), is(Http.Status.INTERNAL_SERVER_ERROR_500));

        // Test the JSON
        final JsonObject json = response.json();
        assertThat(json.getString("outcome"), is("DOWN"));
        assertThat(json.getJsonArray("checks"), notNullValue());
        assertThat(json.getJsonArray("checks"), hasSize(brokenChecks.size()));
    }

    private static final class GoodHealthCheck implements HealthCheck {
        private final String name;

        private GoodHealthCheck(String name) {
            this.name = name;
        }

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named(name).up().build();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class BadHealthCheck implements HealthCheck {
        private final String name;

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
}
