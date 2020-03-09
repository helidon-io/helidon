/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.concurrent.Executors;
import java.util.function.Supplier;

import io.helidon.config.spi.RetryPolicy;

/**
 * Class provides access to built-in {@link io.helidon.config.spi.RetryPolicy} implementations.
 *
 * @see io.helidon.config.spi.RetryPolicy
 */
public class RetryPolicies {

    private RetryPolicies() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Creates a new instance of {@link io.helidon.config.SimpleRetryPolicy.Builder} class with a number of retries
     * as a parameter.
     * <p>
     * The default values are:
     * <ul>
     * <li>delay: 200ms</li>
     * <li>delayFactor: 2.0</li>
     * <li>callTimeout: 500ms</li>
     * <li>overallTimeout: 2s</li>
     * <li>executor: {@link Executors#newSingleThreadScheduledExecutor single-threaded scheduled executor}</li>
     * </ul>
     * <p>
     * The default {@link RetryPolicy} is {@link #justCall()}.
     *
     * @param retries a number of retries, excluding the first call
     * @return a new builder
     */
    public static SimpleRetryPolicy.Builder repeat(int retries) {
        return SimpleRetryPolicy.builder().retries(retries);
    }

    /**
     * An implementation that invokes the requested method just once, without any execute.
     *
     * @return a default execute policy
     */
    public static RetryPolicy justCall() {
        return JustCallRetryPolicyHolder.JUST_CALL;
    }

    private static final class JustCallRetryPolicyHolder {
        private JustCallRetryPolicyHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }

        private static final RetryPolicy JUST_CALL = new RetryPolicy() {
            @Override
            public <T> T execute(Supplier<T> call) {
                return call.get();
            }
        };
    }
}
