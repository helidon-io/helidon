/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.common.reactive;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;


public class TerminatedFutureTest {

    @Test
    public void nullToCancel() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();

        TerminatedFuture.cancel(ref);

        assertThat(ref.get(), sameInstance(TerminatedFuture.CANCELED));

        TerminatedFuture.cancel(ref);

        assertThat(ref.get(), sameInstance(TerminatedFuture.CANCELED));
    }

    @Test
    public void someToCancel() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();

        CompletableFuture<Integer> cf = new CompletableFuture<>();
        ref.set(cf);

        TerminatedFuture.cancel(ref);

        assertThat(ref.get(), sameInstance(TerminatedFuture.CANCELED));

        assertThat(cf.isCancelled(), is(true));
    }

    @Test
    public void nullToSome() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();

        CompletableFuture<Integer> cf = new CompletableFuture<>();

        TerminatedFuture.setFuture(ref, cf);

        assertThat(ref.get(), sameInstance(cf));
    }

    @Test
    public void finishToCancel() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();
        ref.set(TerminatedFuture.FINISHED);

        TerminatedFuture.cancel(ref);

        assertThat(ref.get(), sameInstance(TerminatedFuture.FINISHED));
    }

    @Test
    public void someToSome() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();

        CompletableFuture<Integer> cf = new CompletableFuture<>();
        CompletableFuture<Integer> cf1 = new CompletableFuture<>();

        TerminatedFuture.setFuture(ref, cf);

        assertThat(ref.get(), sameInstance(cf));

        TerminatedFuture.setFuture(ref, cf1);

        assertThat(ref.get(), sameInstance(cf));
    }

    @Test
    public void someWhenFinished() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();
        ref.set(TerminatedFuture.FINISHED);

        CompletableFuture<Integer> cf = new CompletableFuture<>();

        TerminatedFuture.setFuture(ref, cf);

        assertThat(ref.get(), sameInstance(TerminatedFuture.FINISHED));
    }


    @Test
    public void someWhenCanceled() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();
        ref.set(TerminatedFuture.CANCELED);

        CompletableFuture<Integer> cf = new CompletableFuture<>();

        TerminatedFuture.setFuture(ref, cf);

        assertThat(ref.get(), sameInstance(TerminatedFuture.CANCELED));

        assertThat(cf.isCancelled(), is(true));
    }

    @Test
    public void finishedMethods() {
        assertThat(TerminatedFuture.FINISHED.cancel(true), is(false));

        assertThat(TerminatedFuture.FINISHED.isCancelled(), is(false));

        assertThat(TerminatedFuture.FINISHED.isDone(), is(true));

        assertThat(TerminatedFuture.FINISHED.get(), nullValue());

        assertThat(TerminatedFuture.FINISHED.get(1, TimeUnit.MINUTES), nullValue());
    }

    @Test
    public void canceledMethods() {
        assertThat(TerminatedFuture.CANCELED.cancel(true), is(false));

        assertThat(TerminatedFuture.CANCELED.isCancelled(), is(true));

        assertThat(TerminatedFuture.CANCELED.isDone(), is(true));
    }

    @Test
    public void canceledGet() {
        assertThrows(CancellationException.class, TerminatedFuture.CANCELED::get);
    }

    @Test
    public void canceledGetTimeout() {
        assertThrows(CancellationException.class, () -> TerminatedFuture.CANCELED.get(1, TimeUnit.MINUTES));
    }
}
