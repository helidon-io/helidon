/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics;

import io.helidon.metrics.LabeledSample.Derived;
import io.helidon.metrics.WeightedSnapshot.WeightedSample;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.LabeledSample.derived;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestNearestValueSearch {

    private static final WeightedSample[] VALUES = new WeightedSample[] {
            new WeightedSample(1L),
            new WeightedSample(3L),
            new WeightedSample(5L),
            new WeightedSample(7L)};

    @Test
    public void testExactMatch() {
        assertThat("Exact match of 1", WeightedSnapshot.slotNear(derived(1.0), VALUES), is(0));
        assertThat("Exact match of 3", WeightedSnapshot.slotNear(derived(3.0), VALUES), is(1));
        assertThat("Exact match of 5", WeightedSnapshot.slotNear(derived(5.0), VALUES), is(2));
    }

    @Test
    public void testBeforeFirst() {
        assertThat("Approx match before first element", WeightedSnapshot.slotNear(derived(0.5), VALUES), is(0));
    }

    @Test
    public void testAfterLast() {
        assertThat("Approx match after last element", WeightedSnapshot.slotNear(derived(9.0), VALUES),
                is(VALUES.length - 1));
    }

    @Test
    public void testCloserToLower() {
        assertThat("Closer to lowest", WeightedSnapshot.slotNear(derived(1.2), VALUES), is(0));
        assertThat("Closer to inside", WeightedSnapshot.slotNear(derived(3.2), VALUES), is(1));
    }

    @Test
    public void testCloserToHigher() {
        assertThat("Closer to highest", WeightedSnapshot.slotNear(derived(6.5), VALUES), is(3));
        assertThat("Closer to inside", WeightedSnapshot.slotNear(derived(2.5), VALUES), is(1));
    }

    @Test
    public void testMidpoint() {
        assertThat("Midpoint", WeightedSnapshot.slotNear(derived(2.0), VALUES), is(0));
    }
}
