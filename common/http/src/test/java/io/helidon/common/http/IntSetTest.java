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

package io.helidon.common.http;

import java.util.HashSet;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class IntSetTest {
    @RepeatedTest(20)
    void test() {
        IntSet set = new IntSet(14);
        HashSet<Integer> expected = new HashSet<>();
        Random random = new Random();

        for (int i = 0; i < 14; i++) {
            if (random.nextBoolean()) {
                expected.add(i);
                set.add(i);
            }
        }

        HashSet<Integer> actual = new HashSet<>();
        for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
            actual.add(i);
        }

        assertThat(actual, is(expected));
    }
}