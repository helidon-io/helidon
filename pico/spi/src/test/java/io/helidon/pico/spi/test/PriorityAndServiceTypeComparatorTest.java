/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.spi.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import io.helidon.common.Weighted;
import io.helidon.common.Weights;
import io.helidon.pico.spi.test.testsubjects.PicoServices1;

import io.helidon.pico.spi.test.testsubjects.PicoServices2;
import io.helidon.pico.spi.test.testsubjects.PicoServices3;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

/**
 * Ensure the weights comparator from common works the way we expect it to. Sanity test for Pico...
 */
class PriorityAndServiceTypeComparatorTest {

    static final Comparator<Object> comparator = Weights.weightComparator();

    @Test
    void ordering() {
        assertThat(comparator.compare(null, null), is(0));
        assertThat(comparator.compare(null, null), is(0));
        assertThat(comparator.compare("a", "a"), is(0));
        assertThat(comparator.compare("a", "b"), is(lessThan(0)));
        assertThat(comparator.compare("b", "a"), is(greaterThan(0)));
        assertThat(comparator.compare(1, 2), is(lessThan(0)));

        assertThat(comparator.compare(new HighWeight(), new HighWeight()), is(0));
        assertThat(comparator.compare(new LowWeight(), new HighWeight()), is(greaterThan(0)));
        assertThat(comparator.compare(new LowWeight(), new DefaultWeight()), is(greaterThan(0)));
        assertThat(comparator.compare(new DefaultWeight(), new LowWeight()), is(lessThan(0)));
        assertThat(comparator.compare(new DefaultWeight(), null), is(lessThan(0)));
        assertThat(comparator.compare(null, new DefaultWeight()), is(greaterThan(0)));
        assertThat(comparator.compare(new NoWeight(), null), is(lessThan(0)));
        assertThat(comparator.compare(null, new NoWeight()), is(greaterThan(0)));

        assertThat(comparator.compare(new DefaultWeight(), new DefaultWeight()), is(0));
        assertThat(comparator.compare(new DefaultWeight(), new NoWeight()), lessThan(0));
        assertThat(comparator.compare(new NoWeight(), new NoWeight()), is(0));
        assertThat(comparator.compare(new NoWeight(), new DefaultWeight()), is(greaterThan(0)));

        assertThat(comparator.compare(new JustAClass(), new JustBClass()), is(lessThan(0)));
        assertThat(comparator.compare(new JustBClass(), new JustAClass()), is(greaterThan(0)));
        assertThat(comparator.compare(new JustBClass(), new DefaultWeight()), is(greaterThan(0)));
        assertThat(comparator.compare(new DefaultWeight(), new JustAClass()), is(lessThan(0)));
        assertThat(comparator.compare(null, new JustAClass()), is(greaterThan(0)));
        assertThat(comparator.compare(new JustAClass(), null), is(lessThan(0)));

        ArrayList<?> list = new ArrayList<>(Arrays.asList(new PicoServices1(), new PicoServices2(), new PicoServices3()));
        list.sort(comparator);
        assertThat(list.get(0), instanceOf(PicoServices2.class));
        assertThat(list.get(1), instanceOf(PicoServices3.class));
        assertThat(list.get(2), instanceOf(PicoServices1.class));
    }

    static class DefaultWeight implements Weighted {
    }

    static class NoWeight implements Weighted {
        public Integer getPriority() {
            return null;
        }
    }

    static class LowWeight implements Weighted {
        public Integer getPriority() {
            return 1;
        }
    }

    static class HighWeight implements Weighted {
        @Override
        public double weight() {
            return DEFAULT_WEIGHT + 10;
        }
    }

    static class JustAClass {
    }

    static class JustBClass {
    }

}
