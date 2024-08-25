/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class TestIntegration {

    @Test
    void testHelidonRegistrationViaMicrometer() {

        MeterRegistry hMeterRegistry = Metrics.globalRegistry();

        Counter hCounter = hMeterRegistry.getOrCreate(Counter.builder("hCounter1"));
        hCounter.increment(2);

        io.micrometer.core.instrument.Counter unwrappedCounter = hCounter.unwrap(io.micrometer.core.instrument.Counter.class);
        assertThat("hCounter via unwrap", unwrappedCounter.count(), equalTo(2D));

        unwrappedCounter.increment(3);

        assertThat("hCounter via Helidon unwrap after Micrometer increment", hCounter.count(), equalTo(5L));

        io.micrometer.core.instrument.MeterRegistry mMeterRegistry = io.micrometer.core.instrument.Metrics.globalRegistry;
        io.micrometer.core.instrument.Counter mCounter = mMeterRegistry.counter("hCounter1", "scope", "application");
        assertThat(unwrappedCounter, sameInstance(mCounter));
        assertThat("hCounter via Micrometer meter registry", mCounter.count(), equalTo(5D));
    }

    @Test
    void testMicrometerRegistrationViaHelidon() {
        MeterRegistry hMeterRegistry = Metrics.globalRegistry();
        io.micrometer.core.instrument.MeterRegistry mMeterRegistry = io.micrometer.core.instrument.Metrics.globalRegistry;
        io.micrometer.core.instrument.Counter mCounter = mMeterRegistry.counter("mCounter1", "scope", "application");
        mCounter.increment(2);

        // Should find the previously-registered counter.
        Counter hCounter = hMeterRegistry.getOrCreate(Counter.builder("mCounter1"));

        assertThat(mCounter, sameInstance(hCounter.unwrap(io.micrometer.core.instrument.Counter.class)));
        assertThat("mCounter via Helidon with no explicit tag",
                   hCounter.count(),
                   equalTo(2L));

        // Should find the previously-registered counter via a general search.
        assertThat("mCounter via Helidon meters with predicate",
                   hMeterRegistry.meters(m -> m.id().name().equals("mCounter1"))
                           .stream()
                           .filter(m -> m.type() == Meter.Type.COUNTER)
                           .map(m -> ((Counter) m).count())
                           .findFirst(),
                   OptionalMatcher.optionalValue(equalTo(2L)));
    }
}
