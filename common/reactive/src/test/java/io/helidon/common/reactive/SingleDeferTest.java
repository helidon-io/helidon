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

public class SingleDeferTest {

    @Test
    public void nullPublisher() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.defer(() -> null)
                .subscribe(ts);

        ts.assertFailure(NullPointerException.class);
    }

    @Test
    public void supplierCrash() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.defer(() -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
    }
}
