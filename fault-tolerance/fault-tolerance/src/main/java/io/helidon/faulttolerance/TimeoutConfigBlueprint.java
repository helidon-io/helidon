/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * {@link Timeout} configuration bean.
 */
//@ConfigDriven.ConfigBean("fault-tolerance.timeouts")
//@ConfigDriven.Repeatable
@Prototype.Blueprint(decorator = TimeoutConfigBlueprint.BuilderDecorator.class)
@Prototype.Configured("fault-tolerance.timeouts")
interface TimeoutConfigBlueprint extends Prototype.Factory<Timeout> {
    /**
     * Name for debugging, error reporting, monitoring.
     *
     * @return name of this timeout
     */
    Optional<String> name();

    /**
     * Duration to wait before timing out.
     * Defaults to {@code 10 seconds}.
     *
     * @return timeout
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration timeout();

    /**
     * Flag to indicate that code must be executed in current thread instead
     * of in an executor's thread. This flag is {@code false} by default.
     *
     * @return whether to execute on current thread ({@code true}), or in an executor service ({@code false}})
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean currentThread();

    /**
     * Executor service to schedule the timeout.
     *
     * @return executor service to use
     */
    Optional<ExecutorService> executor();

    /**
     * Flag to enable metrics for this instance. The value of this flag is
     * combined with the global config entry
     * {@link io.helidon.faulttolerance.FaultTolerance#FT_METRICS_DEFAULT_ENABLED}.
     * If either of these flags is {@code true}, then metrics will be enabled
     * for the instance.
     *
     * @return metrics enabled flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enableMetrics();

    class BuilderDecorator implements Prototype.BuilderDecorator<TimeoutConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(TimeoutConfig.BuilderBase<?, ?> target) {
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
        }
    }
}
