/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.annotation.Priority;

import io.helidon.common.Prioritized;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;


public class PriorityBagTest {

    @Test
    public void shouldReturnElementsInOrder() {
        PriorityBag<String> bag = PriorityBag.create();
        bag.add("Three", 3);
        bag.add("Two", 2);
        bag.add("One", 1);

        assertThat(bag, contains("One", "Two", "Three"));
    }

    @Test
    public void shouldReturnElementsInOrderWithinSamePriority() {
        PriorityBag<String> bag = PriorityBag.create();
        bag.add("Two", 2);
        bag.add("TwoToo", 2);

        assertThat(bag, contains("Two", "TwoToo"));
    }

    @Test
    public void shouldReturnNoPriorityElementsLast() {
        PriorityBag<String> bag = PriorityBag.create();
        bag.add("Three", 3);
        bag.add("Last");
        bag.add("One", 1);

        assertThat(bag, contains("One", "Three", "Last"));
    }

    @Test
    public void shouldGetPriorityFromAnnotation() {
        PriorityBag<Object> bag = PriorityBag.create();
        Value value = new Value();
        bag.add("One", 1);
        bag.add("Three", 3);
        bag.add(value);

        assertThat(bag, contains("One", value, "Three"));
    }

    @Test
    public void shouldGetPriorityFromPrioritized() {
        PriorityBag<Object> bag = PriorityBag.create();
        PrioritizedValue value = new PrioritizedValue();
        bag.add("One", 1);
        bag.add("Three", 3);
        bag.add(value);

        assertThat(bag, contains("One", value, "Three"));
    }

    @Test
    public void shouldUsePriorityFromPrioritizedOverAnnotation() {
        PriorityBag<Object> bag = PriorityBag.create();
        AnnotatedPrioritizedValue value = new AnnotatedPrioritizedValue();
        bag.add("One", 1);
        bag.add("Three", 3);
        bag.add(value);

        assertThat(bag, contains("One", value, "Three"));
    }

    @Test
    public void shouldUseDefaultPriority() {
        PriorityBag<Object> bag = PriorityBag.withDefaultPriority(2);
        bag.add("One", 1);
        bag.add("Three", 3);
        bag.add("Two");

        assertThat(bag, contains("One", "Two", "Three"));
    }

    @Test
    public void shouldAddAll() {
        PriorityBag<Object> bag = PriorityBag.create();
        bag.addAll(Arrays.asList("One", "Two", "Three"));

        assertThat(bag, contains("One", "Two", "Three"));
    }

    @Test
    public void shouldAddAllWithPriority() {
        PriorityBag<Object> bag = PriorityBag.create();
        bag.add("First", 1);
        bag.add("Last", 3);
        bag.addAll(Arrays.asList("One", "Two", "Three"), 2);

        assertThat(bag, contains("First", "One", "Two", "Three", "Last"));
    }

    @Test
    public void shouldMerge() {
        PriorityBag<Object> bagOne = PriorityBag.create();
        PriorityBag<Object> bagTwo = PriorityBag.create();

        bagOne.add("A", 1);
        bagOne.add("B", 2);
        bagOne.add("C", 2);
        bagOne.add("D", 3);

        bagTwo.add("E", 1);
        bagTwo.add("F", 3);
        bagTwo.add("G", 3);
        bagTwo.add("H", 4);

        bagOne.merge(bagTwo);

        assertThat(bagOne, contains("A", "E", "B", "C", "D", "F", "G", "H"));
    }


    @Priority(2)
    public static class Value {
    }


    public static class PrioritizedValue
            implements Prioritized {
        @Override
        public int priority() {
            return 2;
        }
    }


    @Priority(0)
    public static class AnnotatedPrioritizedValue
            implements Prioritized {
        @Override
        public int priority() {
            return 2;
        }
    }
}
