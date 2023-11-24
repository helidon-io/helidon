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

package io.helidon.scheduling;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

/**
 * Scheduling periodically executed task with specified cron expression.
 *
 * <pre>{@code
 * Scheduling.cron()
 *      .expression("0 45 9 ? * *")
 *      .task(inv -> System.out.println("Executed every day at 9:45"))
 *      .build()
 * }</pre>
 */
@RuntimeType.PrototypedBy(CronConfig.class)
public interface Cron extends RuntimeType.Api<CronConfig>, Task {

    /**
     * Create a new fluent API builder to build a cron task.
     *
     * @return a builder instance
     */
    static CronConfig.Builder builder() {
        return CronConfig.builder();
    }

    /**
     * Create a cron task from configuration.
     *
     * @param configConsumer config consumer
     * @return a new cron task configured from config
     */
    static Cron create(Consumer<CronConfig.Builder> configConsumer) {
        return builder().update(configConsumer).build();
    }

    /**
     * Create a cron task from programmatic configuration.
     *
     * @param config configuration
     * @return a new cron task
     */
    static Cron create(CronConfig config) {
        return new CronTask(config);
    }
}
