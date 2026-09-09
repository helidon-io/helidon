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

import java.time.Duration;

import io.helidon.builder.api.Prototype;

final class MessagingExecutionConfigBuilderDecorator
        implements Prototype.BuilderDecorator<MessagingExecutionConfig.BuilderBase<?, ?>> {
    @Override
    public void decorate(MessagingExecutionConfig.BuilderBase<?, ?> target) {
        if (target.queueCapacity() < 0) {
            throw new IllegalArgumentException("messaging.execution.queue-capacity must be zero or greater");
        }
        if (target.maxPendingAdmissions() <= 0) {
            throw new IllegalArgumentException(
                    "messaging.execution.max-pending-admissions must be greater than zero");
        }
        if (target.maxPendingMessages() <= 0) {
            throw new IllegalArgumentException(
                    "messaging.execution.max-pending-messages must be greater than zero");
        }
        if (target.maxInFlightMessages() <= 0) {
            throw new IllegalArgumentException(
                    "messaging.execution.max-in-flight-messages must be greater than zero");
        }
        target.admissionTimeout().ifPresent(timeout -> requirePositive(timeout,
                                                                       "messaging.execution.admission-timeout"));
        requirePositive(target.shutdownTimeout(), "messaging.execution.shutdown-timeout");
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(name + " must be representable in nanoseconds", e);
        }
    }
}
