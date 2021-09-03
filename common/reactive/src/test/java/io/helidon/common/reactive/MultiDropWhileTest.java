/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

public class MultiDropWhileTest {

    @Test
    public void predicateCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(Collections.singletonList(1))
                .dropWhile(v -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getSubcription(), is(notNullValue()));
        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }
}
