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

package io.helidon.nima.faulttolerance;

import io.helidon.builder.api.Prototype;

class RetryConfigInterceptor implements Prototype.BuilderInterceptor<RetryConfig.BuilderBase<?, ?>> {
    @Override
    public RetryConfig.BuilderBase<?, ?> intercept(RetryConfig.BuilderBase<?, ?> target) {
        if (target.name().isEmpty()) {
            target.config()
                    .ifPresent(cfg -> target.name(cfg.name()));
        }
        if (target.retryPolicy().isEmpty()) {
            target.retryPolicy(retryPolicy(target));
        }
        return target;
    }

    /**
     * Retry policy created from this configuration.
     *
     * @return retry policy to use
     */
    private Retry.RetryPolicy retryPolicy(RetryConfig.BuilderBase<?, ?> target) {
        if (target.jitter().toSeconds() == -1) {
            Retry.DelayingRetryPolicy.Builder delayBuilder = Retry.DelayingRetryPolicy.builder()
                    .calls(target.calls())
                    .delay(target.delay());

            if (target.delayFactor() != -1) {
                delayBuilder.delayFactor(target.delayFactor());
            }
            return delayBuilder.build();
        }
        if (target.delayFactor() != -1) {
            return Retry.DelayingRetryPolicy.builder()
                    .calls(target.calls())
                    .delayFactor(target.delayFactor())
                    .delay(target.delay())
                    .build();
        }
        return Retry.JitterRetryPolicy.builder()
                .calls(target.calls())
                .delay(target.delay())
                .jitter(target.jitter())
                .build();
    }
}
