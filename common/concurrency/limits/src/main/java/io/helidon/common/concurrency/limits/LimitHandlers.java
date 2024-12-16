/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.common.concurrency.limits;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

class LimitHandlers {

    private LimitHandlers() {
    }

    @SuppressWarnings("removal")
    interface LimiterHandler extends SemaphoreLimit, LimitAlgorithm {
    }

    static class NoOpSemaphoreHandler implements LimiterHandler {
        private static final Token TOKEN = new Token() {
            @Override
            public void dropped() {
            }

            @Override
            public void ignore() {
            }

            @Override
            public void success() {
            }
        };

        @Override
        public <T> T invoke(Callable<T> callable) throws Exception {
            try {
                return callable.call();
            } catch (IgnoreTaskException e) {
                return e.handle();
            }
        }

        @Override
        public void invoke(Runnable runnable) {
            runnable.run();
        }

        @Override
        public Optional<Token> tryAcquire(boolean wait) {
            return Optional.of(TOKEN);
        }

        @SuppressWarnings("removal")
        @Override
        public Semaphore semaphore() {
            return NoopSemaphore.INSTANCE;
        }
    }

    static class QueuedSemaphoreHandler implements LimiterHandler {
        private final Semaphore semaphore;
        private final int queueLength;
        private final long timeoutMillis;
        private final Supplier<Token> tokenSupplier;

        QueuedSemaphoreHandler(Semaphore semaphore, int queueLength, Duration queueTimeout) {
            this.semaphore = semaphore;
            this.queueLength = queueLength;
            this.timeoutMillis = queueTimeout.toMillis();
            this.tokenSupplier = () -> new SemaphoreToken(semaphore);
        }

        QueuedSemaphoreHandler(Semaphore semaphore, int queueLength, Duration queueTimeout, Supplier<Token> tokenSupplier) {
            this.semaphore = semaphore;
            this.queueLength = queueLength;
            this.timeoutMillis = queueTimeout.toMillis();
            this.tokenSupplier = tokenSupplier;
        }

        @Override
        public Optional<Token> tryAcquire(boolean wait) {
            if (queueLength > 0 && semaphore.getQueueLength() >= queueLength) {
                // this is an estimate - we do not promise to be precise here
                return Optional.empty();
            }

            try {
                if (wait) {
                    if (!semaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
                        return Optional.empty();
                    }
                } else if (!semaphore.tryAcquire()) {
                    return Optional.empty();
                }
            } catch (InterruptedException e) {
                return Optional.empty();
            }
            return Optional.of(tokenSupplier.get());
        }

        @Override
        public Semaphore semaphore() {
            return semaphore;
        }
    }

    static class SemaphoreToken implements LimitAlgorithm.Token {
        private final Semaphore semaphore;

        SemaphoreToken(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void dropped() {
            semaphore.release();
        }

        @Override
        public void ignore() {
            semaphore.release();
        }

        @Override
        public void success() {
            semaphore.release();
        }
    }
}
