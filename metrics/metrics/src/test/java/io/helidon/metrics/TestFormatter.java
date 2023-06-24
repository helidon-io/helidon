/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.SystemTagsManager;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestFormatter {

    private static final String SCOPE = "formatScope";
    private static final String SCOPE_TAG_NAME = "scope";

    private static final String COUNTER_OUTPUT_PATTERN = ".*?^%s_total\\{.*%s=\"%s\".*?}\\s+(\\S).*?";

    private static RegistryFactory registryFactory;
    private static MetricsSettings metricsSettings;

    @BeforeAll
    static void init() {
        metricsSettings = MetricsSettings.create();
        registryFactory = RegistryFactory.create(metricsSettings);
        SystemTagsManager.create(metricsSettings, SCOPE_TAG_NAME, null);
    }

    @AfterAll
    static void finish() {
        SystemTagsManager.create(metricsSettings, null, null);
    }

    @Test
    void testSimpleCounterFormatting() {
        io.helidon.metrics.api.Registry reg = registryFactory.getRegistry(SCOPE);

        String counterName = "formatCounter";
        Counter counter = reg.counter(counterName);
        counter.inc();
        assertThat("Updated counter", counter.getCount(), is(1L));

         Metrics.globalRegistry.getRegistries()
                .stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .map(PrometheusMeterRegistry.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find Prometheus registry"));

        PrometheusFormatter formatter = PrometheusFormatter.builder().build();
        String promFormat = formatter.filteredOutput();

        // Want to match: any uninteresting lines, start-of-line, the meter name, the tags (capturing the scope tag value),
        // capture the meter value, further uninteresting text.
        Pattern expectedNameAndTagAndValue = counterPattern(counterName, SCOPE);

        Matcher matcher = expectedNameAndTagAndValue.matcher(promFormat);
        assertThat("Pattern match check: output " + System.lineSeparator() + promFormat + System.lineSeparator(),
                   matcher.matches(),
                   is(true));
        assertThat("Output matcher groups", matcher.groupCount(), is(1));
        assertThat("Captured metric value as ", Double.parseDouble(matcher.group(1)), is(1.0D));
    }

    @Test
    void testScopeSelection() {
        io.helidon.metrics.api.Registry reg = registryFactory.getRegistry(SCOPE);

        String otherScope = "other";
        MetricRegistry otherRegistry = registryFactory.getRegistry(otherScope);

        String counterName = "formatCounterWithScope";
        Counter counter = reg.counter(counterName);
        counter.inc();
        assertThat("Updated counter", counter.getCount(), is(1L));

        // Even though we register this "other" counter in a different MP registry, it should
        // find its way into the shared Prometheus meter registry (with the correct mp_scope tag).
        Counter otherCounter = otherRegistry.counter(counterName);
        otherCounter.inc(2L);

        Metrics.globalRegistry.getRegistries()
                .stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .map(PrometheusMeterRegistry.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find Prometheus registry"));

        PrometheusFormatter formatter = PrometheusFormatter.builder()
                .resultMediaType(MediaTypes.TEXT_PLAIN)
                .scopeTagName(SCOPE_TAG_NAME)
                .scope(SCOPE)
                .build();
        String promFormat = formatter.filteredOutput();

        // Want to match: any uninteresting lines, start-of-line, the meter name, the tags (capturing the scope tag value),
        // capture the meter value, further uninteresting text.
        Pattern expectedNameAndTagAndValue = counterPattern(counterName, SCOPE);
        Matcher matcher = expectedNameAndTagAndValue.matcher(promFormat);
        assertThat("Pattern match check: output " + System.lineSeparator() + promFormat + System.lineSeparator(),
                   matcher.matches(),
                   is(true));
        assertThat("Output matcher groups", matcher.groupCount(), is(1));
        assertThat("Captured value of counter " + counterName + " as ",
                   Double.parseDouble(matcher.group(1)),
                   is(1.0D));

        // Make sure the "other" counter is not also present in the output; it should have been suppressed
        // because of the scope filtering we requested.
        Pattern unexpectedNameAndTagAndValue = counterPattern(counterName, otherScope);
        matcher = unexpectedNameAndTagAndValue.matcher(promFormat);
        assertThat("Pattern match check: output " + System.lineSeparator() + promFormat + System.lineSeparator(),
                   matcher.matches(),
                   is(false));

    }

    @Test
    void testNameSelection() {
        io.helidon.metrics.api.Registry reg = registryFactory.getRegistry(SCOPE);

        String counterName = "formatCounterWithName";
        Counter counter = reg.counter(counterName);
        counter.inc();
        assertThat("Updated counter", counter.getCount(), is(1L));

        String otherCounterName = "otherFormatCounterWithName";
        Counter otherCounter = reg.counter(otherCounterName);
        otherCounter.inc(3L);

        Metrics.globalRegistry.getRegistries()
                .stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .map(PrometheusMeterRegistry.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find Prometheus registry"));

        PrometheusFormatter formatter = PrometheusFormatter.builder()
                .resultMediaType(MediaTypes.TEXT_PLAIN)
                .meterName(counterName)
                .build();
        String promFormat = formatter.filteredOutput();

        // Want to match: any uninteresting lines, start-of-line, the meter name, the tags (capturing the scope tag value),
        // capture the meter value, further uninteresting text.
        Pattern expectedNameAndTagAndValue = counterPattern(counterName, SCOPE);
        Matcher matcher = expectedNameAndTagAndValue.matcher(promFormat);
        assertThat("Pattern match check: output " + System.lineSeparator() + promFormat + System.lineSeparator(),
                   matcher.matches(),
                   is(true));
        assertThat("Output matcher groups", matcher.groupCount(), is(1));
        assertThat("Captured metric value as ", Double.parseDouble(matcher.group(1)), is(1.0D));

        // Make sure the counter with an unmatching name does not also appear in the output.
        Pattern unexpectedNameAndTagValue = Pattern.compile(".*?^"
                                                                    + otherCounterName
                                                                    + "_total\\{.*mp_scope=\""
                                                                    + SCOPE
                                                                    + "\".*?}\\s+(\\S+).*?",
                                                            Pattern.MULTILINE + Pattern.DOTALL);
        matcher = unexpectedNameAndTagValue.matcher(promFormat);
        assertThat("Pattern match check: output " + System.lineSeparator() + promFormat + System.lineSeparator(),
                   matcher.matches(),
                   is(false));
    }

    private static Pattern counterPattern(String counterName, String scope) {
        return Pattern.compile(String.format(COUNTER_OUTPUT_PATTERN,
                                             counterName,
                                             SCOPE_TAG_NAME,
                                             scope),
                               Pattern.MULTILINE | Pattern.DOTALL);
    }
}
