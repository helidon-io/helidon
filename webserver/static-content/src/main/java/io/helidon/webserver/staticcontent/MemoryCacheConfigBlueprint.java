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

/**
 * Configuration of memory cache for static content.
 * The memory cache will cache the first {@link #maxBytes() bytes} that fit into the configured memory size for the
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
     * Maximal capacity of the cached bytes of file content.
     * If set to {@code 0}, the cache is unlimited. To disable caching, set {@link #enabled()} to {@code false},
     * or do not configure a memory cache at all.
     *
     * @return maximal number of bytes in cache
     */
    @Option.DefaultLong(50_000_000)
    @Option.Configured
    long maxBytes();
}
