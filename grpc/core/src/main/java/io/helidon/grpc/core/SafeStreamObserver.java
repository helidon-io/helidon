/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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

package io.helidon.grpc.core;

import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * A {@link StreamObserver} that handles exceptions correctly.
 *
 * @param <T> the type of response expected
 */
public class SafeStreamObserver<T> implements StreamObserver<T> {

    /**
     * Create a {@link SafeStreamObserver} that wraps
     * another {@link StreamObserver}.
     *
     * @param streamObserver  the {@link StreamObserver} to wrap
     */
    private SafeStreamObserver(StreamObserver<? super T> streamObserver) {
        delegate = streamObserver;
    }

    @Override
    public void onNext(T t) {
        if (done) {
            return;
        }

        if (t == null) {
            onError(Status.INVALID_ARGUMENT
                            .withDescription("onNext called with null. Null values are generally not allowed.")
                            .asRuntimeException());
        } else {
            try {
                delegate.onNext(t);
            } catch (Throwable thrown) {
                throwIfFatal(thrown);
                onError(thrown);
            }
        }
    }

    @Override
    public void onError(Throwable thrown) {
        try {
            if (done) {
                LOGGER.log(Level.TRACE, () -> "OnError called after StreamObserver was closed");
            } else {
                done = true;
                delegate.onError(checkNotNull(thrown));
            }
        } catch (UncheckedIOException e) {
            LOGGER.log(Level.TRACE, () -> "Caught I/O exception handling onError", e);
        } catch (Throwable t) {
            throwIfFatal(t);
            LOGGER.log(Level.ERROR, () -> "Caught exception handling onError", t);
        }
    }

    @Override
    public void onCompleted() {
        if (done) {
            LOGGER.log(Level.TRACE, "onComplete called after StreamObserver was closed");
        } else {
            try {
                done = true;
                delegate.onCompleted();
            } catch (UncheckedIOException e) {
                LOGGER.log(Level.TRACE, () -> "Caught I/O exception handling onComplete", e);
            } catch (Throwable thrown) {
                throwIfFatal(thrown);
                LOGGER.log(Level.ERROR, () -> "Caught exception handling onComplete", thrown);
            }
        }
    }

    /**
     * Obtain the wrapped {@link StreamObserver}.
     * @return  the wrapped {@link StreamObserver}
     */
    public StreamObserver<? super T> delegate() {
        return delegate;
    }

    private Throwable checkNotNull(Throwable thrown) {
        if (thrown == null) {
            thrown = Status.INVALID_ARGUMENT
                    .withDescription("onError called with null Throwable. Null exceptions are generally not allowed.")
                    .asRuntimeException();
        }

        return thrown;
    }

    /**
     * Throws a particular {@code Throwable} only if it belongs to a set of "fatal" error varieties. These varieties are
     * as follows:
     * <ul>
     * <li>{@code VirtualMachineError}</li>
     * <li>{@code ThreadDeath}</li>
     * <li>{@code LinkageError}</li>
     * </ul>
     *
     * @param thrown the {@code Throwable} to test and perhaps throw
     */
    private static void throwIfFatal(Throwable thrown) {
        if (thrown instanceof VirtualMachineError) {
            throw (VirtualMachineError) thrown;
        } else if (thrown instanceof ThreadDeath) {
            throw (ThreadDeath) thrown;
        } else if (thrown instanceof LinkageError) {
            throw (LinkageError) thrown;
        }
    }

    /**
     * Ensure that the specified {@link StreamObserver} is a safe observer.
     * <p>
     * If the specified observer is not an instance of {@link SafeStreamObserver} then wrap
     * it in a {@link SafeStreamObserver}.
     *
     * @param observer  the {@link StreamObserver} to test
     * @param <T>       the response type expected by the observer
     *
     * @return a safe {@link StreamObserver}
     */
    public static <T> StreamObserver<T> ensureSafeObserver(StreamObserver<T> observer) {
        if (observer instanceof SafeStreamObserver) {
            return observer;
        }

        return new SafeStreamObserver<>(observer);
    }

    // ----- constants ------------------------------------------------------

    /**
     * The {2link Logger} to use.
     */
    private static final System.Logger LOGGER = System.getLogger(SafeStreamObserver.class.getName());

    // ----- data members ---------------------------------------------------

    /**
     * The actual StreamObserver.
     */
    private final StreamObserver<? super T> delegate;

    /**
     * Indicates a terminal state.
     */
    private boolean done;
}
