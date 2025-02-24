/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pretty printer.
 */
@SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
final class PrettyPrinter {

    private final StringBuilder sb = new StringBuilder();
    private int i = 0;

    /**
     * Empty consumer.
     */
    static final Consumer<PrettyPrinter> EMPTY = printer -> {
    };

    /**
     * Print a name.
     *
     * @param name name
     * @return this instance
     */
    PrettyPrinter name(String name) {
        if (name != null && !name.isEmpty()) {
            value(name).append(" = ");
        }
        return this;
    }

    /**
     * Append a value.
     *
     * @param value value
     * @return this instance
     */
    PrettyPrinter value(Object value) {
        return append('\n').indent().append(value);
    }

    /**
     * Append a named value.
     *
     * @param key   key
     * @param value value
     * @return this instance
     */
    PrettyPrinter value(String key, Object value) {
        return name(key).append(value);
    }

    /**
     * Append a named block.
     *
     * @param key   key
     * @param block block
     * @return this instance
     */
    PrettyPrinter block(String key, String block) {
        name(key).append("<<");
        block.lines().forEach(this::value);
        value(">>");
        return this;
    }

    /**
     * Append an object structure.
     *
     * @param action action
     * @return this instance
     */
    PrettyPrinter object(Consumer<PrettyPrinter> action) {
        append('{')
                .indent(() -> action.accept(this))
                .append('\n').indent().append('}');
        return this;
    }

    /**
     * Append a named object structure.
     * No-op if {@code action} is {@link #EMPTY}.
     *
     * @param action action
     * @return this instance
     */
    PrettyPrinter object(String name, Consumer<PrettyPrinter> action) {
        if (action != EMPTY) {
            name(name).object(action);
        }
        return this;
    }

    /**
     * Repeat a named object structure for the given list.
     *
     * @param name     name, may be {@code null}
     * @param list     list
     * @param function function
     * @param <T>      list element type
     * @return this instance
     */
    <T> PrettyPrinter objects(String name, List<T> list, Function<T, Consumer<PrettyPrinter>> function) {
        if (list != null) {
            list.forEach(e -> object(name, function.apply(e)));
        }
        return this;
    }

    /**
     * Append a named list structure.
     *
     * @param name   name, may be {@code null}
     * @param list   list
     * @param mapper mapper
     * @param <T>    list element type
     * @return this instance
     */
    <T> PrettyPrinter values(String name, List<T> list, Function<T, ?> mapper) {
        if (list != null && !list.isEmpty()) {
            name(name).append('[')
                    .indent(() -> list.forEach(e -> value(mapper.apply(e))))
                    .append('\n').indent().append(']');
        }
        return this;
    }

    /**
     * Append a named list structure.
     *
     * @param name   name, may be {@code null}
     * @param values values
     * @return this instance
     */
    @SafeVarargs
    final <T> PrettyPrinter values(String name, T... values) {
        return values(name, Arrays.asList(values), e -> e);
    }

    /**
     * Delegate to the given action.
     *
     * @param action action
     * @return this instance
     */
    PrettyPrinter apply(Consumer<PrettyPrinter> action) {
        action.accept(this);
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    private PrettyPrinter append(Object object) {
        sb.append(object);
        return this;
    }

    private PrettyPrinter indent() {
        sb.append("  ".repeat(i));
        return this;
    }

    private PrettyPrinter indent(Runnable action) {
        try {
            i++;
            action.run();
            return this;
        } finally {
            i--;
        }
    }
}
