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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestFormatter {

    private static final String SCOPE = "formatScope";
    private static final String COUNTER_NAME = "formatCounter";
    private static MpRegistryFactory mpRegistryFactory;

    @BeforeAll
    static void init() {
        mpRegistryFactory = MpRegistryFactory.get();
    }

    @Test
    void testSimpleCounterFormatting() {
        MpMetricRegistry reg = (MpMetricRegistry) mpRegistryFactory.registry(SCOPE);

        Counter counter = reg.counter(COUNTER_NAME);
        counter.inc();
        assertThat("Updated counter", counter.getCount(), is(1L));

        PrometheusMeterRegistry promReg = ((CompositeMeterRegistry) reg.meterRegistry()).getRegistries()
                .stream()
                .filter(PrometheusMeterRegistry.class::isInstance)
                .map(PrometheusMeterRegistry.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find Prometheus registry"));

        String promFormat = PrometheusFormatter.formattedOutput(promReg,
                                                                MediaTypes.TEXT_PLAIN,
                                                                Optional.empty(),
                                                                Optional.empty());

        // Want to match: any uninteresting lines, start-of-line, the meter name, the tags (capturing the scope tag value),
        // catpure the meter value, further uninteresting text.
        Pattern expectedNameAndTagAndValue = Pattern.compile(".*?^"
                                                                     + COUNTER_NAME
                                                                     + "_total\\{.*mp_scope=\""
                                                                     + SCOPE
                                                                     + "\".*?}\\s+(\\S+).*?",
                                                             Pattern.MULTILINE + Pattern.DOTALL);
        Matcher matcher = expectedNameAndTagAndValue.matcher(promFormat);
        assertThat("Output " + System.lineSeparator() + promFormat + System.lineSeparator() + " matches expected", matcher.matches(), is(true));
        assertThat("Output matcher groups", matcher.groupCount(), is(1));
        assertThat("Captured metric value as ", Double.parseDouble(matcher.group(1)), is(1.0D));
    }
}
