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

import java.util.Optional;
import java.util.concurrent.Semaphore;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.concurrency.limits.spi.LimitProvider;

/**
 * Configuration of {@link BasicLimit}.
 */
@Prototype.Blueprint
@Prototype.Configured(value = BasicLimit.TYPE, root = false)
@Prototype.Provides(LimitProvider.class)
interface BasicLimitConfigBlueprint extends Prototype.Factory<BasicLimit> {
    /**
     * Number of permit to allow.
     * Defaults to {@value BasicLimit#DEFAULT_LIMIT}.
     * When set to {@code 0}, we switch to unlimited.
     *
     * @return number of permits
     */
    @Option.Configured
    @Option.DefaultInt(BasicLimit.DEFAULT_LIMIT)
    int permits();

    /**
     * Whether the {@link java.util.concurrent.Semaphore} should be {@link java.util.concurrent.Semaphore#isFair()}.
     * Defaults to {@code false}.
     *
     * @return whether this should be a fair semaphore
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean fair();

    /**
     * Name of this instance.
     *
     * @return name of the instance
     */
    @Option.Default(BasicLimit.TYPE)
    String name();

    /**
     * Explicitly configured semaphore.
     * Note that if this is set, all other configuration is ignored.
     *
     * @return semaphore instance
     */
    Optional<Semaphore> semaphore();

}
