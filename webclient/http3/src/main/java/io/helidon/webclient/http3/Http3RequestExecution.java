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

package io.helidon.webclient.http3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

final class Http3RequestExecution {
    private Http3RequestExecution() {
    }

    enum Commitment {
        NONE,
        COMMITTING,
        HEADERS_DISPATCHED,
        REQUEST_DISPATCHING,
        REQUEST_DISPATCHED
    }

    enum ContinueDecision {
        SEND_BODY,
        RESPONSE_READY
    }

    static final class Task extends FutureTask<Void> {
        private final AtomicBoolean started = new AtomicBoolean();
        private final CompletableFuture<Void> exited = new CompletableFuture<>();
        private volatile Thread runner;

        Task(Runnable task) {
            super(task, null);
        }

        @Override
        public void run() {
            if (!started.compareAndSet(false, true)) {
                return;
            }
            runner = Thread.currentThread();
            try {
                super.run();
            } finally {
                runner = null;
                exited.complete(null);
            }
        }

        @Override
        protected void done() {
            if (!started.get()) {
                exited.complete(null);
            }
        }

        CompletableFuture<Void> exited() {
            return exited;
        }

        boolean runsOnCurrentThread() {
            return runner == Thread.currentThread();
        }

        void cancelAfterClose() {
            cancel(runner != Thread.currentThread());
        }
    }

    static final class BodyInterruptedException extends RuntimeException {
        BodyInterruptedException() {
        }

        BodyInterruptedException(Throwable cause) {
            super(cause);
        }
    }
}
