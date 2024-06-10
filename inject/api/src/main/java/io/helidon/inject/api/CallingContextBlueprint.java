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

package io.helidon.inject.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Prototype;

/**
 * For internal use only to Helidon. Applicable when {@link InjectionServices#TAG_DEBUG} is enabled.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint(decorator = CallingContextBlueprint.BuilderDecorator.class)
interface CallingContextBlueprint {
    /**
     * Only populated when {@link InjectionServices#TAG_DEBUG} is set.
     *
     * @return the stack trace for who initialized
     */
    Optional<StackTraceElement[]> stackTrace();

    /**
     * Only populated when {@code module} is set.
     *
     * @return the module name
     */
    Optional<String> moduleName();

    /**
     * The thread that created the calling context.
     *
     * @return thread creating the calling context
     */
    String threadName();

    /**
     * Returns a stack trace as a list of strings.
     *
     * @return the list of strings for the stack trace, or empty list if not available
     */
    default List<String> stackTraceAsList() {
        return stackTrace().map(stackTrace -> {
                    List<String> result = new ArrayList<>();
                    for (StackTraceElement e : stackTrace) {
                        result.add(e.toString());
                    }
                    return result;
                })
                .orElseGet(List::of);
    }

    class BuilderDecorator implements Prototype.BuilderDecorator<CallingContext.BuilderBase<?, ?>> {
        @Override
        public void decorate(CallingContext.BuilderBase<?, ?> target) {
            if (target.threadName().isEmpty()) {
                target.threadName(Thread.currentThread().getName());
            }
        }
    }

}
