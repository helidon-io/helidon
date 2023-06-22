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
package io.helidon.microprofile.metrics;

import java.util.Arrays;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestCounters {

    static PrometheusMeterRegistry prometheusMeterRegistry;
    static MeterRegistry meterRegistry;
    static MpMetricRegistry mpMetricRegistry;

    @BeforeAll
    static void setup() {
        PrometheusConfig config = new PrometheusConfig() {
            @Override
            public String get(String s) {
                return null;
            }
        };

        prometheusMeterRegistry = new PrometheusMeterRegistry(config);
        meterRegistry = Metrics.globalRegistry;

        mpMetricRegistry = MpMetricRegistry.create("myscope", meterRegistry);
    }

    @Test
    void testCounter() {
        Counter counter = mpMetricRegistry.counter("myCounter");
        counter.inc();
        assertThat("Updated counter", counter.getCount(), is(1L));
    }

    @Test
    void testConflictingTags() {
        Counter counter = mpMetricRegistry.counter("conflictingCounterDueToTags"); // name only
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                     () -> mpMetricRegistry.counter("conflictingCounterDueToTags",
                                                    new Tag[] {new Tag("tag1", "value1")}));
        assertThat("Inconsistent tags check", ex.getMessage(), containsString("inconsistent"));
    }

    @Test
    void testConsistentTags() {
        Tag[] tags = {new Tag("tag1", "value1"), new Tag("tag2", "value2")};
        Counter counter1 = mpMetricRegistry.counter("sameTag", tags);
        Counter counter2 = mpMetricRegistry.counter("sameTag", Arrays.copyOf(tags, tags.length));
        assertThat("Reregistered meter", counter2, is(sameInstance(counter1)));
    }

    @Test
    void conflictingMetadata() {
        mpMetricRegistry.counter(Metadata.builder()
                                         .withName("counterWithMetadata")
                                         .withDescription("first")
                                         .build());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> mpMetricRegistry.counter(Metadata.builder()
                                                                                          .withName("counterWithMetadata")
                                                                                          .withDescription("second")
                                                                                          .build()));

        assertThat("Error message",
                   ex.getMessage().matches(".*?metadata.*?inconsistent.*?"),
                   is(true));
    }
}
