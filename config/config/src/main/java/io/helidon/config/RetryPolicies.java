/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import io.helidon.config.internal.ConfigThreadFactory;
import io.helidon.config.internal.RetryPolicyImpl;
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
     * Creates a new instance of {@link RetryPolicies.Builder} class with a number of retries as a parameter.
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
    public static Builder repeat(int retries) {
        return new Builder(retries);
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

    /**
     * A builder of the default {@link RetryPolicy}.
     */
    public static class Builder implements Supplier<RetryPolicy> {

        private static final String RETRIES_KEY = "retries";

        private int retries;
        private Duration delay;
        private double delayFactor;
        private Duration callTimeout;
        private Duration overallTimeout;
        private ScheduledExecutorService executorService;

        private Builder(int retries) {
            this.retries = retries;
            this.delay = Duration.ofMillis(200);
            this.delayFactor = 2;
            this.callTimeout = Duration.ofMillis(500);
            this.overallTimeout = Duration.ofSeconds(2);
            this.executorService = Executors.newSingleThreadScheduledExecutor(new ConfigThreadFactory("retry-policy"));
        }

        /**
         * Initializes retry policy instance from configuration properties.
         * <p>
         * Mandatory {@code properties}, see {@link RetryPolicies#repeat(int)}:
         * <ul>
         * <li>{@code retries} - type {@code int}</li>
         * </ul>
         * Optional {@code properties}:
         * <ul>
         * <li>{@code delay} - type {@link Duration}, see {@link #delay(Duration)}</li>
         * <li>{@code delay-factor} - type {@code double}, see {@link #delayFactor(double)}</li>
         * <li>{@code call-timeout} - type {@link Duration}, see {@link #callTimeout(Duration)}</li>
         * <li>{@code overall-timeout} - type {@link Duration}, see {@link #overallTimeout(Duration)}</li>
         * </ul>
         *
         * @param metaConfig meta-configuration used to initialize returned polling strategy builder instance from.
         * @return new instance of polling strategy builder described by {@code metaConfig}
         * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
         *                                required by the mapper implementation to provide instance of Java type.
         * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
         *                                supplied configuration node to an instance of a given Java type.
         * @see PollingStrategies#regular(Duration)
         */
        public static Builder from(Config metaConfig) throws ConfigMappingException, MissingValueException {
            // retries
            Builder builder = new Builder(metaConfig.get(RETRIES_KEY).asInt());
            // delay
            metaConfig.get("delay").asOptional(Duration.class)
                    .ifPresent(builder::delay);
            // delay-factor
            metaConfig.get("delay-factor").asOptionalDouble()
                    .ifPresent(builder::delayFactor);
            // call-timeout
            metaConfig.get("call-timeout").asOptional(Duration.class)
                    .ifPresent(builder::callTimeout);
            // overall-timeout
            metaConfig.get("overall-timeout").asOptional(Duration.class)
                    .ifPresent(builder::overallTimeout);

            return builder;
        }

        /**
         * Sets an initial delay between invocations, that is repeatedly multiplied by {@code delayFactor}.
         * <p>
         * The default value is 200ms.
         *
         * @param delay an overall timeout
         * @return a modified builder instance
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Sets a factor that prolongs the delay for an every new execute.
         * <p>
         * The default value is 2.
         *
         * @param delayFactor a delay prolonging factor
         * @return a modified builder instance
         */
        public Builder delayFactor(double delayFactor) {
            this.delayFactor = delayFactor;
            return this;
        }

        /**
         * Sets a limit for each invocation.
         * <p>
         * The default value is 500ms.
         *
         * @param callTimeout an invocation timeout - a limit per call
         * @return a modified builder instance
         */
        public Builder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        /**
         * Sets a overall limit for all invocation, including delays.
         * <p>
         * The default value is 2s.
         *
         * @param overallTimeout an overall timeout
         * @return a modified builder instance
         */
        public Builder overallTimeout(Duration overallTimeout) {
            this.overallTimeout = overallTimeout;
            return this;
        }

        /**
         * Sets a custom {@link ScheduledExecutorService executor} used to invoke a method call.
         * <p>
         * By default single-threaded executor is used.
         *
         * @param executorService the custom scheduled executor service
         * @return a modified builder instance
         */
        public Builder executor(ScheduledExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Builds a new execute policy.
         *
         * @return the new instance
         */
        public RetryPolicy build() {
            return new RetryPolicyImpl(retries, delay, delayFactor, callTimeout, overallTimeout, executorService);
        }

        @Override
        public RetryPolicy get() {
            return build();
        }
    }
}
