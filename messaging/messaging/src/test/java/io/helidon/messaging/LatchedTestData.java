/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

public class LatchedTestData<E> extends CountDownLatch {

    final List<E> expected;
    final ArrayList<E> result = new ArrayList<>();

    public LatchedTestData(List<E> expected) {
        super(expected.size());
        this.expected = expected;
    }

    void add(E value) {
        result.add(value);
        countDown();
    }

    void assertEquals(List<E> expected) {
        try {
            this.await(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e);
        }
        assertThat(result, equalTo(expected));
    }

    void assertEquals() {
        assertEquals(this.expected);
    }

    void assertEqualsAnyOrder() {
        try {
            this.await(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e);
        }
        assertThat(result, containsInAnyOrder(expected.toArray()));
    }
}