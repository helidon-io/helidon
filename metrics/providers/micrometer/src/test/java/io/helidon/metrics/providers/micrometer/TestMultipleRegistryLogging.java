/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.testing.MultiStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isEmptyString;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

class TestMultipleRegistryLogging {

    private static MultiStream multiStream;
    private static ByteArrayOutputStream baos;
    private static PrintStream originalErr;
    private static PrintStream testPrintStream;

    @BeforeAll
    static void beforeAll() {
        baos = new ByteArrayOutputStream();
        originalErr = System.err;
        testPrintStream = new PrintStream(baos);
        multiStream = MultiStream.create(baos, testPrintStream);
        System.setErr(multiStream);
    }

    @BeforeEach
    void setUp() {
        MetricsFactory.closeAll();
    }

    @AfterEach
    void tearDown() {
        baos.reset();
    }

    @AfterAll
    static void afterAll() {
        MMeterRegistry.clearMultipleInstantiationInfo();
        multiStream.close();
        System.setErr(originalErr);
    }

    @Test
    void testSingleRegistry() {
        Metrics.globalRegistry();
        testPrintStream.flush();
        String output = baos.toString();
        assertThat("Single meter registry", output, isEmptyString());
    }

    @Test
    void testTwoRegistries() {
        Metrics.globalRegistry();
        Metrics.createMeterRegistry();
        testPrintStream.flush();

        String output = baos.toString();
        assertThat("Two meter registries", output, allOf(
                containsString("Unexpected duplicate"),
                containsString("Original instantiation"),
                containsString("Additional instantiation")));
    }

    @Test
    void testThreeRegistries() {
        Metrics.globalRegistry();
        Metrics.createMeterRegistry();
        Metrics.createMeterRegistry();
        testPrintStream.flush();

        String output = baos.toString();
        assertThat("Three meter registries", output, allOf(
                containsString("Unexpected duplicate"),
                containsString("Original instantiation"),
                containsString("Additional instantiation"),
                containsString("Unexpected additional instantiation")));
    }

    @Test
    void testTwoRegistriesWithWarningDisabled() {
        MetricsConfig configWithWarningsSuppressed = MetricsConfig.builder().warnOnMultipleRegistries(false).build();
        Metrics.createMeterRegistry(configWithWarningsSuppressed);
        Metrics.createMeterRegistry(configWithWarningsSuppressed);
        testPrintStream.flush();

        String output = baos.toString();
        assertThat("Two meter registrations with warnings suppressed", output, allOf(
                not(containsString("Unexpected duplicate")),
                not(containsString("Original instantiation")),
                not(containsString("Additional instantiation")),
                not(containsString("Unexpected additional instantiation"))));

    }

}
