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

import java.util.function.Function;

public class SingleToTest {

    @Test
    public void compose() {
        TestSubscriber<String> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Function<Single<Integer>, Single<String>> function =
                upstream -> upstream.map(Object::toString);

        Single.just(1)
                .to(function)
                .subscribe(ts);

        ts.assertResult("1");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void composeNull() {
        Single.just(1).to(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void composeThrows() {
        Single.just(1)
                .to(s -> { throw new IllegalArgumentException(); });
    }
}
