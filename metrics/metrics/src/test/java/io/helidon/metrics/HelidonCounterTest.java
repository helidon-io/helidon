/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricUnits;
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

    @BeforeAll
    static void initClass() {
        meta = Metadata.builder()
                .withName("theName")
                .withDescription("theDescription")
                .withUnit(MetricUnits.NONE)
                .build();
    }

    @BeforeEach
    void resetCounter() {
        counter = HelidonCounter.create("base", meta);
    }

    @Test
    void testValue() {
        testValues(0);
    }

    @Test
    void testInc() {
        testValues(0);
        counter.inc();
        testValues(1);
    }

    @Test
    void testIncWithParam() {
        testValues(0);
        counter.inc(49);
        testValues(49);
    }

    private void testValues(long counterValue) {
        assertThat(counter.getCount(), is(counterValue));
    }
}
