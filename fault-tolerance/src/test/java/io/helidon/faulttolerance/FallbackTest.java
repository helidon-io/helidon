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

import java.util.Arrays;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.reactive.Single;
import io.helidon.config.ConfigException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class FallbackTest {
    private final AtomicInteger primaryCounter = new AtomicInteger();
    private final AtomicInteger fallbackCounter = new AtomicInteger();

    @BeforeEach
    void reset() {
        primaryCounter.set(0);
        fallbackCounter.set(0);
    }

    @Test
    void testFallback() {
        String result = FaultTolerance.fallback(this::primary, this::fallback)
                .await(1, TimeUnit.SECONDS);

        assertThat(result, is("fallback"));
        assertThat(primaryCounter.get(), is(1));
        assertThat(fallbackCounter.get(), is(1));
    }

    @Test
    void testFallbackFails() {
        Single<String> result = FaultTolerance.fallback(this::primary, this::fallbackFail);
        ConfigException exception = FaultToleranceTest.completionException(result, ConfigException.class);
        Throwable[] suppressed = exception.getSuppressed();
        assertThat("Should have a suppressed exception: " + Arrays.toString(suppressed), suppressed.length, is(1));
        assertThat(suppressed[0], instanceOf(CompletionException.class));
        Throwable suppressedCause = suppressed[0].getCause();
        assertThat(suppressedCause, instanceOf(IllegalArgumentException.class));
    }

    private Single<String> primary() {
        primaryCounter.incrementAndGet();
        return Single.error(new IllegalArgumentException("Intentional failure"));
    }

    private Single<String> fallback(Throwable throwable) {
        fallbackCounter.incrementAndGet();
        return Single.just("fallback");
    }

    private Single<String> fallbackFail(Throwable throwable) {
        fallbackCounter.incrementAndGet();
        return Single.error(new ConfigException("Intentional failure"));
    }
}