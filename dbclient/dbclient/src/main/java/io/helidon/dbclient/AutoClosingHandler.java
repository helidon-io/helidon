/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A runnable that runs only once.
 * This class supports {@link AutoClosingStream}.
 */
class AutoClosingHandler implements Runnable {

    private final AtomicBoolean invoked = new AtomicBoolean();
    private final Runnable delegate;

    private AutoClosingHandler(Runnable delegate) {
        this.delegate = delegate;
    }

    /**
     * Decorate a {@link Runnable} to only run once.
     *
     * @param runnable runnable to decorate
     * @return decorated runnable
     */
    static Runnable decorate(Runnable runnable) {
        if (runnable instanceof AutoClosingHandler) {
            return runnable;
        }
        return new AutoClosingHandler(runnable);
    }

    @Override
    public void run() {
        if (invoked.compareAndSet(false, true)) {
            delegate.run();
        }
    }
}
