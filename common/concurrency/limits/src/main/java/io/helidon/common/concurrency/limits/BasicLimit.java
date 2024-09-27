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

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;

/**
 * Basic limit, that provides {@link java.util.concurrent.Semaphore} like feature of limiting
 * concurrent executions to a fixed number.
 */
@SuppressWarnings("removal")
@RuntimeType.PrototypedBy(BasicLimitConfig.class)
public class BasicLimit implements Limit, SemaphoreLimit, RuntimeType.Api<BasicLimitConfig> {
    /**
     * Default limit, meaning unlimited execution.
     */
    public static final int DEFAULT_LIMIT = 0;
    static final String TYPE = "basic";

    private final BasicLimitConfig config;
    private final LimiterHandler handler;

    private BasicLimit(BasicLimitConfig config) {
        this.config = config;
        if (config.semaphore().isPresent()) {
            this.handler = new RealSemaphoreHandler(config.semaphore().get());
        } else if (config.permits() == 0) {
            this.handler = new NoOpSemaphoreHandler();
        } else {
            this.handler = new RealSemaphoreHandler(new Semaphore(config.permits(), config.fair()));
        }
    }

    /**
     * Create a new fluent API builder to construct {@link io.helidon.common.concurrency.limits.BasicLimit}
     * instance.
     *
     * @return fluent API builder
     */
    public static BasicLimitConfig.Builder builder() {
        return BasicLimitConfig.builder();
    }

    /**
     * Create a new instance with all defaults (no limit).
     *
     * @return a new limit instance
     */
    public static BasicLimit create() {
        return builder().build();
    }

    /**
     * Create an instance from the provided semaphore.
     *
     * @param semaphore semaphore to use
     * @return a new basic limit backed by the provided semaphore
     */
    public static BasicLimit create(Semaphore semaphore) {
        return builder()
                .semaphore(semaphore)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the basic limit
     * @return a new limit instance configured from {@code config}
     */
    public static BasicLimit create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the basic limit
     * @return a new limit instance configured from {@code config}
     */
    public static BasicLimit create(BasicLimitConfig config) {
        return new BasicLimit(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer consumer of configuration builder
     * @return a new limit instance configured from the builder
     */
    public static BasicLimit create(Consumer<BasicLimitConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    @Override
    public <T> T invoke(Callable<T> callable) throws Exception {
        return handler.invoke(callable);
    }

    @Override
    public void invoke(Runnable runnable) throws Exception {
        handler.invoke(runnable);
    }

    @SuppressWarnings("removal")
    @Override
    public Semaphore semaphore() {
        return handler.semaphore();
    }

    @Override
    public BasicLimitConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return BasicLimit.TYPE;
    }

    @SuppressWarnings("removal")
    private interface LimiterHandler extends SemaphoreLimit {
        <T> T invoke(Callable<T> callable) throws Exception;
        void invoke(Runnable runnable) throws Exception;
    }

    private static class NoOpSemaphoreHandler implements LimiterHandler {
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

        @SuppressWarnings("removal")
        @Override
        public Semaphore semaphore() {
            return NoopSemaphore.INSTANCE;
        }
    }

    @SuppressWarnings("removal")
    private static class RealSemaphoreHandler implements LimiterHandler {
        private final Semaphore semaphore;

        private RealSemaphoreHandler(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public <T> T invoke(Callable<T> callable) throws Exception {
            if (semaphore.tryAcquire()) {
                try {
                    return callable.call();
                } catch (IgnoreTaskException e) {
                    return e.handle();
                } finally {
                    semaphore.release();
                }
            } else {
                throw new LimitException("No more permits available for the semaphore");
            }
        }

        @Override
        public void invoke(Runnable runnable) throws Exception {
            if (semaphore.tryAcquire()) {
                try {
                    runnable.run();
                } catch (IgnoreTaskException e) {
                    e.handle();
                } finally {
                    semaphore.release();
                }
            } else {
                throw new LimitException("No more permits available for the semaphore");
            }
        }

        @Override
        public Semaphore semaphore() {
            return semaphore;
        }
    }

}
