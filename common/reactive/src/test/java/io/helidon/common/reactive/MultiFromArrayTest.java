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

import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Iterator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.testng.Assert.assertEquals;

public class MultiFromArrayTest {
    @Test
    public void nullItem() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(new Integer[] { 1, null, 2 })
        .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getItems().size(), is(1));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), instanceOf(NullPointerException.class));
    }

    @Test
    public void cancelAfterItem() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(new Integer[] { 1, 2, 3 })
                .limit(2)
                .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getItems().size(), is(2));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
    }

}
