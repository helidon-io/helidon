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

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

public class SingleOnErrorResumeWithTest {

    @Test
    public void fallbackFunctionCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.<Integer>error(new IOException())
                .onErrorResumeWith(e -> { throw new IllegalArgumentException(); })
                .subscribe(ts);


        ts.assertFailure(IllegalArgumentException.class);

        assertThat(ts.getLastError().getSuppressed()[0], instanceOf(IOException.class));
    }

    @Test
    public void fallbackToError() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.<Integer>error(new IOException())
                .onErrorResumeWith(e -> Single.error(new IllegalArgumentException()))
                .subscribe(ts);


        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void emptySource() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.<Integer>empty()
                .onErrorResumeWith(e -> Single.just(e.hashCode()))
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void emptyFallback() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.<Integer>error(new IOException())
                .onErrorResumeWith(e -> Single.empty())
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void noSelfSuppressionFailure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.<Integer>error(new IllegalArgumentException())
                .onErrorResumeWith(e -> { throw (IllegalArgumentException)e; })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
    }
}
