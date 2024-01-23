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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.helidon.common.GenericType;

/**
 * Configuration options.
 */
public interface CodegenOptions {
    /**
     * Tag to define custom module name.
     */
    String TAG_CODEGEN_MODULE = "helidon.codegen.module-name";
    /**
     * Tag to define custom package name.
     */
    String TAG_CODEGEN_PACKAGE = "helidon.codegen.package-name";

    /**
     * Codegen option to configure codegen scope.
     */
    Option<CodegenScope> CODEGEN_SCOPE = Option.create("helidon.codegen.scope",
                                                       "Override scope that is \"guessed\" from the "
                                                               + "environment. By default we support "
                                                               + "production and test scopes",
                                                       CodegenScope.PRODUCTION,
                                                       CodegenScope::new,
                                                       GenericType.create(CodegenScope.class));
    /**
     * Codegen option to configure module name of the module being processed.
     */
    Option<String> CODEGEN_MODULE = Option.create(TAG_CODEGEN_MODULE,
                                                  "Override name of the module that is being processed, or provide it"
                                                          + " if this module does not have a module-info.java",
                                                  "");

    /**
     * Codegen option to configure module name of the module being processed.
     */
    Option<String> CODEGEN_PACKAGE = Option.create(TAG_CODEGEN_PACKAGE,
                                                   "Define package to use for generated types.",
                                                   "");
    /**
     * Codegen option to configure which indent type to use (a space character, or a tab character).
     */
    Option<IndentType> INDENT_TYPE = Option.create("helidon.codegen.indent.type",
                                                   "Type of indentation, either of " + Arrays.toString(IndentType.values()),
                                                   IndentType.SPACE,
                                                   IndentType::valueOf,
                                                   GenericType.create(IndentType.class));
    /**
     * Codegen option to configure how many times to repeat the {@link #INDENT_TYPE} for indentation.
     * <p>
     * Defaults to {@code 4}.
     */
    Option<Integer> INDENT_COUNT = Option.create("helidon.codegen.indent.count",
                                                 "Number of indents to use (such as 4, if combined with SPACE will indent by 4 "
                                                         + "spaces",
                                                 4);

    /**
     * Codegen option to configure creation of META-INF services when module-info.java is present.
     * Defaults to {@code true}, so the file is generated.
     */
    Option<Boolean> CREATE_META_INF_SERVICES = Option.create("helidon.codegen.meta-inf.services",
                                                             "Whether to create META-INF/services for generated services even "
                                                                     + "if module-info.java is present",
                                                             true);

    /**
     * Find an option.
     *
     * @param option option name
     * @return option value if configured
     */
    Optional<String> option(String option);

    /**
     * Enumeration option.
     *
     * @param option       option name
     * @param defaultValue default value
     * @param enumType     type of the enum
     * @param <T>          type of the enum
     * @return option value, or default value if not defined
     * @throws IllegalArgumentException in case the enum value is not valid for the provided enum type
     */
    default <T extends Enum<T>> T option(String option, T defaultValue, Class<T> enumType) {
        return option(option)
                .map(it -> Enum.valueOf(enumType, it))
                .orElse(defaultValue);
    }

    /**
     * Boolean option that defaults to false.
     *
     * @param option option to check
     * @return whether the option is enabled (e.g. its value is explicitly configured to {@code true})
     */
    default boolean enabled(Option<Boolean> option) {
        return option.value(this);
    }

    /**
     * List of string options.
     *
     * @param option option name
     * @return list of values, or an empty list if not defined
     */
    default List<String> asList(String option) {
        return option(option)
                .stream()// stream of string (option value)
                .map(it -> it.split(",")) // split to array
                .flatMap(Stream::of) // stream from array
                .map(String::trim) // trim each element
                .toList();
    }

    /**
     * Set of string options.
     *
     * @param option option name
     * @return set of values, or an empty list if not defined
     */
    default Set<String> asSet(String option) {
        return Set.copyOf(asList(option));
    }

    /**
     * Validate options against the permitted options. The implementations in Helidon only validate
     * {@code helidon.} prefixed options.
     *
     * @param permittedOptions options permitted by the codegen in progress
     */
    default void validate(Set<Option<?>> permittedOptions) {
    }
}
