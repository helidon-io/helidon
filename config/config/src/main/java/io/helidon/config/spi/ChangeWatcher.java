/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Similar to a {@link io.helidon.config.spi.PollingStrategy} a change watcher is used to
 * identify a change and trigger reload of a {@link io.helidon.config.spi.ConfigSource}.
 * Where a polling strategy provides polling events to check for changes, change watcher
 * is capable of identifying a change in the underlying target using other means.
 *
 * @param <T> target of this change watcher, such as {@link java.nio.file.Path}
 */
public interface ChangeWatcher<T> {
    /**
     * Start watching a target for changes.
     * If a change happens, notify the listener.
     *
     * @param target target of this watcher, such as {@link java.nio.file.Path}
     * @param listener listener that handles reloading of the resource being watched
     */
    void start(T target, Consumer<ChangeEvent<T>> listener);

    /**
     * Stop watching all targets for changes.
     */
    default void stop() {
    }

    /**
     * Target supported by this change watcher.
     *
     * @return type supported
     */
    Class<T> type();

    /**
     * A change event, carrying the target, type of change and time of change.
     *
     * @param <T> type of target
     */
    interface ChangeEvent<T> {
        /**
         * Time of change, or as close to that time as we can get.
         * @return instant of the change
         */
        Instant changeTime();

        /**
         * Target of the change.
         * This may be the same as the target of {@link io.helidon.config.spi.ChangeWatcher},
         * though this may also be a different target.
         * In case of {@link java.nio.file.Path}, the change watcher may watch a directory,
         * yet the change event notifies about a single file within that directory.
         *
         * @return target that is changed
         */
        T target();

        /**
         * Type of change if available. If no details can be found (e.g. we do not know if
         * the target was deleted, created or modified, use
         * {@link io.helidon.config.spi.ChangeEventType#CHANGED}.
         *
         * @return type of change
         */
        ChangeEventType type();

        static <T> ChangeEvent<T> create(T target, ChangeEventType type, Instant instant) {
            return new ChangeEvent<>() {
                @Override
                public Instant changeTime() {
                    return instant;
                }

                @Override
                public T target() {
                    return target;
                }

                @Override
                public ChangeEventType type() {
                    return type;
                }

                @Override
                public String toString() {
                    return type + " " + target;
                }
            };
        }

        static <T> ChangeEvent<T> create(T target, ChangeEventType type) {
            return create(target, type, Instant.now());
        }
    }
}
