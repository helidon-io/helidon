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

import java.io.IOException;

public class MultiTakeUntilPublisherTest {

    @Test
    public void otherSignalItem() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.never()
                .takeUntil(Multi.range(1, 5))
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void otherCompletes() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.never()
                .takeUntil(Multi.empty())
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void otherError() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.never()
                .takeUntil(Multi.error(new IOException()))
                .subscribe(ts);

        ts.assertFailure(IOException.class);
    }

    @Test
    public void cancelUpfront() {
        TestSubscriber<Object> ts = new TestSubscriber<>();
        ts.cancel();

        Multi.range(1, 5)
                .takeUntil(Multi.error(new IOException()))
                .subscribe(ts);

        ts.assertEmpty();
    }
}
