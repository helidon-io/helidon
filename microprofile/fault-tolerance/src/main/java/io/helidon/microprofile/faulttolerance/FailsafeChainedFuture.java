/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.jodah.failsafe.FailsafeFuture;

/**
 * A future whose delegate is a {@code FailsafeFuture} whose delegate, in
 * turn, is a future returned by a bean's method or the value returned
 * by a fallback method. Calling a getter will undo the chain of futures
 * (if necessary) and return the final value.
 */
class FailsafeChainedFuture<T> implements Future<T> {

    private final FailsafeFuture<T> delegate;

    FailsafeChainedFuture(FailsafeFuture<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() throws CancellationException, InterruptedException, ExecutionException {
        try {
            final T result = delegate.get();
            if (result instanceof Future) {
                return ((Future<T>) result).get();
            }
            return result;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CancellationException) {
                throw (CancellationException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(long timeout, TimeUnit unit) throws CancellationException, InterruptedException,
            ExecutionException, TimeoutException {
        try {
            final T result = delegate.get(timeout, unit);
            if (result instanceof Future) {
                return ((Future<T>) result).get(timeout, unit);
            }
            return result;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CancellationException) {
                throw (CancellationException) e.getCause();
            }
            throw e;
        }
    }
}
