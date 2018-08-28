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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class DetachedValve<T> implements Valve<T> {

    private static final int INTERNAL_INDEX = 0;
    private static final int EXTERNAL_INDEX = 1;

    private final boolean[] paused = new boolean[] {false, false};

    private final Lock lock = new ReentrantLock();
    private final Valve<T> delegate;
    private final ExecutorService executorService;

    DetachedValve(Valve<T> delegate, ExecutorService executorService) {
        this.delegate = delegate;
        this.executorService = executorService;
    }

    @Override
    public void handle(BiConsumer<T, Pausable> onData, Consumer<Throwable> onError, Runnable onComplete) {
        delegate.handle((t, p) -> {
                            pause(INTERNAL_INDEX);
                            CompletableFuture.runAsync(() -> onData.accept(t, this), executorService)
                                             .whenComplete((vd, thr) -> {
                                                 if (thr == null) {
                                                     resume(INTERNAL_INDEX);
                                                 } else {
                                                     executorService.submit(() -> onError.accept(thr));
                                                 }
                                             });
                        },
                        t -> executorService.submit(() -> onError.accept(t)),
                        () -> executorService.submit(onComplete));
    }

    private void pause(int index) {
        lock.lock();
        try {
            boolean callIt = !paused[0] && !paused[1];
            paused[index] = true;
            if (callIt) {
                delegate.pause();
            }
        } finally {
            lock.unlock();
        }
    }

    private void resume(int index) {
        lock.lock();
        try {
            boolean callIt = paused[index] && !paused[index == 0 ? 1 : 0];
            paused[index] = false;
            if (callIt) {
                delegate.resume();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void pause() {
        pause(EXTERNAL_INDEX);
    }

    @Override
    public void resume() {
        resume(EXTERNAL_INDEX);
    }
}
