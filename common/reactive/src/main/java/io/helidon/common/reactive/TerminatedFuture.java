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

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enumeration that implements a finished or canceled future to be used
 * for reference comparison and atomic state transitions.
 */
enum TerminatedFuture implements Future<Object> {

    /** The task has already finished normally. */
    FINISHED,

    /** The task has been cancelled. */
    CANCELED;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return this == CANCELED;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public Object get() {
        if (this == CANCELED) {
            throw new CancellationException();
        }
        return null;
    }

    @Override
    public Object get(long timeout, TimeUnit unit) {
        if (this == CANCELED) {
            throw new CancellationException();
        }
        return null;
    }

    /**
     * Atomically set a {@link Future} on the given field if that field doesn't
     * already hold the {@link #CANCELED} or {@link #FINISHED} instance.
     * @param field the target atomic reference field
     * @param f the future to set
     */
    public static void setFuture(AtomicReference<Future<?>> field, Future<?> f) {
        for (;;) {
            Future<?> current = field.get();
            if (current == TerminatedFuture.CANCELED) {
                f.cancel(true);
                return;
            }
            if (current != null) {
                return;
            }
            if (field.compareAndSet(null, f)) {
                return;
            }
        }
    }

    /**
     * Atomically cancel the future in the target field or mark it for
     * cancellation.
     * @param field the target atomic reference field
     */
    public static void cancel(AtomicReference<Future<?>> field) {
        for (;;) {
            Future<?> current = field.get();
            if (current == TerminatedFuture.FINISHED || current == TerminatedFuture.CANCELED) {
                return;
            }
            if (field.compareAndSet(current, TerminatedFuture.CANCELED)) {
                if (current != null) {
                    current.cancel(true);
                }
                return;
            }
        }
    }
}
