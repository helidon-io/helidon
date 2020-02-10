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

import java.time.Instant;
import java.util.function.Consumer;

import io.helidon.config.ChangeEventType;

public interface ChangeWatcher<T> {
    /**
     * Start watching the target for changes.
     * If a change happens, notify the listener.
     *
     * @param target target of this watcher, such as {@link java.nio.file.Path}
     * @param listener listener that handles reloading of the resource being watched
     */
    void start(T target, Consumer<Change<T>> listener);

    default void stop() {
    }

    Class<T> type();

    interface Change<T> {
        Instant changeInstant();

        T target();

        ChangeEventType type();

        static <T> Change<T> create(T target, ChangeEventType type, Instant instant) {
            return new Change<>() {
                @Override
                public Instant changeInstant() {
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

        static <T> Change<T> create(T target, ChangeEventType type) {
            return create(target, type, Instant.now());
        }
    }
}
