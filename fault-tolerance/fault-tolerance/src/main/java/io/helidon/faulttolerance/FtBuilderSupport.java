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

package io.helidon.faulttolerance;

import java.time.Duration;

import io.helidon.builder.api.Prototype;

final class FtBuilderSupport {
    private FtBuilderSupport() {
    }

    static class AsyncBuilderDecorator implements Prototype.BuilderDecorator<AsyncConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(AsyncConfig.BuilderBase<?, ?> target) {
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
        }
    }

    static class BulkheadBuilderDecorator implements Prototype.BuilderDecorator<BulkheadConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(BulkheadConfig.BuilderBase<?, ?> target) {
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
        }
    }

    static class CircuitBreakerBuilderDecorator
            implements Prototype.BuilderDecorator<CircuitBreakerConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(CircuitBreakerConfig.BuilderBase<?, ?> target) {
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
        }
    }

    static class RetryBuilderDecorator implements Prototype.BuilderDecorator<RetryConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(RetryConfig.BuilderBase<?, ?> target) {
            validate(target);
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
            if (target.retryPolicy().isEmpty()) {
                target.retryPolicy(retryPolicy(target));
            }
        }

        /**
         * Retry policy created from this configuration.
         *
         * @return retry policy to use
         */
        private Retry.RetryPolicy retryPolicy(RetryConfig.BuilderBase<?, ?> target) {
            boolean jitterConfigured = !target.jitter().equals(Duration.ofSeconds(-1));
            boolean jitterFactorConfigured = target.jitterFactor() != -1;
            Retry.DelayingRetryPolicy.Builder delayBuilder = Retry.DelayingRetryPolicy.builder()
                    .calls(target.calls())
                    .delay(target.delay());

            if (target.delayFactor() != -1) {
                delayBuilder.delayFactor(target.delayFactor());
            } else if (jitterConfigured || jitterFactorConfigured) {
                delayBuilder.delayFactor(1);
            }
            if (jitterConfigured) {
                delayBuilder.jitter(target.jitter());
            }
            if (jitterFactorConfigured) {
                delayBuilder.jitterFactor(target.jitterFactor());
            }
            target.maxDelay().ifPresent(delayBuilder::maxDelay);
            return delayBuilder.build();
        }

        private void validate(RetryConfig.BuilderBase<?, ?> target) {
            if (target.calls() < 1) {
                throw new IllegalArgumentException("Retry calls must be at least 1");
            }
            if (target.delay().isNegative()) {
                throw new IllegalArgumentException("Retry delay must not be negative");
            }
            if (target.overallTimeout().isNegative() || target.overallTimeout().isZero()) {
                throw new IllegalArgumentException("Retry overall timeout must be positive");
            }
            double delayFactor = target.delayFactor();
            if (delayFactor != -1 && (!Double.isFinite(delayFactor) || delayFactor < 0)) {
                throw new IllegalArgumentException("Retry delay factor must be -1 or a finite, non-negative number");
            }
            Duration jitter = target.jitter();
            if (jitter.isNegative() && !jitter.equals(Duration.ofSeconds(-1))) {
                throw new IllegalArgumentException("Retry jitter must be PT-1S or non-negative");
            }
            double jitterFactor = target.jitterFactor();
            if (jitterFactor != -1 && (!Double.isFinite(jitterFactor) || jitterFactor < 0 || jitterFactor >= 1)) {
                throw new IllegalArgumentException("Retry jitter factor must be -1 or from 0 (inclusive) to 1 "
                                                           + "(exclusive)");
            }
            if (!jitter.equals(Duration.ofSeconds(-1)) && jitterFactor != -1) {
                throw new IllegalArgumentException("Retry jitter and jitter factor cannot both be configured");
            }
            target.maxDelay().ifPresent(maxDelay -> {
                if (maxDelay.isNegative()) {
                    throw new IllegalArgumentException("Retry maximum delay must not be negative");
                }
            });
        }
    }

    static class TimeoutBuilderDecorator implements Prototype.BuilderDecorator<TimeoutConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(TimeoutConfig.BuilderBase<?, ?> target) {
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
        }
    }
}
