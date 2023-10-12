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

package io.helidon.common.configurable;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
@Prototype.Configured
interface ScheduledThreadPoolConfigBlueprint extends Prototype.Factory<ScheduledThreadPoolSupplier> {
    /**
     * Default core pool size ({@value}).
     *
     * @see #corePoolSize()
     */
    int DEFAULT_CORE_POOL_SIZE = 16;
    /**
     * Default daemon status of the created threads ({@value}).
     *
     * @see #daemon()
     */
    boolean DEFAULT_IS_DAEMON = true;
    /**
     * Default thread name prefix ({@value}).
     *
     * @see #threadNamePrefix()
     */
    String DEFAULT_THREAD_NAME_PREFIX = "helidon-";
    /**
     * Default prestart status of threads ({@value}).
     *
     * @see #prestart()
     */
    boolean DEFAULT_PRESTART = false;

    /**
     * When configured to {@code true}, an unbounded virtual executor service (project Loom) will be used.
     * <p>
     * If enabled, all other configuration options of this executor service are ignored!
     *
     * @return whether to use virtual threads or not, defaults to {@code false}
     */
    @Option.Configured
    boolean virtualThreads();

    /**
     * Core pool size of the thread pool executor.
     * Defaults to {@value #DEFAULT_CORE_POOL_SIZE}.
     *
     * @return corePoolSize see {@link java.util.concurrent.ThreadPoolExecutor#getCorePoolSize()}
     */
    @Option.DefaultInt(DEFAULT_CORE_POOL_SIZE)
    @Option.Configured
    int corePoolSize();

    /**
     * Is daemon of the thread pool executor.
     * Defaults to {@value #DEFAULT_IS_DAEMON}.
     *
     * @return whether the threads are daemon threads
     */
    @Option.DefaultBoolean(DEFAULT_IS_DAEMON)
    @Option.Configured("is-daemon")
    boolean daemon();

    /**
     * Name prefix for threads in this thread pool executor.
     * Defaults to {@value #DEFAULT_THREAD_NAME_PREFIX}.
     *
     * @return prefix of a thread name
     */
    @Option.Default(DEFAULT_THREAD_NAME_PREFIX)
    @Option.Configured
    String threadNamePrefix();

    /**
     * Whether to prestart core threads in this thread pool executor.
     * Defaults to {@value #DEFAULT_PRESTART}.
     *
     * @return whether to prestart the threads
     */
    @Option.DefaultBoolean(DEFAULT_PRESTART)
    @Option.Configured
    boolean prestart();
}
