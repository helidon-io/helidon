/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.builder.api.Prototype;

final class FormatterContextSupport {

    private FormatterContextSupport() {
    }

    /**
     * Sets the meter names to format, replacing any previous selection.
     * The names are copied before this method returns.
     *
     * @param builder builder
     * @param names meter names; empty means no name-based restriction
     */
    @Prototype.BuilderMethod
    static void nameSelection(FormatterContext.BuilderBase<?, ?> builder, Iterable<String> names) {
        Objects.requireNonNull(names, "nameSelection");
        List<String> copy = new ArrayList<>();
        names.forEach(name -> copy.add(Objects.requireNonNull(name, "selected meter name")));
        builder.nameSelection(copy);
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<FormatterContext.BuilderBase<?, ?>> {

        @Override
        public void decorate(FormatterContext.BuilderBase<?, ?> builder) {
            Map<String, Collection<String>> selections = new LinkedHashMap<>();
            builder.tagSelections().forEach((name, values) -> selections.put(Objects.requireNonNull(name, "selected tag name"),
                                                                           List.copyOf(values)));
            builder.tagSelections(selections);
        }
    }
}
