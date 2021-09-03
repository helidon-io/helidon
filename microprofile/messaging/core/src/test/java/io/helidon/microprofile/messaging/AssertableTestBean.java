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

package io.helidon.microprofile.messaging;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.hamcrest.Matcher;

public interface AssertableTestBean {

    Set<String> TEST_DATA = new HashSet<>(Arrays.asList("teST1", "TEst2", "tESt3"));

    void assertValid();

    default void await(String msg, CountDownLatch countDownLatch) {
        try {
            assertThat(msg + composeOrigin(), countDownLatch.await(500, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(msg + composeOrigin(), e);
        }
    }

    default <T> T await(String msg, CompletableFuture<T> cs) {
        try {
            return cs.get(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return fail(msg + composeOrigin(), e);
        }
    }

    default <T> void assertWithOrigin(String msg, boolean assertion) {
        assertThat(msg + composeOrigin(), assertion);
    }

    default <T> void assertWithOrigin(String msg, T actual, Matcher<? super T> matcher) {
        assertThat(msg + composeOrigin(), actual, matcher);
    }

    default void failWithOrigin(String msg) {
        fail(msg + composeOrigin());
    }

    default void failWithOrigin(String msg, Throwable e) {
        fail(msg + composeOrigin(), e);
    }

    default String composeOrigin() {
        StackTraceElement e = Thread.currentThread().getStackTrace()[3];
        return " at " + e.toString();
    }
}
