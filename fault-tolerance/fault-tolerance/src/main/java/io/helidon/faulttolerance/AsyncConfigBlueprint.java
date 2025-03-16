/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * {@link Async} configuration bean.
 */
//@ConfigDriven.ConfigBean("fault-tolerance.asyncs")
//@ConfigDriven.Repeatable
@Prototype.Blueprint(decorator = AsyncConfigBlueprint.BuilderDecorator.class)
@Prototype.Configured
interface AsyncConfigBlueprint extends Prototype.Factory<Async> {
    /**
     * Name for debugging, error reporting, monitoring.
     *
     * @return name of this async
     */
    Optional<String> name();

    /**
     * Name of an executor service. This is only honored when service registry is used.
     *
     * @return name fo the {@link java.util.concurrent.ExecutorService} to lookup
     * @see #executor()
     */
    @Option.Configured
    Optional<String> executorName();

    /**
     * Executor service. Will be used to run the asynchronous tasks.
     *
     * @return explicit executor service
     */
    Optional<ExecutorService> executor();

    /**
     * A future that is completed when execution of the asynchronous task starts.
     *
     * @return future that will be completed by the asynchronous processing
     */
    Optional<CompletableFuture<Async>> onStart();

    class BuilderDecorator implements Prototype.BuilderDecorator<AsyncConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(AsyncConfig.BuilderBase<?, ?> target) {
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
        }
    }
}
