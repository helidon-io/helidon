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

package io.helidon.messaging;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

final class AdmissionTimeoutBudget {
    private static final long NO_DEADLINE = Long.MIN_VALUE;

    private final String channel;
    private final LongSupplier nanoTime;
    private final AtomicLong deadline = new AtomicLong(NO_DEADLINE);

    AdmissionTimeoutBudget(String channel, LongSupplier nanoTime) {
        this.channel = channel;
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    <T> Optional<T> attempt(LongSupplier timeoutSupplier, LongFunction<Optional<T>> operation) {
        long started = nanoTime.getAsLong();
        long timeout = timeoutSupplier.getAsLong();
        long remaining = timeout;
        long currentDeadline;
        while (true) {
            currentDeadline = deadline.get();
            if (currentDeadline == NO_DEADLINE) {
                remaining = timeout;
                break;
            }
            long now = nanoTime.getAsLong();
            if (deadline.get() != currentDeadline) {
                continue;
            }
            remaining = currentDeadline - now;
            if (remaining <= 0) {
                throw timedOut();
            }
            break;
        }

        Optional<T> result = operation.apply(remaining);
        if (result.isPresent()) {
            reset();
        } else if (currentDeadline == NO_DEADLINE) {
            startOrRejectExpired(started, timeout);
        }
        return result;
    }

    void reset() {
        deadline.set(NO_DEADLINE);
    }

    private void startOrRejectExpired(long started, long timeout) {
        long currentDeadline;
        while ((currentDeadline = deadline.get()) == NO_DEADLINE) {
            if (timeout == Long.MAX_VALUE) {
                return;
            }
            long newDeadline = saturatedAdd(started, timeout);
            if (deadline.compareAndSet(NO_DEADLINE, newDeadline)) {
                currentDeadline = newDeadline;
                break;
            }
        }
        rejectIfExpired(currentDeadline, nanoTime.getAsLong());
    }

    private void rejectIfExpired(long currentDeadline, long now) {
        if (currentDeadline - now <= 0 && deadline.get() == currentDeadline) {
            throw timedOut();
        }
    }

    private static long saturatedAdd(long first, long second) {
        long result = first + second;
        return ((first ^ result) & (second ^ result)) < 0 ? Long.MAX_VALUE : result;
    }

    private MessagingRejectedException timedOut() {
        return new MessagingRejectedException(
                channel,
                MessagingRejectedException.Reason.TIMEOUT,
                "Messaging delivery reservation timed out on channel " + channel);
    }
}
