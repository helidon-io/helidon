/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.spi;

import java.util.Optional;

/**
 * A source implementing this interface provides a target that a {@link io.helidon.config.spi.ChangeWatcher} can
 * use.
 *
 * @param <T> Type of target of this config source
 * @see io.helidon.config.spi.ChangeWatcher
 */
public interface WatchableSource<T> {
    /**
     * Target type as supported by this source.
     *
     * @return class of the target, by default used for {@link #target()}
     */
    @SuppressWarnings("unchecked")
    default Class<T> targetType() {
        return (Class<T>) target().getClass();
    }

    /**
     * The target of this source.
     *
     * @return target this source is configured with, never {@code null}
     */
    T target();

    /**
     * If a change watcher is configured with this source, return it.
     * The source implementation does not need to handle change watcher .
     *
     * @return change watcher if one is configured on this source
     */
    Optional<ChangeWatcher<Object>> changeWatcher();

    /**
     * A builder for a watchable source.
     *
     * @param <B> type of the builder, used when extending this builder ({@code MyBuilder implements Builder<MyBuilder, Path>}
     * @param <T> type of the target of the source
     * @see io.helidon.config.AbstractConfigSourceBuilder
     * @see io.helidon.config.AbstractConfigSource
     */
    interface Builder<B extends Builder<B, T>, T> {
        /**
         * Configure the change watcher to be used with this source.
         *
         * @param changeWatcher watcher to use
         * @return updated builder instance
         */
        B changeWatcher(ChangeWatcher<T> changeWatcher);
    }
}
