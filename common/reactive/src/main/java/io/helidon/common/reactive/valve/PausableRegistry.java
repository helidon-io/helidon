/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Supports {@link Valve} implementation by providing single handler registry
 * and pause / resume functionality with facility tryProcess reservation.
 */
abstract class PausableRegistry<T> implements Pausable {

    private static final Logger LOGGER = Logger.getLogger(PausableRegistry.class.getName());

    private final ReentrantLock lock = new ReentrantLock();

    private volatile BiConsumer<T, Pausable> onData;
    private volatile Consumer<Throwable> onError;
    private volatile Runnable onComplete;

    private volatile boolean paused = false;
    private boolean processing = false;

    @Override
    public void pause() {
        paused = true;
    }

    @Override
    public void resume() {
        paused = false;
        tryProcess();
    }

    /**
     * Implements item handling / processing. Implementation can use {@link #canProcess()} and {@link #canContinueProcessing()}
     * method to ensure, that processing is done by a single thread at a time.
     */
    protected abstract void tryProcess();

    public void handle(BiConsumer<T, Pausable> onData, Consumer<Throwable> onError, Runnable onComplete) {
        Objects.requireNonNull(onData, "Parameter onData is null!");
        synchronized (this) {
            if (this.onData != null) {
                throw new IllegalStateException("Handler is already registered!");
            }
            this.onData = onData;
            this.onError = onError;
            this.onComplete = onComplete;
        }
        resume();
    }

    /**
     * Implementation of {@link #tryProcess()} method should call this to reserve initial handle processing (if possible).
     * The same method should call {@link #canContinueProcessing()} before every iteration to be sure, that handle processing
     * should continue.
     *
     * @return {@code true} only if method can process (handle) item
     */
    protected boolean canProcess() {
        if (onData == null) {
            return false;
        }
        lock.lock();
        try {
            if (paused || processing) {
                return false;
            } else {
                processing = true;
                return true;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Implementation of {@link #tryProcess()} which initially was accepted by {@link #canProcess()} should call this method
     * before every iteration to be sure, that processing can continue (is not paused).
     *
     * @return {@code true} only if method can continue with handle processing
     */
    protected boolean canContinueProcessing() {
        if (paused) {
            lock.lock();
            try {
                processing = false;
            } finally {
                lock.unlock();
            }
            return false;
        } else {
            return true;
        }
    }

    protected boolean paused() {
        return paused;
    }

    protected void releaseProcessing() {
        lock.lock();
        try {
            processing = false;
        } finally {
            lock.unlock();
        }
    }

    protected void handleError(Throwable thr) {
        if (onError != null) {
            onError.accept(thr);
        } else {
            LOGGER.log(Level.WARNING, "Unhandled throwable!", thr);
        }
    }

    protected BiConsumer<T, Pausable> getOnData() {
        return onData;
    }

    protected Consumer<Throwable> getOnError() {
        return onError;
    }

    protected Runnable getOnComplete() {
        return onComplete;
    }
}
