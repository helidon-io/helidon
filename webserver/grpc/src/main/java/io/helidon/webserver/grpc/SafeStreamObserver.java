/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

/**
 * A {@link io.grpc.stub.StreamObserver} that handles exceptions correctly.
 *
 * @param <T> the type of response expected
 */
class SafeStreamObserver<T> implements StreamObserver<T> {

    private static final System.Logger LOGGER = System.getLogger(SafeStreamObserver.class.getName());
    /**
     * The actual StreamObserver.
     */
    private final StreamObserver<? super T> delegate;
    /**
     * Indicates a terminal state.
     */
    private boolean done;

    /**
     * Create a {@link SafeStreamObserver} that wraps
     * another {@link io.grpc.stub.StreamObserver}.
     *
     * @param streamObserver the {@link io.grpc.stub.StreamObserver} to wrap
     */
    private SafeStreamObserver(StreamObserver<? super T> streamObserver) {
        delegate = streamObserver;
    }

    /**
     * Ensure that the specified {@link io.grpc.stub.StreamObserver} is a safe observer.
     * <p>
     * If the specified observer is not an instance of {@link SafeStreamObserver} then wrap
     * it in a {@link SafeStreamObserver}.
     *
     * @param observer the {@link io.grpc.stub.StreamObserver} to test
     * @param <T>      the response type expected by the observer
     * @return a safe {@link io.grpc.stub.StreamObserver}
     */
    public static <T> StreamObserver<T> ensureSafeObserver(StreamObserver<T> observer) {
        if (observer instanceof SafeStreamObserver) {
            return observer;
        }

        return new SafeStreamObserver<>(observer);
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
                LOGGER.log(ERROR, "OnError called after StreamObserver was closed", checkNotNull(thrown));
            } else {
                done = true;
                delegate.onError(checkNotNull(thrown));
            }
        } catch (Throwable t) {
            throwIfFatal(t);
            LOGGER.log(ERROR, "Caught exception handling onError", t);
        }
    }

    @Override
    public void onCompleted() {
        if (done) {
            LOGGER.log(WARNING, "onComplete called after StreamObserver was closed");
        } else {
            try {
                done = true;
                delegate.onCompleted();
            } catch (Throwable thrown) {
                throwIfFatal(thrown);
                LOGGER.log(ERROR, "Caught exception handling onComplete", thrown);
            }
        }
    }

    // ----- constants ------------------------------------------------------

    /**
     * Obtain the wrapped {@link io.grpc.stub.StreamObserver}.
     *
     * @return the wrapped {@link io.grpc.stub.StreamObserver}
     */
    public StreamObserver<? super T> delegate() {
        return delegate;
    }

    // ----- data members ---------------------------------------------------

    /**
     * Throws a particular {@code Throwable} only if it belongs to a set of "fatal" error varieties.
     * These varieties are as follows:
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

    private Throwable checkNotNull(Throwable thrown) {
        if (thrown == null) {
            thrown = Status.INVALID_ARGUMENT
                    .withDescription("onError called with null Throwable. Null exceptions are generally not allowed.")
                    .asRuntimeException();
        }

        return thrown;
    }
}
