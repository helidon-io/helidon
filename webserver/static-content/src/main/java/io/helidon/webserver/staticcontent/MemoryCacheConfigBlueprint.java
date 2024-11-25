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

package io.helidon.webserver.staticcontent;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Size;

/**
 * Configuration of memory cache for static content.
 * The memory cache will cache the first {@link #capacity() bytes} that fit into the configured memory size for the
 * duration of the service uptime.
 */
@Prototype.Blueprint
@Prototype.Configured
interface MemoryCacheConfigBlueprint extends Prototype.Factory<MemoryCache> {
    /**
     * Whether the cache is enabled, defaults to {@code true}.
     *
     * @return whether the cache is enabled
     */
    @Option.DefaultBoolean(true)
    @Option.Configured
    boolean enabled();

    /**
     * Capacity of the cached bytes of file content.
     * If set to {@code 0}, the cache is unlimited. To disable caching, set {@link #enabled()} to {@code false},
     * or do not configure a memory cache at all.
     * <p>
     * The capacity must be less than {@link java.lang.Long#MAX_VALUE} bytes, though you must be careful still,
     * as it must fit into the heap size.
     *
     * @return capacity of the cache in bytes, defaults to 50 million bytes (50 mB)
     */
    @Option.Default("50 mB")
    @Option.Configured
    Size capacity();
}
