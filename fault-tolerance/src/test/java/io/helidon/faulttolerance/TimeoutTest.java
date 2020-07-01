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
 */

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.reactive.Multi;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeoutTest {

    @Test
    void testTimeout() {
        long now = System.currentTimeMillis();
        CompletionException exc = assertThrows(CompletionException.class,
                                               () -> Timeout.create(Duration.ofMillis(10)).invoke(CompletableFuture::new)
                                                       .await(5, TimeUnit.SECONDS));
        long time = System.currentTimeMillis()- now;
        Throwable cause = exc.getCause();
        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(TimeoutException.class));
        assertThat("Should have timed out in FT, not in await in test", time, is(Matchers.lessThan(5000L)));
    }

    @Test
    void testTimeoutMulti() {
        long now = System.currentTimeMillis();
        CompletionException exc = assertThrows(CompletionException.class,
                                               () -> Timeout.create(Duration.ofMillis(10)).invokeMulti(Multi::never)
                                                       .forEach(it -> {})
                                                       .await(5, TimeUnit.SECONDS));
        long time = System.currentTimeMillis()- now;
        Throwable cause = exc.getCause();
        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(TimeoutException.class));
        assertThat("Should have timed out in FT, not in await in test", time, is(Matchers.lessThan(5000L)));
    }
}