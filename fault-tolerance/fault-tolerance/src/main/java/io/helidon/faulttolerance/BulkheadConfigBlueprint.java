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

package io.helidon.faulttolerance;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.inject.configdriven.api.ConfigBean;

/**
 * {@link Bulkhead} configuration bean.
 */
@ConfigBean(repeatable = true)
@Prototype.Configured("fault-tolerance.bulkheads")
@Prototype.Blueprint(decorator = BulkheadConfigBlueprint.BuilderDecorator.class)
interface BulkheadConfigBlueprint extends Prototype.Factory<Bulkhead> {
    /**
     * Default limit.
     *
     * @see #limit()
     */
    int DEFAULT_LIMIT = 10;

    /**
     * Default queue lengths.
     * @see #queueLength()
     */
    int DEFAULT_QUEUE_LENGTH = 10;

    /**
     * Maximal number of parallel requests going through this bulkhead.
     * When the limit is reached, additional requests are enqueued.
     *
     * @return maximal number of parallel calls, defaults is {@value DEFAULT_LIMIT}
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_LIMIT)
    int limit();

    /**
     * Maximal number of enqueued requests waiting for processing.
     * When the limit is reached, additional attempts to invoke
     * a request will receive a {@link BulkheadException}.
     *
     * @return length of the queue
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_QUEUE_LENGTH)
    int queueLength();

    /**
     * Queue listeners of this bulkhead.
     *
     * @return queue listeners
     */
    @Option.Singular
    List<Bulkhead.QueueListener> queueListeners();

    /**
     * Name for debugging, error reporting, monitoring.
     *
     * @return name of this bulkhead
     */
    Optional<String> name();

    class BuilderDecorator implements Prototype.BuilderDecorator<BulkheadConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(BulkheadConfig.BuilderBase<?, ?> target) {
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
        }
    }
}
