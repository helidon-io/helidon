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

package io.helidon.codegen;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.common.GenericType;

/**
 * Option definition.
 * When implementing your own, hashCode and equals should return value of {@link #name()}, to correctly match against other
 * option instances.
 *
 * @param <T> option type, as options are always loaded from String, the type has to map from a String or list of strings
 */
public interface Option<T> {
    /**
     * Create a new String option.
     *
     * @param name         name of the option
     * @param description  human readable description
     * @param defaultValue default value to use if not found
     * @return a new option
     */
    static Option<String> create(String name, String description, String defaultValue) {
        return new OptionImpl<>(name, description, defaultValue, Function.identity(), GenericType.STRING);
    }

    /**
     * Create a new boolean option.
     *
     * @param name         name of the option
     * @param description  human readable description
     * @param defaultValue default value to use if not found
     * @return a new option
     */
    static Option<Boolean> create(String name, String description, boolean defaultValue) {
        return new OptionImpl<>(name, description, defaultValue, Boolean::parseBoolean, GenericType.create(Boolean.class));
    }

    /**
     * Create a new int option.
     *
     * @param name         name of the option
     * @param description  human readable description
     * @param defaultValue default value to use if not found
     * @return a new option
     */
    static Option<Integer> create(String name, String description, int defaultValue) {
        return new OptionImpl<>(name, description, defaultValue, Integer::parseInt, GenericType.create(Integer.class));
    }

    /**
     * Create a new option with a custom mapper.
     *
     * @param name         name of the option
     * @param description  description of the option
     * @param defaultValue default value
     * @param mapper       mapper from string
     * @param type         type of the option
     * @param <T>          type of the option
     * @return a new option that can be used to load value from {@link io.helidon.codegen.CodegenOptions}
     */
    static <T> Option<T> create(String name,
                                String description,
                                T defaultValue,
                                Function<String, T> mapper,
                                GenericType<T> type) {
        return new OptionImpl<>(name, description, defaultValue, mapper, type);
    }

    /**
     * Create a new option that has a set of values, with a custom mapper.
     *
     * @param name         name of the option
     * @param description  description of the option
     * @param defaultValue default value
     * @param mapper       mapper from string
     * @param type         type of the option
     * @param <T>          type of the option
     * @return a new option that can be used to load value from {@link io.helidon.codegen.CodegenOptions}
     */
    static <T> Option<Set<T>> createSet(String name,
                                        String description,
                                        Set<T> defaultValue,
                                        Function<String, T> mapper,
                                        GenericType<Set<T>> type) {
        return new SetOptionImpl<>(name, description, defaultValue, mapper, type);
    }

    /**
     * Create a new option that has a list of values, with a custom mapper.
     *
     * @param name         name of the option
     * @param description  description of the option
     * @param defaultValue default value
     * @param mapper       mapper from string
     * @param type         type of the option
     * @param <T>          type of the option
     * @return a new option that can be used to load value from {@link io.helidon.codegen.CodegenOptions}
     */
    static <T> Option<List<T>> createList(String name,
                                          String description,
                                          List<T> defaultValue,
                                          Function<String, T> mapper,
                                          GenericType<List<T>> type) {
        return new ListOptionImpl<>(name, description, defaultValue, mapper, type);
    }

    /**
     * Type of the option, metadata that can be used to list available options and their types.
     *
     * @return type of this option
     */
    GenericType<T> type();

    /**
     * Name of the option. The name can be configured in Maven plugin, through command line arguments,
     * or through {@code -A} prefixed annotation processing arguments to compiler.
     *
     * @return name of the option
     */
    String name();

    /**
     * Option description, metadata that can be used to list available options and their description.
     *
     * @return option description
     */
    String description();

    /**
     * Default to use if the option is not defined.
     *
     * @return default value
     */
    T defaultValue();

    /**
     * Find an option value from the codegen options.
     *
     * @param options as obtained from the caller
     * @return value of this option, or empty if not configured
     */
    Optional<T> findValue(CodegenOptions options);

    /**
     * Obtain an option value from the codegen options using {@link #defaultValue()} if none configured.
     *
     * @param options as obtained from the caller
     * @return value of this option
     */
    default T value(CodegenOptions options) {
        return findValue(options).orElseGet(this::defaultValue);
    }
}
