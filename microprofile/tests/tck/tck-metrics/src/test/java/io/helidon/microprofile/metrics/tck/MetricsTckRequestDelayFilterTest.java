/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics.tck;

import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MetricsTckRequestDelayFilterTest {

    private String originalDelayMs;
    private String originalDelayPathPattern;

    @Before
    public void rememberProperties() {
        originalDelayMs = System.getProperty(MetricsTckRequestDelayFilter.DELAY_MS_PROPERTY);
        originalDelayPathPattern = System.getProperty(MetricsTckRequestDelayFilter.DELAY_PATH_PATTERN_PROPERTY);
    }

    @After
    public void restoreProperties() {
        restoreProperty(MetricsTckRequestDelayFilter.DELAY_MS_PROPERTY, originalDelayMs);
        restoreProperty(MetricsTckRequestDelayFilter.DELAY_PATH_PATTERN_PROPERTY, originalDelayPathPattern);
    }

    @Test
    public void testMatchesDefaultConfiguredPaths() {
        assertThat(MetricsTckRequestDelayFilter.matchesPath("get-async"), is(true));
        assertThat(MetricsTckRequestDelayFilter.matchesPath("metrics"), is(true));
        assertThat(MetricsTckRequestDelayFilter.matchesPath("/get-async"), is(true));
        assertThat(MetricsTckRequestDelayFilter.matchesPath("/metrics"), is(true));
        assertThat(MetricsTckRequestDelayFilter.matchesPath("optional/get-async"), is(true));
        assertThat(MetricsTckRequestDelayFilter.matchesPath("optional/metrics"), is(true));
    }

    @Test
    public void testIgnoresOtherPaths() {
        assertThat(MetricsTckRequestDelayFilter.matchesPath("get-async-extra"), is(false));
        assertThat(MetricsTckRequestDelayFilter.matchesPath("metrics-extra"), is(false));
        assertThat(MetricsTckRequestDelayFilter.matchesPath(""), is(false));
        assertThat(MetricsTckRequestDelayFilter.matchesPath(null), is(false));
    }

    @Test
    public void testUsesConfiguredDelayMs() {
        System.setProperty(MetricsTckRequestDelayFilter.DELAY_MS_PROPERTY, "400");

        assertThat(MetricsTckRequestDelayFilter.configuredDelayMs(), is(400L));
    }

    @Test
    public void testFallsBackToDefaultDelayMs() {
        System.setProperty(MetricsTckRequestDelayFilter.DELAY_MS_PROPERTY, "not-a-number");

        assertThat(MetricsTckRequestDelayFilter.configuredDelayMs(),
                   is(MetricsTckRequestDelayFilter.DEFAULT_DELAY_MS));
    }

    @Test
    public void testUsesConfiguredPathPattern() {
        System.setProperty(MetricsTckRequestDelayFilter.DELAY_PATH_PATTERN_PROPERTY, ".*/custom");

        Pattern configuredPattern = MetricsTckRequestDelayFilter.configuredDelayPathPattern();

        assertThat(MetricsTckRequestDelayFilter.matchesPath("tck/custom", configuredPattern), is(true));
        assertThat(MetricsTckRequestDelayFilter.matchesPath("metrics", configuredPattern), is(false));
    }

    @Test
    public void testFallsBackToDefaultPattern() {
        System.setProperty(MetricsTckRequestDelayFilter.DELAY_PATH_PATTERN_PROPERTY, "[");

        Pattern configuredPattern = MetricsTckRequestDelayFilter.configuredDelayPathPattern();

        assertThat(MetricsTckRequestDelayFilter.matchesPath("metrics", configuredPattern), is(true));
        assertThat(MetricsTckRequestDelayFilter.matchesPath("other", configuredPattern), is(false));
    }

    private static void restoreProperty(String propertyName, String value) {
        if (value == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, value);
        }
    }
}
