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
package io.helidon.metrics.microprofile;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.media.type.MediaTypes;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestFormatter {

    private static final String SCOPE = "formatScope";
    private static MpRegistryFactory mpRegistryFactory;

    @BeforeAll
    static void init() {
        mpRegistryFactory = MpRegistryFactory.get();
    }

    @Test
    void testSimpleCounterFormatting() {
        MpMetricRegistry reg = (MpMetricRegistry) mpRegistryFactory.registry(SCOPE);

        String counterName = "formatCounter";
        Counter counter = reg.counter(counterName);
        counter.inc();
        assertThat("Updated counter", counter.getCount(), is(1L));

        ((CompositeMeterRegistry) reg.meterRegistry()).getRegistries()
                .stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .map(PrometheusMeterRegistry.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find Prometheus registry"));

        PrometheusFormatter formatter = PrometheusFormatter.builder().build();
        String promFormat = formatter.filteredOutput();

        // Want to match: any uninteresting lines, start-of-line, the meter name, the tags (capturing the scope tag value),
        // capture the meter value, further uninteresting text.
        Pattern expectedNameAndTagAndValue = Pattern.compile(".*?^"
                                                                     + counterName
                                                                     + "_total\\{.*mp_scope=\""
                                                                     + SCOPE
                                                                     + "\".*?}\\s+(\\S+).*?",
                                                             Pattern.MULTILINE + Pattern.DOTALL);
        Matcher matcher = expectedNameAndTagAndValue.matcher(promFormat);
        assertThat("Pattern match check: output " + System.lineSeparator() + promFormat + System.lineSeparator(),
                   matcher.matches(),
                   is(true));
        assertThat("Output matcher groups", matcher.groupCount(), is(1));
        assertThat("Captured metric value as ", Double.parseDouble(matcher.group(1)), is(1.0D));
    }

    @Test
    void testScopeSelection() {
        MpMetricRegistry reg = (MpMetricRegistry) mpRegistryFactory.registry(SCOPE);

        String otherScope = "other";
        MetricRegistry otherRegistry = mpRegistryFactory.registry(otherScope);

        String counterName = "formatCounterWithScope";
        Counter counter = reg.counter(counterName);
        counter.inc();
        assertThat("Updated counter", counter.getCount(), is(1L));

        // Even though we register this "other" counter in a different MP registry, it should
        // find its way into the shared Prometheus meter registry (with the correct mp_scope tag).
        Counter otherCounter = otherRegistry.counter(counterName);
        otherCounter.inc(2L);

        ((CompositeMeterRegistry) reg.meterRegistry()).getRegistries()
                .stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .map(PrometheusMeterRegistry.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find Prometheus registry"));

        PrometheusFormatter formatter = PrometheusFormatter.builder()
                .resultMediaType(MediaTypes.TEXT_PLAIN)
                .scope(SCOPE)
                .build();
        String promFormat = formatter.filteredOutput();

        // Want to match: any uninteresting lines, start-of-line, the meter name, the tags (capturing the scope tag value),
        // capture the meter value, further uninteresting text.
        Pattern expectedNameAndTagAndValue = Pattern.compile(".*?^"
                                                                     + counterName
                                                                     + "_total\\{.*mp_scope=\""
                                                                     + SCOPE
                                                                     + "\".*?}\\s+(\\S+).*?",
                                                             Pattern.MULTILINE + Pattern.DOTALL);
        Matcher matcher = expectedNameAndTagAndValue.matcher(promFormat);
        assertThat("Pattern match check: output " + System.lineSeparator() + promFormat + System.lineSeparator(),
                   matcher.matches(),
                   is(true));
        assertThat("Output matcher groups", matcher.groupCount(), is(1));
        assertThat("Captured metric value as ", Double.parseDouble(matcher.group(1)), is(1.0D));

        // Make sure the "other" counter is not also present in the output; it should have been suppressed
        // because of the scope filtering we requested.
        Pattern unexpectedNameAndTagAndValue = Pattern.compile(".*?^"
                                                                     + counterName
                                                                     + "_total\\{.*mp_scope=\""
                                                                     + otherScope
                                                                     + "\".*?}\\s+(\\S+).*?",
                                                             Pattern.MULTILINE + Pattern.DOTALL);
        matcher = unexpectedNameAndTagAndValue.matcher(promFormat);
        assertThat("Pattern match check: output " + System.lineSeparator() + promFormat + System.lineSeparator(),
                   matcher.matches(),
                   is(false));

    }

    @Test
    void testNameSelection() {
        MpMetricRegistry reg = (MpMetricRegistry) mpRegistryFactory.registry(SCOPE);

        String counterName = "formatCounterWithName";
        Counter counter = reg.counter(counterName);
        counter.inc();
        assertThat("Updated counter", counter.getCount(), is(1L));

        String otherCounterName = "otherFormatCounterWithName";
        Counter otherCounter = reg.counter(otherCounterName);
        otherCounter.inc(3L);

        ((CompositeMeterRegistry) reg.meterRegistry()).getRegistries()
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
        Pattern expectedNameAndTagAndValue = Pattern.compile(".*?^"
                                                                     + counterName
                                                                     + "_total\\{.*mp_scope=\""
                                                                     + SCOPE
                                                                     + "\".*?}\\s+(\\S+).*?",
                                                             Pattern.MULTILINE + Pattern.DOTALL);
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
}
