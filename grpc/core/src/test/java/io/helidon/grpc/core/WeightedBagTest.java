/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.grpc.core;

import java.util.Arrays;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

class WeightedBagTest {

    @Test
    public void shouldReturnElementsInOrder() {
        WeightedBag<String> bag = WeightedBag.create();
        bag.add("Three", 3.0);
        bag.add("Two", 2.0);
        bag.add("One", 1.0);
        assertThat(bag, contains("Three", "Two", "One"));
    }

    @Test
    public void shouldReturnElementsInOrderWithinSameWeight() {
        WeightedBag<String> bag = WeightedBag.create();
        bag.add("Two", 2.0);
        bag.add("TwoToo", 2.0);
        assertThat(bag, contains("Two", "TwoToo"));
    }

    @Test
    public void shouldReturnNoWeightElementsLast() {
        WeightedBag<String> bag = WeightedBag.create();
        bag.add("Three", 3.0);
        bag.add("Last");
        bag.add("One", 1.0);
        assertThat(bag, contains("Three", "One", "Last"));
    }

    @Test
    public void shouldGetWeightFromAnnotation() {
        WeightedBag<Object> bag = WeightedBag.create();
        Value value = new Value();
        bag.add("One", 1.0);
        bag.add("Three", 3.0);
        bag.add(value);
        assertThat(bag, contains("Three", value, "One"));
    }

    @Test
    public void shouldGetWeightFromWeighted() {
        WeightedBag<Object> bag = WeightedBag.create();
        WeightedValue value = new WeightedValue();
        bag.add("One", 1.0);
        bag.add("Three", 3.0);
        bag.add(value);
        assertThat(bag, contains("Three", value, "One"));
    }

    @Test
    public void shouldUseWeightFromWeightedOverAnnotation() {
        WeightedBag<Object> bag = WeightedBag.create();
        AnnotatedWeightedValue value = new AnnotatedWeightedValue();
        bag.add("One", 1.0);
        bag.add("Three", 3.0);
        bag.add(value);
        assertThat(bag, contains("Three", value, "One"));
    }

    @Test
    public void shouldUseDefaultWeight() {
        WeightedBag<Object> bag = WeightedBag.withDefaultWeight(2);
        bag.add("One", 1.0);
        bag.add("Three", 3.0);
        bag.add("Two");
        assertThat(bag, contains("Three", "Two", "One"));
    }

    @Test
    public void shouldAddAll() {
        WeightedBag<Object> bag = WeightedBag.create();
        bag.addAll(Arrays.asList("Three", "Two", "One"));
        assertThat(bag, contains("Three", "Two", "One"));
    }

    @Test
    public void shouldAddAllWithWeight() {
        WeightedBag<Object> bag = WeightedBag.create();
        bag.add("First", 1.0);
        bag.add("Last", 3.0);
        bag.addAll(Arrays.asList("Three", "Two", "One"), 2.0);
        assertThat(bag, contains("Last", "Three", "Two", "One", "First"));
    }

    @Test
    public void shouldMerge() {
        WeightedBag<Object> bagOne = WeightedBag.create();
        WeightedBag<Object> bagTwo = WeightedBag.create();

        bagOne.add("A", 1.0);
        bagOne.add("B", 2.0);
        bagOne.add("C", 2.0);
        bagOne.add("D", 3.0);

        bagTwo.add("E", 1.0);
        bagTwo.add("F", 3.0);
        bagTwo.add("G", 3.0);
        bagTwo.add("H", 4.0);

        bagOne.merge(bagTwo);
        assertThat(bagOne, contains("H", "D", "F", "G", "B", "C", "A", "E"));
    }

    @Weight(2.0)
    public static class Value {
    }

    public static class WeightedValue implements Weighted {
        @Override
        public double weight() {
            return 2.0;
        }
    }

    @Weight(0.0)
    public static class AnnotatedWeightedValue implements Weighted {
        @Override
        public double weight() {
            return 2.0;
        }
    }
}
