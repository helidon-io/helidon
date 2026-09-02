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

final class RetryConfigBuilderDecorator
        implements Prototype.BuilderDecorator<RetryConfig.BuilderBase<?, ?>> {
    @Override
    public void decorate(RetryConfig.BuilderBase<?, ?> target) {
        validate(target.delay(), target.maxAttempts());
    }

    static void validate(Duration delay, int maxAttempts) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            throw new IllegalArgumentException("failure.retry.delay must be greater than zero");
        }
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("failure.retry.max-attempts must be zero or greater");
        }
    }
}
