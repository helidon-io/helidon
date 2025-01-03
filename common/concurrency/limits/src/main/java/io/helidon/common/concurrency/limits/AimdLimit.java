/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;

/**
 * AIMD based limiter.
 * <p>
 * The additive-increase/multiplicative-decrease (AIMD) algorithm is a feedback control algorithm best known for its use in TCP
 * congestion control. AIMD combines linear growth of the congestion window when there is no congestion with an exponential
 * reduction when congestion is detected.
 */
@SuppressWarnings("removal")
@RuntimeType.PrototypedBy(AimdLimitConfig.class)
public class AimdLimit implements Limit, SemaphoreLimit, RuntimeType.Api<AimdLimitConfig> {
    /**
     * Default length of the queue.
     */
    public static final int DEFAULT_QUEUE_LENGTH = 0;
    /**
     * Timeout of a request that is enqueued.
     */
    public static final String DEFAULT_QUEUE_TIMEOUT_DURATION = "PT1S";

    static final String TYPE = "aimd";

    private final AimdLimitConfig config;
    private final AimdLimitImpl aimdLimitImpl;

    private AimdLimit(AimdLimitConfig config) {
        this.config = config;
        this.aimdLimitImpl = new AimdLimitImpl(config);
    }

    /**
     * Create a new fluent API builder to construct {@link io.helidon.common.concurrency.limits.AimdLimit}
     * instance.
     *
     * @return fluent API builder
     */
    public static AimdLimitConfig.Builder builder() {
        return AimdLimitConfig.builder();
    }

    /**
     * Create a new instance with all defaults.
     *
     * @return a new limit instance
     */
    public static AimdLimit create() {
        return builder().build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the AIMD limit
     * @return a new limit instance configured from {@code config}
     */
    public static AimdLimit create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the AIMD limit
     * @return a new limit instance configured from {@code config}
     */
    public static AimdLimit create(AimdLimitConfig config) {
        return new AimdLimit(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer consumer of configuration builder
     * @return a new limit instance configured from the builder
     */
    public static AimdLimit create(Consumer<AimdLimitConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    @Override
    public <T> T invoke(Callable<T> callable) throws Exception {
        return aimdLimitImpl.invoke(callable);
    }

    @Override
    public void invoke(Runnable runnable) throws Exception {
        aimdLimitImpl.invoke(runnable);
    }

    @Override
    public Optional<Token> tryAcquire(boolean wait) {
        return aimdLimitImpl.tryAcquire(wait);
    }

    @SuppressWarnings("removal")
    @Override
    public Semaphore semaphore() {
        return aimdLimitImpl.semaphore();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public AimdLimitConfig prototype() {
        return config;
    }

    @Override
    public Limit copy() {
        return config.build();
    }

    @Override
    public void init(String socketName) {
        aimdLimitImpl.initMetrics(socketName, config);
    }
}
