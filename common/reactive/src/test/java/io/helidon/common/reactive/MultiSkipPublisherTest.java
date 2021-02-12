/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiSkipPublisherTest {

    @Test
    public void errorBeforeSkip() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IllegalArgumentException())
                .skip(5)
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void errorAfterSkip() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.create(() -> new Iterator<Object>() {

            int count;

            @Override
            public boolean hasNext() {
                if (++count == 4) {
                    throw new IllegalArgumentException();
                }
                return true;
            }

            @Override
            public Object next() {
                return count;
            }
        })
        .skip(2)
        .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(3));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }
}
