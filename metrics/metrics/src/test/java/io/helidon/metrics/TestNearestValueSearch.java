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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestNearestValueSearch {

    private static final long[] VALUES = {1, 3, 5, 7};

    @Test
    public void testExactMatch() {
        assertThat("Exact match of 1", WeightedSnapshot.slotNear(VALUES, 1.0), is(0));
        assertThat("Exact match of 3", WeightedSnapshot.slotNear(VALUES, 3.0), is(1));
        assertThat("Exact match of 5", WeightedSnapshot.slotNear(VALUES, 5.0), is(2));
    }

    @Test
    public void testBeforeFirst() {
        assertThat("Approx match before first element", WeightedSnapshot.slotNear(VALUES, 0.5), is(0));
    }

    @Test
    public void testAfterLast() {
        assertThat("Approx match after last element", WeightedSnapshot.slotNear(VALUES, 9.0), is(VALUES.length - 1));
    }

    @Test
    public void testCloserToLower() {
        assertThat("Closer to lowest", WeightedSnapshot.slotNear(VALUES, 1.2), is(0));
        assertThat("Closer to inside", WeightedSnapshot.slotNear(VALUES, 3.2), is(1));
    }

    @Test
    public void testCloserToHigher() {
        assertThat("Closer to highest", WeightedSnapshot.slotNear(VALUES, 6.5), is(3));
        assertThat("Closer to inside", WeightedSnapshot.slotNear(VALUES, 2.5), is(1));
    }

    @Test
    public void testMidpoint() {
        assertThat("Midpoint", WeightedSnapshot.slotNear(VALUES, 2.0), is(0));
    }
}
