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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeoutTest {

    @BeforeEach
    void reset() {
    }

    @Test
    void testAsync() {
        CompletionException exc = assertThrows(CompletionException.class,
                                               () -> FaultTolerance.timeout(Duration.ofSeconds(1), this::timeOut)
                                                       .await());

        Throwable cause = exc.getCause();
        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(TimeoutException.class));
    }

    private CompletionStage<String> timeOut() {
        // never completing
        return new CompletableFuture<>();
    }

}