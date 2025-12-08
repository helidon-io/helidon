/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.concurrency.limits.LimitAlgorithm.Token;

class LimitHandlers {

    private LimitHandlers() {
    }

    @SuppressWarnings("removal")
    interface LimiterHandler extends SemaphoreLimit {
        Optional<Token> tryAcquireToken(boolean wait);
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
        public Optional<Token> tryAcquireToken(boolean wait) {
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
        private final long maxWaitMillis;
        private final Optional<Runnable> beforeAcquire;

        QueuedSemaphoreHandler(Semaphore semaphore,
                               int queueLength,
                               Duration queueTimeout,
                               Supplier<Token> tokenSupplier) {
            this(semaphore, queueLength, queueTimeout, tokenSupplier, 0, null);
        }

        QueuedSemaphoreHandler(Semaphore semaphore,
                               int queueLength,
                               Duration queueTimeout,
                               Supplier<Token> tokenSupplier,
                               long maxWaitMillis,
                               Runnable beforeAcquire) {
            this.semaphore = semaphore;
            this.queueLength = queueLength;
            this.timeoutMillis = queueTimeout.toMillis();
            this.tokenSupplier = tokenSupplier;
            this.maxWaitMillis = maxWaitMillis;
            this.beforeAcquire = Optional.ofNullable(beforeAcquire);
        }

        @Override
        public Optional<Token> tryAcquireToken(boolean wait) {
            if (queueLength > 0 && semaphore.getQueueLength() >= queueLength) {
                // this is an estimate - we do not promise to be precise here
                return Optional.empty();
            }

            try {
                return wait ? tryAcquireWithWait() : tryAcquireWithoutWait();
            } catch (InterruptedException e) {
                return Optional.empty();
            }
        }

        private Optional<Token> tryAcquireWithWait() throws InterruptedException {
            long remainingWaitMillis = timeoutMillis;
            long waitMillis = maxWaitMillis > 0 ? Math.min(maxWaitMillis, timeoutMillis) : timeoutMillis;
            do {
                long actualWaitMillis = Math.min(waitMillis, remainingWaitMillis);
                beforeAcquire.ifPresent(Runnable::run);
                if (semaphore.tryAcquire(actualWaitMillis, TimeUnit.MILLISECONDS)) {
                    return Optional.of(tokenSupplier.get());
                }
                remainingWaitMillis -= actualWaitMillis;
            } while (remainingWaitMillis > 0);
            return Optional.empty();
        }

        private Optional<Token> tryAcquireWithoutWait() {
            beforeAcquire.ifPresent(Runnable::run);
            return semaphore.tryAcquire() ? Optional.of(tokenSupplier.get()) : Optional.empty();
        }

        @Override
        public Semaphore semaphore() {
            return semaphore;
        }
    }
}
