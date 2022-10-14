/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Unit test for {@link HelidonCounter}.
 */
class HelidonCounterTest {
    private static Metadata meta;
    private HelidonCounter counter;
    private MetricID counterID;
    private HelidonCounter wrappingCounter;
    private MetricID wrappingCounterID;

    @BeforeAll
    static void initClass() {
        meta = Metadata.builder()
                .withName("theName")
                .withDisplayName("theDisplayName")
                .withDescription("theDescription")
                .withType(MetricType.COUNTER)
                .withUnit(MetricUnits.NONE)
                .build();
    }

    @BeforeEach
    void resetCounter() {
        Counter wrapped = new Counter() {
            @Override
            public void inc() {

            }

            @Override
            public void inc(long n) {

            }

            @Override
            public long getCount() {
                return 49;
            }
        };
        counter = HelidonCounter.create("base", meta);
        counterID = new MetricID("theName", new Tag("a", "b"), new Tag("c", "d"));
        wrappingCounter = HelidonCounter.create("base", meta, wrapped);
        wrappingCounterID = new MetricID("theName");
    }

    @Test
    void testValue() {
        testValues(0);
    }

    @Test
    void testInc() {
        testValues(0);
        counter.inc();
        wrappingCounter.inc();
        testValues(1);
    }

    @Test
    void testIncWithParam() {
        testValues(0);
        counter.inc(49);
        wrappingCounter.inc();
        testValues(49);
    }

    private void testValues(long counterValue) {
        assertThat(counter.getCount(), is(counterValue));
        assertThat(wrappingCounter.getCount(), is(49L));
    }
}
