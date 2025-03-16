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
 * Scheduling periodically executed task with specified fixed rate.
 *
 * <pre>{@code
 * Scheduling.fixedRate()
 *      .interval(Duration.ofSecond(2))
 *      .task(inv -> System.out.println("Executed every 2 seconds"))
 *      .build();
 * }</pre>
 */
@RuntimeType.PrototypedBy(FixedRateConfig.class)
public interface FixedRate extends RuntimeType.Api<FixedRateConfig>, Task {

    /**
     * Create a new fluent API builder to build a fixed rate task.
     *
     * @return a builder instance
     */
    static FixedRateConfig.Builder builder() {
        return FixedRateConfig.builder();
    }

    /**
     * Create a fixed rate task from configuration.
     *
     * @param configConsumer config consumer
     * @return a new fixed rate task configured from config
     */
    static FixedRate create(Consumer<FixedRateConfig.Builder> configConsumer) {
        return builder().update(configConsumer).build();
    }

    /**
     * Create a fixed rate task from programmatic configuration.
     *
     * @param config configuration
     * @return a new fixed rate task
     */
    static FixedRate create(FixedRateConfig config) {
        return new FixedRateTask(config);
    }

    /**
     * Whether the interval of the next invocation should be calculated from the start or end of the previous task.
     */
    enum DelayType {
        /**
         * Next invocation start is measured from the previous invocation task start.
         */
        SINCE_PREVIOUS_START,
        /**
         * Next invocation start is measured from the previous invocation task end.
         */
        SINCE_PREVIOUS_END
    }
}
