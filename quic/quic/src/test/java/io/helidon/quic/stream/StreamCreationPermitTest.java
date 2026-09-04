/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.quic.stream;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamCreationPermitTest {
    @Test
    void noTimeoutExecutorRejectionWhileRegisteringFailsWaiter() {
        StreamCreationPermit permit = new StreamCreationPermit(0);
        RejectedExecutionException rejection = new RejectedExecutionException("test rejection");

        CompletableFuture<Boolean> acquisition = permit.tryAcquire(command -> {
            throw rejection;
        });

        CompletionException thrown = assertThrows(CompletionException.class, acquisition::join);
        assertThat(thrown.getCause(), sameInstance(rejection));
        permit.tryIncreaseLimitTo(1);
        assertThat(permit.tryAcquire(), is(true));
    }

    @Test
    void cancellationAfterAcquisitionBeforeCompletionReleasesPermit() {
        StreamCreationPermit permit = new StreamCreationPermit(0);
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        CompletableFuture<Boolean> acquisition = permit.tryAcquire(tasks::addLast);

        assertThat(tasks.size(), is(1));
        permit.tryIncreaseLimitTo(1);
        tasks.removeFirst().run();
        assertThat(tasks.size(), is(1));

        assertThat(acquisition.cancel(false), is(true));
        tasks.removeFirst().run();

        assertThat(permit.tryAcquire(), is(true));
    }

    @Test
    void executorRejectionWhileRegisteringFailsWaiter() {
        StreamCreationPermit permit = new StreamCreationPermit(0);
        RejectedExecutionException rejection = new RejectedExecutionException("test rejection");

        CompletableFuture<Boolean> acquisition = permit.tryAcquire(1, TimeUnit.DAYS, command -> {
            throw rejection;
        });

        CompletionException thrown = assertThrows(CompletionException.class, acquisition::join);
        assertThat(thrown.getCause(), sameInstance(rejection));
        permit.tryIncreaseLimitTo(1);
        assertThat(permit.tryAcquire(), is(true));
    }

    @Test
    void executorRejectionFailsWaiterAndReleasesAcquiredPermit() {
        StreamCreationPermit permit = new StreamCreationPermit(0);
        AtomicBoolean reject = new AtomicBoolean();
        RejectedExecutionException rejection = new RejectedExecutionException("test rejection");
        Executor executor = command -> {
            if (reject.get()) {
                throw rejection;
            }
            command.run();
        };
        CompletableFuture<Boolean> acquisition = permit.tryAcquire(1, TimeUnit.DAYS, executor);
        reject.set(true);

        permit.tryIncreaseLimitTo(1);

        CompletionException thrown = assertThrows(CompletionException.class, acquisition::join);
        assertThat(thrown.getCause(), sameInstance(rejection));
        assertThat(permit.tryAcquire(), is(true));
    }

    @Test
    void terminationFailsPendingWaiterAndSealsPermit() {
        StreamCreationPermit permit = new StreamCreationPermit(0);
        IllegalStateException termination = new IllegalStateException("test termination");
        CompletableFuture<Boolean> acquisition = permit.tryAcquire(1, TimeUnit.DAYS, Runnable::run);

        permit.terminate(termination);

        CompletionException thrown = assertThrows(CompletionException.class, acquisition::join);
        assertThat(thrown.getCause(), sameInstance(termination));
        assertThat(permit.tryIncreaseLimitTo(1), is(false));
        assertThat(permit.tryAcquire(), is(false));
        CompletableFuture<Boolean> lateAcquisition = permit.tryAcquire(1, TimeUnit.DAYS, Runnable::run);
        CompletionException lateThrown = assertThrows(CompletionException.class, lateAcquisition::join);
        assertThat(lateThrown.getCause(), sameInstance(termination));
    }
}
