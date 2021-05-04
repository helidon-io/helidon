/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


public class TerminatedFutureTest {

    @Test
    public void nullToCancel() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();

        TerminatedFuture.cancel(ref);

        assertSame(ref.get(), TerminatedFuture.CANCELED);

        TerminatedFuture.cancel(ref);

        assertSame(ref.get(), TerminatedFuture.CANCELED);
    }

    @Test
    public void someToCancel() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();

        CompletableFuture<Integer> cf = new CompletableFuture<>();
        ref.set(cf);

        TerminatedFuture.cancel(ref);

        assertSame(ref.get(), TerminatedFuture.CANCELED);

        assertTrue(cf.isCancelled());
    }

    @Test
    public void nullToSome() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();

        CompletableFuture<Integer> cf = new CompletableFuture<>();

        TerminatedFuture.setFuture(ref, cf);

        assertSame(ref.get(), cf);
    }

    @Test
    public void finishToCancel() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();
        ref.set(TerminatedFuture.FINISHED);

        TerminatedFuture.cancel(ref);

        assertSame(ref.get(), TerminatedFuture.FINISHED);
    }

    @Test
    public void someToSome() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();

        CompletableFuture<Integer> cf = new CompletableFuture<>();
        CompletableFuture<Integer> cf1 = new CompletableFuture<>();

        TerminatedFuture.setFuture(ref, cf);

        assertSame(ref.get(), cf);

        TerminatedFuture.setFuture(ref, cf1);

        assertSame(ref.get(), cf);
    }

    @Test
    public void someWhenFinished() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();
        ref.set(TerminatedFuture.FINISHED);

        CompletableFuture<Integer> cf = new CompletableFuture<>();

        TerminatedFuture.setFuture(ref, cf);

        assertSame(ref.get(), TerminatedFuture.FINISHED);
    }


    @Test
    public void someWhenCanceled() {
        AtomicReference<Future<?>> ref = new AtomicReference<>();
        ref.set(TerminatedFuture.CANCELED);

        CompletableFuture<Integer> cf = new CompletableFuture<>();

        TerminatedFuture.setFuture(ref, cf);

        assertSame(ref.get(), TerminatedFuture.CANCELED);

        assertTrue(cf.isCancelled());
    }

    @Test
    public void finishedMethods() {
        assertFalse(TerminatedFuture.FINISHED.cancel(true));

        assertFalse(TerminatedFuture.FINISHED.isCancelled());

        assertTrue(TerminatedFuture.FINISHED.isDone());

        assertNull(TerminatedFuture.FINISHED.get());

        assertNull(TerminatedFuture.FINISHED.get(1, TimeUnit.MINUTES));
    }

    @Test
    public void canceledMethods() {
        assertFalse(TerminatedFuture.CANCELED.cancel(true));

        assertTrue(TerminatedFuture.CANCELED.isCancelled());

        assertTrue(TerminatedFuture.CANCELED.isDone());
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
